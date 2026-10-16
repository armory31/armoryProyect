/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ruben.spotitube.viewmodels

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ruben.spotitube.constants.HistorySource
import com.ruben.spotitube.db.MusicDatabase
import com.ruben.spotitube.innertube.YouTube
import com.ruben.spotitube.innertube.pages.HistoryPage
import com.ruben.spotitube.utils.HistoryMetadataLoader
import com.ruben.spotitube.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel
@Inject
constructor(
    val database: MusicDatabase,
) : ViewModel() {
    var historySource = MutableStateFlow(HistorySource.LOCAL)

    private val today = LocalDate.now()
    private val thisMonday = today.with(DayOfWeek.MONDAY)

    val historyPage = mutableStateOf<HistoryPage?>(null)

    val rawHistory = database.allHistorySongs()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun loadMetadataForIds(ids: List<String>): Job? {
        return HistoryMetadataLoader.loadMetadataForIds(ids)
    }

    init {
        fetchRemoteHistory()
    }

    fun fetchRemoteHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            YouTube.musicHistory().onSuccess {
                historyPage.value = it
            }.onFailure {
                reportException(it)
            }
        }
    }
}
