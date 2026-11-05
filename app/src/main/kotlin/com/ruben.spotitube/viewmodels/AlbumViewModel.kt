/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ruben.spotitube.viewmodels

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ruben.spotitube.db.MusicDatabase
import com.ruben.spotitube.db.entities.AlbumWithSongs
import com.ruben.spotitube.innertube.YouTube
import com.ruben.spotitube.innertube.models.AlbumItem
import com.ruben.spotitube.models.toAlbumWithSongs
import com.ruben.spotitube.sync.LibraryStateHolder
import com.ruben.spotitube.sync.SyncManager
import com.ruben.spotitube.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AlbumUiState {
    data object Loading : AlbumUiState

    data object Content : AlbumUiState

    data object Empty : AlbumUiState

    data class Error(
        val isNotFound: Boolean = false,
    ) : AlbumUiState
}

private sealed interface FetchState {
    data object Pending : FetchState
    data object Success : FetchState
    data class Failed(val isNotFound: Boolean = false) : FetchState
}

@HiltViewModel
class AlbumViewModel
@Inject
constructor(
    private val database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
    libraryStateHolder: LibraryStateHolder,
    private val syncManager: SyncManager
) : ViewModel() {
    val albumId = savedStateHandle.get<String>("albumId")!!
    val playlistId = MutableStateFlow("")

    val isSaved = libraryStateHolder.isSavedAlbum(albumId)
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    private val onlineAlbumWithSongs = MutableStateFlow<AlbumWithSongs?>(null)

    val albumWithSongs: StateFlow<AlbumWithSongs?> = combine(
        database.albumWithSongs(albumId),
        onlineAlbumWithSongs
    ) { dbData, onlineData ->
        if (dbData != null && dbData.songs.isNotEmpty()) {
            dbData
        } else {
            onlineData
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    var otherVersions = MutableStateFlow<List<AlbumItem>>(emptyList())

    private val _fetchState = MutableStateFlow<FetchState>(FetchState.Pending)

    val uiState: StateFlow<AlbumUiState> =
        combine(albumWithSongs, _fetchState) { data, fetch ->
            when {
                data != null && data.songs.isNotEmpty() -> AlbumUiState.Content
                fetch is FetchState.Pending -> AlbumUiState.Loading
                fetch is FetchState.Failed && data == null -> AlbumUiState.Error(fetch.isNotFound)
                fetch is FetchState.Success && data != null && data.songs.isEmpty() -> AlbumUiState.Empty
                fetch is FetchState.Failed && data != null && data.songs.isNotEmpty() -> AlbumUiState.Content
                else -> AlbumUiState.Loading
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, AlbumUiState.Loading)

    init {
        retry()
    }

    fun retry() {
        viewModelScope.launch {
            _fetchState.value = FetchState.Pending
            YouTube
                .album(albumId)
                .onSuccess { albumPage ->
                    Log.d("SAVED_ALBUM_DEBUG", "AlbumViewModel: Obtenidas ${albumPage.songs.size} canciones desde InnerTube para el browseId $albumId")
                    playlistId.value = albumPage.album.playlistId
                    otherVersions.value = albumPage.otherVersions
                    onlineAlbumWithSongs.value = albumPage.toAlbumWithSongs()
                    _fetchState.value = FetchState.Success
                }.onFailure {
                    reportException(it)
                    val isNotFound = it.message?.contains("NOT_FOUND") == true
                    _fetchState.value = FetchState.Failed(isNotFound = isNotFound)
                }
        }
    }

    fun toggleSaved() {
        viewModelScope.launch {
            val current = albumWithSongs.value
            val album = current?.album
                ?: database.getAlbumById(albumId)
                ?: database.getSavedAlbum(albumId)?.toAlbum()?.album

            if (album != null) {
                syncManager.toggleAlbumSaved(album)
            } else {
                syncManager.toggleAlbumSaved(
                    albumId = albumId,
                    title = ""
                )
            }
        }
    }

    fun addAllToPlaylist(playlistId: String) {
        viewModelScope.launch {
            syncManager.addAlbumSongsToPlaylist(playlistId, albumId)
        }
    }
}
