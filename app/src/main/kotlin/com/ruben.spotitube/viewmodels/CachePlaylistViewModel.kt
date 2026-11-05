/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ruben.spotitube.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.offline.Download
import com.ruben.spotitube.db.entities.Song
import com.ruben.spotitube.models.DownloadedSongMetadata
import com.ruben.spotitube.playback.DownloadUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class CachePlaylistViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val downloadUtil: DownloadUtil
) : ViewModel() {

    val cachedSongs: StateFlow<List<Song>> = downloadUtil.downloads
        .map { downloadsMap ->
            downloadsMap.values
                .filter { it.state == Download.STATE_COMPLETED }
                .mapNotNull { download ->
                    DownloadedSongMetadata.fromDownload(download)?.toSong()
                }
                .sortedBy { it.song.title }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun removeSongFromCache(songId: String) {
        downloadUtil.downloadManager.removeDownload(songId)
    }
}
