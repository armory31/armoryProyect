/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ruben.spotitube.lyrics

import com.ruben.spotitube.constants.PreloadQueueLyricsEnabledKey
import com.ruben.spotitube.constants.QueueLyricsPreloadCountKey
import com.ruben.spotitube.db.MusicDatabase
import com.ruben.spotitube.db.entities.LyricsEntity
import com.ruben.spotitube.models.MediaMetadata
import com.ruben.spotitube.utils.NetworkConnectivityObserver
import com.ruben.spotitube.utils.dataStore
import com.ruben.spotitube.utils.reportException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Manages preloading of lyrics for upcoming songs in the queue.
 * This improves user experience by having lyrics ready when songs change.
 */
class LyricsPreloadManager @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val database: MusicDatabase,
    private val networkConnectivity: NetworkConnectivityObserver,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var preloadJob: Job? = null
    
    // Track current queue to detect changes
    private var currentQueueIds: List<String> = emptyList()
    private var currentIndex: Int = -1

    /**
     * Called when the current song changes in the player.
     * Triggers preloading of lyrics for the next N songs in the queue.
     *
     * @param currentIndex The index of the currently playing song in the queue
     * @param queue List of MediaMetadata for songs in the queue
     */
    fun onSongChanged(currentIndex: Int, queue: List<MediaMetadata>) {
        // Cancel any existing preload job
        preloadJob?.cancel()
        
        // Check if preload is enabled
        scope.launch {
            try {
                val preferences = context.dataStore.data.first()
                val isEnabled = preferences[PreloadQueueLyricsEnabledKey] ?: true
                
                if (!isEnabled) {
                    Timber.tag(TAG).d("Queue lyrics pre-load is disabled")
                    return@launch
                }
                
                // Check network connectivity
                val isNetworkAvailable = try {
                    networkConnectivity.isCurrentlyConnected()
                } catch (_: Exception) {
                    true
                }
                
                if (!isNetworkAvailable) {
                    Timber.tag(TAG).w("Network unavailable, skipping lyrics pre-load")
                    return@launch
                }
                
                val preloadCount = preferences[QueueLyricsPreloadCountKey] ?: DEFAULT_PRELOAD_COUNT
                
                // Get next N songs after current index
                val nextSongs = getNextSongs(queue, currentIndex, preloadCount)
                
                if (nextSongs.isEmpty()) {
                    Timber.tag(TAG).d("No songs to pre-load")
                    return@launch
                }

                Timber.tag(TAG).d("Starting pre-load for ${nextSongs.size} songs")
                preloadLyrics(nextSongs)
                
            } catch (e: Exception) {
                reportException(e)
            }
        }
    }

    /**
     * Get the next N songs from the queue after the current index.
     */
    private fun getNextSongs(queue: List<MediaMetadata>, currentIndex: Int, count: Int): List<MediaMetadata> {
        if (queue.isEmpty() || currentIndex < 0) {
            return emptyList()
        }
        
        val startIndex = currentIndex + 1
        val endIndex = minOf(startIndex + count, queue.size)
        
        if (startIndex >= queue.size) {
            return emptyList()
        }
        
        return queue.subList(startIndex, endIndex)
    }

    /**
     * Preload lyrics for the given songs.
     * Uses parallel fetching with limited concurrency.
     */
    private fun preloadLyrics(songs: List<MediaMetadata>) {
        preloadJob = scope.launch {
            try {
                // Process songs with limited concurrency
                songs.forEach { song ->
                    // Check if lyrics already exist in database
                    val existingLyrics = database.lyrics(song.id).first()
                    if (existingLyrics != null && existingLyrics.lyrics != LyricsEntity.LYRICS_NOT_FOUND) {
                        Timber.tag(TAG).d("Lyrics already cached for: ${song.title}")
                        return@forEach
                    }
                    
                    // Fetch lyrics for this song
                    try {
                        val lyrics = fetchLyricsForSong(song)
                        if (lyrics != null && lyrics != LyricsEntity.LYRICS_NOT_FOUND) {
                            // Save to database using query block
                            database.query {
                                upsert(
                                    LyricsEntity(
                                        id = song.id,
                                        lyrics = lyrics
                                    )
                                )
                            }
                            Timber.tag(TAG).d("Pre-loaded lyrics for: ${song.title}")
                        }
                    } catch (e: Exception) {
                        Timber.tag(TAG).w("Failed to pre-load lyrics for ${song.title}: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                reportException(e)
            }
        }
    }

    /**
     * Fetch lyrics for a single song using the LyricsHelper.
     * This is a simplified version that gets lyrics from enabled providers.
     */
    private suspend fun fetchLyricsForSong(song: MediaMetadata): String? {
        val lyricsHelper = LyricsHelper(context, networkConnectivity)
        
        return try {
            lyricsHelper.getLyrics(song, preferredProviderOnly = true)
        } catch (e: Exception) {
            Timber.tag(TAG).w("Error fetching lyrics for ${song.title}: ${e.message}")
            null
        }
    }

    /**
     * Cancel any ongoing preload operations.
     */
    fun cancel() {
        preloadJob?.cancel()
        preloadJob = null
    }

    /**
     * Clean up resources when no longer needed.
     */
    fun destroy() {
        cancel()
        scope.cancel()
    }

    companion object {
        private const val TAG = "LyricsPreloadManager"
        private const val DEFAULT_PRELOAD_COUNT = 3
    }
}
