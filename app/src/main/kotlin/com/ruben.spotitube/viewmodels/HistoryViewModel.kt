/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ruben.spotitube.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import com.ruben.spotitube.db.MusicDatabase
import com.ruben.spotitube.db.entities.HistorySongEntity
import com.ruben.spotitube.models.DateAgo
import com.ruben.spotitube.utils.HistoryItemMemoryCache
import com.ruben.spotitube.utils.HistoryMetadataLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

sealed class HistoryUiModel {
    data class Item(val entity: HistorySongEntity) : HistoryUiModel()
    data class Header(val dateAgo: DateAgo) : HistoryUiModel()
}

fun calculateDateAgo(playedAt: Long, today: LocalDate): DateAgo {
    val thisMonday = today.with(DayOfWeek.MONDAY)
    val lastMonday = thisMonday.minusDays(7)
    val date = Instant.ofEpochMilli(playedAt).atZone(ZoneId.systemDefault()).toLocalDate()
    val daysAgo = ChronoUnit.DAYS.between(date, today).toInt()
    return when {
        daysAgo == 0 -> DateAgo.Today
        daysAgo == 1 -> DateAgo.Yesterday
        date >= thisMonday -> DateAgo.ThisWeek
        date >= lastMonday -> DateAgo.LastWeek
        else -> DateAgo.Other(date.withDayOfMonth(1))
    }
}

private fun PagingData<HistoryUiModel>.insertDateSeparators(today: LocalDate): PagingData<HistoryUiModel> {
    return this.insertSeparators { before: HistoryUiModel?, after: HistoryUiModel? ->
        val b = (before as? HistoryUiModel.Item)?.entity
        val a = (after as? HistoryUiModel.Item)?.entity
        val dateB = b?.let { calculateDateAgo(it.playedAt, today) }
        val dateA = a?.let { calculateDateAgo(it.playedAt, today) }
        if (dateA != null && dateB != dateA) {
            HistoryUiModel.Header(dateA)
        } else null
    }
}

@HiltViewModel
class HistoryViewModel
@Inject
constructor(
    val database: MusicDatabase,
) : ViewModel() {
    private val today = LocalDate.now()

    val queryState = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val historyPagingData = queryState
        .debounce { query -> if (query.isBlank()) 0.milliseconds else 300.milliseconds }
        .flatMapLatest { query ->
            val q = query.trim()
            if (q.isBlank()) {
                Pager(
                    config = PagingConfig(pageSize = 30, enablePlaceholders = false)
                ) {
                    database.historySongsPagingSource()
                }.flow.map { pagingData ->
                    pagingData
                        .map { HistoryUiModel.Item(it) as HistoryUiModel }
                        .insertDateSeparators(today)
                }
            } else {
                database.recentHistorySongs(300).map { recentSongs ->
                    val songIds = recentSongs.map { it.songId }
                    HistoryMetadataLoader.resolve(songIds, timeoutMs = 3000L)
                    val filtered = recentSongs.filter { entity ->
                        val songItem = HistoryItemMemoryCache.get(entity.songId)
                        songItem?.title?.contains(q, ignoreCase = true) == true ||
                                songItem?.artists?.any { it.name.contains(q, ignoreCase = true) } == true
                    }
                    PagingData.from(filtered.map { HistoryUiModel.Item(it) as HistoryUiModel })
                        .insertDateSeparators(today)
                }
            }
        }
        .flowOn(Dispatchers.Default)
        .cachedIn(viewModelScope)

    fun loadMetadataForIds(ids: List<String>): Job? {
        return HistoryMetadataLoader.loadMetadataForIds(ids)
    }
}
