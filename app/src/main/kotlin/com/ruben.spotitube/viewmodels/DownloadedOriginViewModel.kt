package com.ruben.spotitube.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ruben.spotitube.db.MusicDatabase
import com.ruben.spotitube.db.entities.DownloadOriginEntity
import com.ruben.spotitube.db.entities.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DownloadedOriginViewModel
@Inject
constructor(
    database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val type: String = savedStateHandle.get<String>("type")!!
    val originId: String = savedStateHandle.get<String>("originId")!!

    val songs: StateFlow<List<Song>> = database.downloadedSongsByOrigin(type, originId)
        .map { list -> list.map { it.toSong() } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val info: StateFlow<DownloadOriginEntity?> = database.downloadOriginInfo(type, originId)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)
}
