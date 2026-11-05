/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.ruben.spotitube.ArmoryIconButton
import com.ruben.spotitube.ArmoryTopBar
import com.ruben.spotitube.LocalPlayerAwareWindowInsets
import com.ruben.spotitube.LocalPlayerConnection
import com.ruben.spotitube.R
import com.ruben.spotitube.SectionHeader
import com.ruben.spotitube.db.entities.Event
import com.ruben.spotitube.db.entities.SavedArtistEntity
import com.ruben.spotitube.db.entities.SavedSongEntity
import com.ruben.spotitube.db.entities.Song
import com.ruben.spotitube.extensions.toMediaItem
import com.ruben.spotitube.extensions.togglePlayPause
import com.ruben.spotitube.models.DateAgo
import com.ruben.spotitube.models.MediaMetadata
import com.ruben.spotitube.models.toMediaMetadata
import com.ruben.spotitube.models.toSavedSongEntity
import com.ruben.spotitube.playback.queues.ListQueue
import com.ruben.spotitube.ui.component.EmptyPlaceholder
import com.ruben.spotitube.ui.component.HideOnScrollFAB
import com.ruben.spotitube.ui.component.LocalMenuState
import com.ruben.spotitube.ui.component.SongListItem
import com.ruben.spotitube.ui.menu.SelectionMediaMetadataMenu
import com.ruben.spotitube.ui.menu.SongMenu
import com.ruben.spotitube.utils.HistoryItemMemoryCache
import com.ruben.spotitube.utils.HistoryMetadataLoader
import com.ruben.spotitube.utils.rememberPreference
import com.ruben.spotitube.viewmodels.HistoryUiModel
import com.ruben.spotitube.viewmodels.HistoryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    var selection by remember { mutableStateOf(false) }
    var selectedKeys by remember { mutableStateOf(setOf<String>()) }
    var isSearching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(query.text) {
        viewModel.queryState.value = query.text
    }

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
            selectedKeys = emptySet()
        }
    }

    val lazyPagingItems = viewModel.historyPagingData.collectAsLazyPagingItems()

    fun dateAgoToString(dateAgo: DateAgo): String {
        return when (dateAgo) {
            DateAgo.Today -> context.getString(R.string.today)
            DateAgo.Yesterday -> context.getString(R.string.yesterday)
            DateAgo.ThisWeek -> context.getString(R.string.this_week)
            DateAgo.LastWeek -> context.getString(R.string.last_week)
            is DateAgo.Other -> dateAgo.date.format(DateTimeFormatter.ofPattern("yyyy/MM"))
        }
    }

    val lazyListState = rememberLazyListState()

    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.layoutInfo.visibleItemsInfo }
            .debounce(200.milliseconds)
            .collectLatest { visibleItems ->
                val visibleIds = visibleItems.mapNotNull { item ->
                    val uiModel = lazyPagingItems.peek(item.index)
                    (uiModel as? HistoryUiModel.Item)?.entity?.songId
                }
                if (visibleIds.isNotEmpty()) {
                    viewModel.loadMetadataForIds(visibleIds)
                }
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
            if (lazyPagingItems.itemCount == 0 && lazyPagingItems.loadState.refresh !is LoadState.Loading) {
                item {
                    EmptyPlaceholder(
                        icon = R.drawable.history,
                        text = stringResource(R.string.empty_history)
                    )
                }
            } else {
                    items(
                        count = lazyPagingItems.itemCount,
                        key = { index ->
                            when (val uiModel = lazyPagingItems.peek(index)) {
                                is HistoryUiModel.Item -> "${uiModel.entity.playedAt}_${uiModel.entity.songId}"
                                is HistoryUiModel.Header -> "header_${uiModel.dateAgo}"
                                null -> "placeholder_$index"
                            }
                        },
                        contentType = { index ->
                            when (lazyPagingItems.peek(index)) {
                                is HistoryUiModel.Item -> "history_item"
                                is HistoryUiModel.Header -> "header"
                                null -> "placeholder"
                            }
                        }
                    ) { index ->
                        when (val uiModel = lazyPagingItems[index]) {
                            is HistoryUiModel.Header -> {
                                SectionHeader(
                                    text = dateAgoToString(uiModel.dateAgo),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface)
                                )
                            }
                            is HistoryUiModel.Item -> {
                                val historyEntity = uiModel.entity
                                val itemKey = "${historyEntity.playedAt}_${historyEntity.songId}"
                                val isItemSelected = selectedKeys.contains(itemKey)
                                val songItem = HistoryItemMemoryCache.get(historyEntity.songId)
                                val songObj = songItem?.let { item ->
                                    Song(
                                        song = item.toSavedSongEntity(),
                                        artists = item.artists.map { SavedArtistEntity(id = it.id ?: "", name = it.name) }
                                    )
                                } ?: Song(
                                    song = SavedSongEntity(
                                        id = historyEntity.songId,
                                        title = ""
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
                                    isSelected = isItemSelected && selection,
                                    trailingContent = {
                                        if (!selection && songItem != null) {
                                            ArmoryIconButton(
                                                icon = Icons.Rounded.MoreVert,
                                                contentDescription = stringResource(R.string.more_options),
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
                                                    } else if (query.text.isNotBlank()) {
                                                        val validItems = (0 until lazyPagingItems.itemCount)
                                                            .mapNotNull { idx -> (lazyPagingItems.peek(idx) as? HistoryUiModel.Item)?.entity }
                                                        val startIndex = validItems.indexOfFirst {
                                                            it.playedAt == historyEntity.playedAt && it.songId == historyEntity.songId
                                                        }.coerceAtLeast(0)
                                                        val mediaItems = validItems.map { entity ->
                                                            HistoryItemMemoryCache.get(entity.songId)?.toMediaItem()
                                                                ?: MediaMetadata(
                                                                    id = entity.songId,
                                                                    title = "",
                                                                    artists = emptyList(),
                                                                    duration = -1
                                                                ).toMediaItem()
                                                        }

                                                        if (mediaItems.isNotEmpty()) {
                                                            playerConnection.playQueue(
                                                                ListQueue(
                                                                    title = context.getString(R.string.history),
                                                                    items = mediaItems,
                                                                    startIndex = startIndex
                                                                )
                                                            )
                                                        }
                                                    } else {
                                                        coroutineScope.launch(Dispatchers.IO) {
                                                            val historyEntities = viewModel.database.getHistorySongsDirect()
                                                            val clickIndex = historyEntities.indexOfFirst {
                                                                it.playedAt == historyEntity.playedAt && it.songId == historyEntity.songId
                                                            }.coerceAtLeast(0)

                                                            val upcomingIds = historyEntities.drop(clickIndex).take(21).map { it.songId }
                                                            HistoryMetadataLoader.resolve(upcomingIds, timeoutMs = 2000L)

                                                            val validPairs = historyEntities.mapNotNull { entity ->
                                                                HistoryItemMemoryCache.get(entity.songId)?.let { entity to it.toMediaItem() }
                                                                    ?: (entity to MediaMetadata(id = entity.songId, title = "", artists = emptyList(), duration = -1).toMediaItem())
                                                            }
                                                            val startIndex = validPairs.indexOfFirst {
                                                                it.first.playedAt == historyEntity.playedAt && it.first.songId == historyEntity.songId
                                                            }.coerceAtLeast(0)

                                                            val mediaItems = validPairs.map { it.second }
                                                            if (mediaItems.isNotEmpty()) {
                                                                withContext(Dispatchers.Main) {
                                                                    playerConnection.playQueue(
                                                                        ListQueue(
                                                                            title = context.getString(R.string.history),
                                                                            items = mediaItems,
                                                                            startIndex = startIndex
                                                                        )
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                } else {
                                                    selectedKeys = if (isItemSelected) {
                                                        selectedKeys - itemKey
                                                    } else {
                                                        selectedKeys + itemKey
                                                    }
                                                    if (selectedKeys.isEmpty()) {
                                                        selection = false
                                                    }
                                                }
                                            },
                                            onLongClick = {
                                                if (songItem != null) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    if (!selection) {
                                                        selection = true
                                                        selectedKeys = setOf(itemKey)
                                                    }
                                                }
                                            }
                                        )
                                        .animateItem()
                                )
                            }
                            null -> {}
                        }
                    }
                }
            }

        HideOnScrollFAB(
            visible = lazyPagingItems.itemCount > 0,
            lazyListState = lazyListState,
            icon = R.drawable.shuffle,
            onClick = {
                if (query.text.isNotBlank()) {
                    val filteredItems = (0 until lazyPagingItems.itemCount)
                        .mapNotNull { idx -> lazyPagingItems.peek(idx) as? HistoryUiModel.Item }
                        .map { item ->
                            HistoryItemMemoryCache.get(item.entity.songId)?.toMediaItem()
                                ?: MediaMetadata(
                                    id = item.entity.songId,
                                    title = "",
                                    artists = emptyList(),
                                    duration = -1
                                ).toMediaItem()
                        }.shuffled()
                    if (filteredItems.isNotEmpty()) {
                        playerConnection.playQueue(
                            ListQueue(
                                title = context.getString(R.string.history),
                                items = filteredItems
                            )
                        )
                    }
                } else {
                    coroutineScope.launch(Dispatchers.IO) {
                        val historyEntities = viewModel.database.getHistorySongsDirect()
                        val items = historyEntities.map { entity ->
                            HistoryItemMemoryCache.get(entity.songId)?.toMediaItem()
                                ?: MediaMetadata(
                                    id = entity.songId,
                                    title = "",
                                    artists = emptyList(),
                                    duration = -1
                                ).toMediaItem()
                        }.shuffled()
                        if (items.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                playerConnection.playQueue(
                                    ListQueue(
                                        title = context.getString(R.string.history),
                                        items = items
                                    )
                                )
                            }
                        }
                    }
                }
            }
        )
    }

    val topBarTitle = when {
        selection -> {
            val count = selectedKeys.size
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
                    selectedKeys = emptySet()
                }
                else -> {
                    navController.navigateUp()
                }
            }
        },
        actions = {
            if (selection) {
                val loadedItemKeys = (0 until lazyPagingItems.itemCount).mapNotNull { idx ->
                    (lazyPagingItems.peek(idx) as? HistoryUiModel.Item)?.let { "${it.entity.playedAt}_${it.entity.songId}" }
                }
                ArmoryIconButton(
                    icon = Icons.Rounded.SelectAll,
                    contentDescription = stringResource(R.string.select),
                    onClick = {
                        val allSelected = loadedItemKeys.isNotEmpty() && loadedItemKeys.all { selectedKeys.contains(it) }
                        selectedKeys = if (allSelected) emptySet() else loadedItemKeys.toSet()
                        if (selectedKeys.isEmpty()) {
                            selection = false
                        }
                    }
                )
                ArmoryIconButton(
                    icon = Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.more_options),
                    onClick = {
                        menuState.show {
                            val selectedMetadatas = selectedKeys.mapNotNull { key ->
                                val songId = key.substringAfter("_")
                                HistoryItemMemoryCache.get(songId)?.toMediaMetadata()
                            }
                            SelectionMediaMetadataMenu(
                                songSelection = selectedMetadatas,
                                onDismiss = menuState::dismiss,
                                clearAction = {
                                    selection = false
                                    selectedKeys = emptySet()
                                },
                                currentItems = emptyList()
                            )
                        }
                    }
                )
            } else if (!isSearching) {
                ArmoryIconButton(
                    icon = Icons.Rounded.Search,
                    contentDescription = stringResource(R.string.search),
                    onClick = { isSearching = true }
                )
            }
        }
    )
}
