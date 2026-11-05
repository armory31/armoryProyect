/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ruben.spotitube.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ruben.spotitube.constants.MyTopFilter
import com.ruben.spotitube.db.MusicDatabase
import com.ruben.spotitube.models.toSavedSongEntity
import com.ruben.spotitube.utils.HistoryMetadataLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class TopPlaylistViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val top = savedStateHandle.get<String>("top")!!

    val topPeriod = MutableStateFlow(MyTopFilter.ALL_TIME)

    @OptIn(ExperimentalCoroutinesApi::class)
    val topSongs =
        topPeriod
            .flatMapLatest { period ->
                database.mostPlayedSongs(period.toTimeMillis(), top.toInt())
            }.mapLatest { rows ->
                val metadataMap = HistoryMetadataLoader.resolve(rows.map { it.id }, timeoutMs = 5000L)
                rows.mapNotNull { row ->
                    metadataMap[row.id]?.toSavedSongEntity()?.toSong()
                }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}
