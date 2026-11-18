/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ruben.spotitube.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.exoplayer.offline.Download
import com.ruben.spotitube.db.entities.Song
import com.ruben.spotitube.models.DownloadedSongMetadata
import com.ruben.spotitube.models.toSong
import com.ruben.spotitube.playback.DownloadUtil
import com.ruben.spotitube.utils.SongMetadataLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class CachePlaylistViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val downloadUtil: DownloadUtil,
) : ViewModel() {

    private val playerCacheKeysFlow = flow {
        while (true) {
            emit(downloadUtil.playerCache.keys.toSet())
            delay(2.seconds)
        }
    }.distinctUntilChanged()

    val cachedSongs: StateFlow<List<Song>> = combine(
        playerCacheKeysFlow,
        downloadUtil.downloads,
    ) { playerCacheKeys, downloadsMap ->
        playerCacheKeys to downloadsMap
    }.map { (playerCacheKeys, downloadsMap) ->
        withContext(Dispatchers.IO) {
            val completedDownloadsMap = downloadsMap.values
                .filter { it.state == Download.STATE_COMPLETED }
                .associateBy { it.request.id }

            val fullyCachedPlayerCacheIds = playerCacheKeys.filter { id ->
                val formatLength = downloadUtil.database.format(id).first()?.contentLength
                val contentLength = if ((formatLength != null) && (formatLength > 0L)) {
                    formatLength
                } else {
                    downloadUtil.playerCache.getContentMetadata(id)[ContentMetadata.KEY_CONTENT_LENGTH, -1L]
                }
                (contentLength > 0L) && downloadUtil.playerCache.isCached(id, 0, contentLength)
            }.toSet()

            val allIds = completedDownloadsMap.keys + fullyCachedPlayerCacheIds
            val resolvedSongs = mutableMapOf<String, Song>()

            // a) Completed downloads
            for ((id, download) in completedDownloadsMap) {
                DownloadedSongMetadata.fromDownload(download)?.toSong()?.let { song ->
                    resolvedSongs[id] = song
                }
            }

            // b) Saved songs from database for remaining ids
            val remainingAfterA = allIds.filter { it !in resolvedSongs }
            for (id in remainingAfterA) {
                downloadUtil.database.getSavedSong(id)?.toSong()?.let { song ->
                    resolvedSongs[id] = song
                }
            }

            // c) Remaining ids resolved via SongMetadataLoader
            val remainingAfterB = allIds.filter { it !in resolvedSongs }
            if (remainingAfterB.isNotEmpty()) {
                val historyResolved = SongMetadataLoader.resolve(remainingAfterB)
                    .mapValues { (_, songItem) -> songItem.toSong() }
                resolvedSongs.putAll(historyResolved)
            }

            resolvedSongs.values.sortedBy { it.song.title }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    fun removeSongFromCache(songId: String) {
        downloadUtil.downloadManager.removeDownload(songId)
        downloadUtil.playerCache.removeResource(songId)
    }
}
