/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ruben.spotitube.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ruben.spotitube.db.MusicDatabase
import com.ruben.spotitube.innertube.YouTube
import com.ruben.spotitube.innertube.models.AlbumItem
import com.ruben.spotitube.sync.LibraryStateHolder
import com.ruben.spotitube.sync.SyncManager
import com.ruben.spotitube.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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

    val isLiked = libraryStateHolder.isAlbumLiked(albumId)
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    val albumWithSongs =
        database
            .albumWithSongs(albumId)
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)
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
            val album = database.album(albumId).first()
            YouTube
                .album(albumId)
                .onSuccess {
                    playlistId.value = it.album.playlistId
                    otherVersions.value = it.otherVersions
                    database.withTransaction {
                        if (album == null) {
                            insert(it)
                        } else {
                            update(album.album, it, album.artists)
                        }
                    }
                    _fetchState.value = FetchState.Success
                }.onFailure {
                    reportException(it)
                    val isNotFound = it.message?.contains("NOT_FOUND") == true
                    if (isNotFound) {
                        database.query {
                            album?.album?.let(::delete)
                        }
                    }
                    _fetchState.value = FetchState.Failed(isNotFound = isNotFound)
                }
        }
    }

    fun toggleLike() {
        viewModelScope.launch {
            val album = albumWithSongs.value?.album
                ?: database.getAlbumById(albumId)
                ?: database.getSavedAlbum(albumId)?.toAlbum()?.album

            if (album != null) {
                database.withTransaction {
                    if (getAlbumById(album.id) == null) {
                        insert(album)
                    }
                }
                syncManager.toggleAlbumLike(album)
            } else {
                syncManager.toggleAlbumLike(
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
