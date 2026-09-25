/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ruben.spotitube.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.datasource.cache.Cache
import com.ruben.spotitube.constants.HideExplicitKey
import com.ruben.spotitube.db.MusicDatabase
import com.ruben.spotitube.db.entities.Song
import com.ruben.spotitube.di.DownloadCache
import com.ruben.spotitube.di.PlayerCache
import com.ruben.spotitube.utils.dataStore
import com.ruben.spotitube.utils.get
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class CachePlaylistViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    @PlayerCache private val playerCache: Cache,
    @DownloadCache private val downloadCache: Cache
) : ViewModel() {

    private val _cachedSongs = MutableStateFlow<List<Song>>(emptyList())
    val cachedSongs: StateFlow<List<Song>> = _cachedSongs

    init {
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                val cachedIds = playerCache.keys.toSet()
                val downloadedIds = downloadCache.keys.toSet()
                val pureCacheIds = cachedIds.subtract(downloadedIds)

                val songs = if (pureCacheIds.isNotEmpty()) {
                    database.getSongsByIds(pureCacheIds.toList())
                } else {
                    emptyList()
                }

                val completeSongs = songs.filter {
                    val contentLength = it.format?.contentLength
                    contentLength != null && playerCache.isCached(it.song.id, 0, contentLength)
                }

                if (completeSongs.isNotEmpty()) {
                    database.query {
                        completeSongs.forEach {
                            if (it.song.dateDownload == null) {
                                update(it.song.copy(dateDownload = LocalDateTime.now()))
                            }
                        }
                    }
                }

                _cachedSongs.value = completeSongs
                    .filter { it.song.dateDownload != null }
                    .sortedByDescending { it.song.dateDownload }

                delay(1000)
            }
        }
    }

    fun removeSongFromCache(songId: String) {
        playerCache.removeResource(songId)
    }
}
