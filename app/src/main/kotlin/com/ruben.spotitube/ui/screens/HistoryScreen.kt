/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ruben.spotitube.ui.screens

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.ruben.spotitube.ArmoryIconButton
import com.ruben.spotitube.ArmoryTopBar
import com.ruben.spotitube.LocalPlayerAwareWindowInsets
import com.ruben.spotitube.LocalPlayerConnection
import com.ruben.spotitube.R
import com.ruben.spotitube.SectionHeader
import com.ruben.spotitube.constants.HistorySource
import com.ruben.spotitube.constants.InnerTubeCookieKey
import com.ruben.spotitube.db.entities.Event
import com.ruben.spotitube.db.entities.HistorySongEntity
import com.ruben.spotitube.db.entities.SavedArtistEntity
import com.ruben.spotitube.db.entities.SavedSongEntity
import com.ruben.spotitube.db.entities.Song
import com.ruben.spotitube.extensions.metadata
import com.ruben.spotitube.extensions.toMediaItem
import com.ruben.spotitube.extensions.togglePlayPause
import com.ruben.spotitube.innertube.models.SongItem
import com.ruben.spotitube.innertube.utils.parseCookieString
import com.ruben.spotitube.models.DateAgo
import com.ruben.spotitube.models.toMediaMetadata
import com.ruben.spotitube.models.toSavedSongEntity
import com.ruben.spotitube.playback.queues.ListQueue
import com.ruben.spotitube.playback.queues.YouTubeQueue
import com.ruben.spotitube.ui.component.ChipsRow
import com.ruben.spotitube.ui.component.HideOnScrollFAB
import com.ruben.spotitube.ui.component.LocalMenuState
import com.ruben.spotitube.ui.component.SongListItem
import com.ruben.spotitube.ui.component.YouTubeListItem
import com.ruben.spotitube.ui.menu.SelectionMediaMetadataMenu
import com.ruben.spotitube.ui.menu.SongMenu
import com.ruben.spotitube.ui.menu.YouTubeSongMenu
import com.ruben.spotitube.utils.HistoryItemMemoryCache
import com.ruben.spotitube.utils.rememberPreference
import com.ruben.spotitube.viewmodels.HistoryViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.time.Duration.Companion.milliseconds

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun HistoryScreen(
    navController: NavController,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    var selection by remember { mutableStateOf(false) }
    var isSearching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isSearching) {
        if (isSearching) {
            focusRequester.requestFocus()
        }
    }
    if (isSearching) {
        BackHandler {
            isSearching = false
            query = TextFieldValue()
        }
    } else if (selection) {
        BackHandler {
            selection = false
        }
    }

    val historySource by viewModel.historySource.collectAsState()
    val rawHistory by viewModel.rawHistory.collectAsState()
    val historyPage by viewModel.historyPage

    val innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn = remember(innerTubeCookie) {
        "SAPISID" in parseCookieString(innerTubeCookie)
    }

    fun dateAgoToString(dateAgo: DateAgo): String {
        return when (dateAgo) {
            DateAgo.Today -> context.getString(R.string.today)
            DateAgo.Yesterday -> context.getString(R.string.yesterday)
            DateAgo.ThisWeek -> context.getString(R.string.this_week)
            DateAgo.LastWeek -> context.getString(R.string.last_week)
            is DateAgo.Other -> dateAgo.date.format(DateTimeFormatter.ofPattern("yyyy/MM"))
        }
    }

    class WrappedHistoryItem(val historyEntity: HistorySongEntity) {
        var isSelected by mutableStateOf(false)
        val songItem: SongItem?
            get() = HistoryItemMemoryCache.get(historyEntity.songId)
    }

    val today = LocalDate.now()
    val thisMonday = today.with(DayOfWeek.MONDAY)
    val lastMonday = thisMonday.minusDays(7)

    val groupedHistory = remember(rawHistory, query) {
        val filtered = if (query.text.isEmpty()) {
            rawHistory
        } else {
            rawHistory.filter { historyEntity ->
                val songItem = HistoryItemMemoryCache.get(historyEntity.songId)
                songItem?.title?.contains(query.text, ignoreCase = true) == true ||
                        songItem?.artists?.any { it.name.contains(query.text, ignoreCase = true) } == true ||
                        historyEntity.songId.contains(query.text, ignoreCase = true)
            }
        }

        filtered.groupBy { historyEntity ->
            val date = Instant.ofEpochMilli(historyEntity.playedAt).atZone(ZoneId.systemDefault()).toLocalDate()
            val daysAgo = ChronoUnit.DAYS.between(date, today).toInt()
            when {
                daysAgo == 0 -> DateAgo.Today
                daysAgo == 1 -> DateAgo.Yesterday
                date >= thisMonday -> DateAgo.ThisWeek
                date >= lastMonday -> DateAgo.LastWeek
                else -> DateAgo.Other(date.withDayOfMonth(1))
            }
        }.toSortedMap(compareBy { dateAgo ->
            when (dateAgo) {
                DateAgo.Today -> 0L
                DateAgo.Yesterday -> 1L
                DateAgo.ThisWeek -> 2L
                DateAgo.LastWeek -> 3L
                is DateAgo.Other -> ChronoUnit.DAYS.between(dateAgo.date, today)
            }
        })
    }

    val wrappedItemsMap = remember(groupedHistory) {
        groupedHistory.mapValues { (_, historyList) ->
            historyList.map { WrappedHistoryItem(it) }.toMutableStateList()
        }
    }

    val allWrappedItems = remember(wrappedItemsMap) {
        wrappedItemsMap.values.flatten()
    }

    val lazyListState = rememberLazyListState()

    // Load visible items with margin of 20 above and below, debounced
    LaunchedEffect(lazyListState, rawHistory, query) {
        if (query.text.isNotEmpty()) {
            val recentIds = rawHistory.take(300).map { it.songId }.distinct()
            viewModel.loadMetadataForIds(recentIds)
        } else {
            snapshotFlow { lazyListState.layoutInfo }
                .debounce(300.milliseconds)
                .collectLatest { layoutInfo ->
                    val visibleIndices = layoutInfo.visibleItemsInfo.map { it.index }
                    if (visibleIndices.isNotEmpty()) {
                        val allItems = allWrappedItems
                        val minIdx = (visibleIndices.minOrNull() ?: 0) - 20
                        val maxIdx = (visibleIndices.maxOrNull() ?: 0) + 20
                        val visibleIds = allItems.indices
                            .filter { it in minIdx..maxIdx }
                            .mapNotNull { allItems.getOrNull(it)?.historyEntity?.songId }
                        viewModel.loadMetadataForIds(visibleIds)
                    }
                }
        }
    }

    val filteredRemoteContent = remember(historyPage, query) {
        if (query.text.isEmpty()) {
            historyPage?.sections
        } else {
            historyPage?.sections?.map { section ->
                section.copy(
                    songs = section.songs.filter { song ->
                        song.title.contains(query.text, ignoreCase = true) ||
                                song.artists.any { it.name.contains(query.text, ignoreCase = true) }
                    }
                )
            }?.filter { it.songs.isNotEmpty() }
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                .asPaddingValues(),
            modifier = Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)
            )
        ) {
            item {
                ChipsRow(
                    chips = if (isLoggedIn) listOf(
                        HistorySource.LOCAL to stringResource(R.string.local_history),
                        HistorySource.REMOTE to stringResource(R.string.remote_history),
                    ) else {
                        listOf(HistorySource.LOCAL to stringResource(R.string.local_history))
                    },
                    currentValue = historySource,
                    onValueUpdate = {
                        viewModel.historySource.value = it
                        if (it == HistorySource.REMOTE) {
                            viewModel.fetchRemoteHistory()
                        }
                    }
                )
            }

            if (historySource == HistorySource.REMOTE && isLoggedIn) {
                filteredRemoteContent?.forEach { section ->
                    stickyHeader {
                        SectionHeader(
                            text = section.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.background)
                        )
                    }

                    items(
                        items = section.songs,
                        key = { it.id }
                    ) { song ->
                        YouTubeListItem(
                            item = song,
                            isActive = song.id == mediaMetadata?.id,
                            isPlaying = isPlaying,
                            trailingContent = {
                                ArmoryIconButton(
                                    icon = Icons.Rounded.MoreVert,
                                    contentDescription = "Opciones",
                                    onClick = {
                                        menuState.show {
                                            YouTubeSongMenu(
                                                song = song,
                                                navController = navController,
                                                onDismiss = menuState::dismiss
                                            )
                                        }
                                    }
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (song.id == mediaMetadata?.id) {
                                            playerConnection.player.togglePlayPause()
                                        } else {
                                            playerConnection.playQueue(
                                                YouTubeQueue.radio(song.toMediaMetadata())
                                            )
                                        }
                                    },
                                    onLongClick = {
                                        menuState.show {
                                            YouTubeSongMenu(
                                                song = song,
                                                navController = navController,
                                                onDismiss = menuState::dismiss
                                            )
                                        }
                                    }
                                )
                                .animateItem()
                        )
                    }
                }
            } else {
                groupedHistory.forEach { (dateAgo, historyList) ->
                    stickyHeader {
                        SectionHeader(
                            text = dateAgoToString(dateAgo),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                        )
                    }

                    val currentDateWrappedItems = wrappedItemsMap[dateAgo] ?: emptyList()

                    itemsIndexed(
                        items = currentDateWrappedItems,
                        key = { index, wrappedItem -> "${dateAgo}_${wrappedItem.historyEntity.playedAt}_${wrappedItem.historyEntity.songId}_$index" }
                    ) { index, wrappedItem ->
                        val historyEntity = wrappedItem.historyEntity
                        val songItem = wrappedItem.songItem
                        val songObj = songItem?.let { item ->
                            Song(
                                song = item.toSavedSongEntity(),
                                artists = item.artists.map { SavedArtistEntity(id = it.id ?: "", name = it.name) }
                            )
                        } ?: Song(
                            song = SavedSongEntity(
                                id = historyEntity.songId,
                                title = "ID: ${historyEntity.songId}"
                            ),
                            artists = emptyList()
                        )
                        val timestamp = java.time.LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(historyEntity.playedAt),
                            ZoneId.systemDefault()
                        )
                        val eventObj = Event(
                            id = historyEntity.playedAt,
                            songId = historyEntity.songId,
                            timestamp = timestamp,
                            playTime = historyEntity.playTimeMs
                        )

                        SongListItem(
                            song = songObj,
                            isActive = historyEntity.songId == mediaMetadata?.id,
                            isPlaying = isPlaying,
                            showInLibraryIcon = true,
                            isSelected = wrappedItem.isSelected && selection,
                            trailingContent = {
                                if (!selection && songItem != null) {
                                    ArmoryIconButton(
                                        icon = Icons.Rounded.MoreVert,
                                        contentDescription = "Opciones",
                                        onClick = {
                                            menuState.show {
                                                SongMenu(
                                                    originalSong = songObj,
                                                    event = eventObj,
                                                    navController = navController,
                                                    onDismiss = menuState::dismiss
                                                )
                                            }
                                        }
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    enabled = songItem != null,
                                    onClick = {
                                        if (songItem == null) return@combinedClickable
                                        if (!selection) {
                                            if (historyEntity.songId == mediaMetadata?.id) {
                                                playerConnection.player.togglePlayPause()
                                            } else {
                                                playerConnection.playQueue(
                                                    ListQueue(
                                                        title = dateAgoToString(dateAgo),
                                                        items = currentDateWrappedItems.mapNotNull { it.songItem?.toMediaItem() },
                                                        startIndex = index
                                                    )
                                                )
                                            }
                                        } else {
                                            wrappedItem.isSelected = !wrappedItem.isSelected
                                        }
                                    },
                                    onLongClick = {
                                        if (songItem != null) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            if (!selection) {
                                                selection = true
                                                allWrappedItems.forEach { it.isSelected = false }
                                                wrappedItem.isSelected = true
                                            }
                                        }
                                    }
                                )
                                .animateItem()
                        )
                    }
                }
            }
        }

        HideOnScrollFAB(
            visible = if (historySource == HistorySource.REMOTE) {
                filteredRemoteContent?.any { it.songs.isNotEmpty() } == true
            } else {
                allWrappedItems.isNotEmpty()
            },
            lazyListState = lazyListState,
            icon = R.drawable.shuffle,
            onClick = {
                if (historySource == HistorySource.REMOTE && historyPage != null) {
                    val songs = filteredRemoteContent?.flatMap { it.songs } ?: emptyList()
                    if (songs.isNotEmpty()) {
                        playerConnection.playQueue(
                            ListQueue(
                                title = context.getString(R.string.history),
                                items = songs.map { it.toMediaItem() }.shuffled()
                            )
                        )
                    }
                } else {
                    val validSongs = allWrappedItems.mapNotNull { it.songItem?.toMediaItem() }
                    if (validSongs.isNotEmpty()) {
                        playerConnection.playQueue(
                            ListQueue(
                                title = context.getString(R.string.history),
                                items = validSongs.shuffled()
                            )
                        )
                    }
                }
            }
        )
    }

    val topBarTitle = when {
        selection -> {
            val count = allWrappedItems.count { it.isSelected }
            pluralStringResource(R.plurals.n_song, count, count)
        }
        isSearching -> query.text.ifEmpty { stringResource(R.string.search) }
        else -> stringResource(R.string.history)
    }

    ArmoryTopBar(
        title = topBarTitle,
        onBack = {
            when {
                isSearching -> {
                    isSearching = false
                    query = TextFieldValue()
                }
                selection -> {
                    selection = false
                }
                else -> {
                    navController.navigateUp()
                }
            }
        },
        actions = {
            if (selection) {
                val count = allWrappedItems.count { it.isSelected }
                ArmoryIconButton(
                    icon = Icons.Rounded.SelectAll,
                    contentDescription = "Seleccionar",
                    onClick = {
                        val selectAll = count != allWrappedItems.size
                        allWrappedItems.forEach { it.isSelected = selectAll }
                    }
                )
                ArmoryIconButton(
                    icon = Icons.Rounded.MoreVert,
                    contentDescription = "Más",
                    onClick = {
                        menuState.show {
                            SelectionMediaMetadataMenu(
                                songSelection = allWrappedItems
                                    .filter { it.isSelected && it.songItem != null }
                                    .map { it.songItem!!.toMediaItem().metadata!! },
                                onDismiss = menuState::dismiss,
                                clearAction = { selection = false },
                                currentItems = emptyList()
                            )
                        }
                    }
                )
            } else if (!isSearching) {
                ArmoryIconButton(
                    icon = Icons.Rounded.Search,
                    contentDescription = "Buscar",
                    onClick = { isSearching = true }
                )
            }
        }
    )
}
