/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ruben.spotitube.ui.screens.library

import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.ruben.spotitube.ArmoryColors
import com.ruben.spotitube.GoldDivider
import com.ruben.spotitube.LocalDatabase
import com.ruben.spotitube.LocalPlayerAwareWindowInsets
import com.ruben.spotitube.LocalPlayerConnection
import com.ruben.spotitube.R
import com.ruben.spotitube.constants.MixSortDescendingKey
import com.ruben.spotitube.constants.MixSortType
import com.ruben.spotitube.constants.MixSortTypeKey
import com.ruben.spotitube.constants.PlaylistSortType
import com.ruben.spotitube.constants.PlaylistSortTypeKey
import com.ruben.spotitube.constants.PlaylistTagsFilterKey
import com.ruben.spotitube.constants.ShowCachedPlaylistKey
import com.ruben.spotitube.constants.ShowDownloadedPlaylistKey
import com.ruben.spotitube.constants.ShowLikedPlaylistKey
import com.ruben.spotitube.constants.ShowTopPlaylistKey
import com.ruben.spotitube.constants.YtmSyncKey
import com.ruben.spotitube.db.entities.Playlist
import com.ruben.spotitube.db.entities.PlaylistEntity
import com.ruben.spotitube.extensions.move
import com.ruben.spotitube.playback.queues.LocalAlbumRadio
import com.ruben.spotitube.ui.component.LibraryAlbumSpotlightCard
import com.ruben.spotitube.ui.component.LibraryArtistSpotlightCard
import com.ruben.spotitube.ui.component.LibraryPlaylistListItem
import com.ruben.spotitube.ui.component.LocalMenuState
import com.ruben.spotitube.ui.component.SortHeader
import com.ruben.spotitube.ui.menu.AlbumMenu
import com.ruben.spotitube.ui.menu.ArtistMenu
import com.ruben.spotitube.utils.rememberEnumPreference
import com.ruben.spotitube.utils.rememberPreference
import com.ruben.spotitube.viewmodels.LibraryMixViewModel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.text.Collator
import java.util.Locale

@Composable
fun LibraryMixScreen(
    navController: NavController,
    filterContent: @Composable () -> Unit,
    viewModel: LibraryMixViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val database = LocalDatabase.current

    val (sortType, onSortTypeChange) = rememberEnumPreference(
        MixSortTypeKey,
        MixSortType.CREATE_DATE,
    )
    val (sortDescending, onSortDescendingChange) = rememberPreference(MixSortDescendingKey, true)
    val (playlistSortType) = rememberEnumPreference(PlaylistSortTypeKey, PlaylistSortType.CUSTOM)
    val (ytmSync) = rememberPreference(YtmSyncKey, true)
    val (selectedTagsFilter) = rememberPreference(PlaylistTagsFilterKey, "")
    val selectedTagIds = remember(selectedTagsFilter) {
        selectedTagsFilter.split(",").filter { it.isNotBlank() }.toSet()
    }
    val filteredPlaylistIds by database.playlistIdsByTags(
        if (selectedTagIds.isEmpty()) emptyList() else selectedTagIds.toList(),
    ).collectAsState(initial = emptyList())

    val topSize by viewModel.topValue.collectAsState(initial = "50")
    val likedTitle = stringResource(R.string.liked)
    val downloadedTitle = stringResource(R.string.offline)
    val cachedTitle = stringResource(R.string.cached_playlist)
    val topTitle = stringResource(R.string.my_top) + " $topSize"

    val likedPlaylist = remember(likedTitle) {
        Playlist(
            playlist = PlaylistEntity(id = "AUTO_LIKED_LIBRARY", name = likedTitle, isEditable = false),
            songCount = 0,
            songThumbnails = emptyList(),
        )
    }
    val downloadPlaylist = remember(downloadedTitle) {
        Playlist(
            playlist = PlaylistEntity(id = "AUTO_DOWNLOADED_LIBRARY", name = downloadedTitle, isEditable = false),
            songCount = 0,
            songThumbnails = emptyList(),
        )
    }
    val topPlaylist = remember(topTitle) {
        Playlist(
            playlist = PlaylistEntity(id = "AUTO_TOP_LIBRARY", name = topTitle, isEditable = false),
            songCount = 0,
            songThumbnails = emptyList(),
        )
    }
    val cachePlaylist = remember(cachedTitle) {
        Playlist(
            playlist = PlaylistEntity(id = "AUTO_CACHED_LIBRARY", name = cachedTitle, isEditable = false),
            songCount = 0,
            songThumbnails = emptyList(),
        )
    }

    val (showLiked) = rememberPreference(ShowLikedPlaylistKey, true)
    val (showDownloaded) = rememberPreference(ShowDownloadedPlaylistKey, true)
    val (showTop) = rememberPreference(ShowTopPlaylistKey, true)
    val (showCached) = rememberPreference(ShowCachedPlaylistKey, true)

    val albums by viewModel.albums.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val playlists by viewModel.playlists.collectAsState()

    val collator = remember {
        Collator.getInstance(Locale.getDefault()).apply {
            strength = Collator.PRIMARY
        }
    }

    val visiblePlaylists = remember(playlists, selectedTagIds, filteredPlaylistIds) {
        if (selectedTagIds.isEmpty()) {
            playlists
        } else {
            playlists.filter { it.id in filteredPlaylistIds }
        }
    }
    val sortedAlbums = remember(albums, sortType, sortDescending, collator) {
        val sorted = when (sortType) {
            MixSortType.CREATE_DATE -> albums.sortedBy { it.album.bookmarkedAt }
            MixSortType.NAME -> albums.sortedWith(compareBy(collator) { it.album.title })
            MixSortType.LAST_UPDATED -> albums.sortedBy { it.album.lastUpdateTime }
        }
        if (sortDescending) sorted.asReversed() else sorted
    }
    val sortedArtists = remember(artists, sortType, sortDescending, collator) {
        val sorted = when (sortType) {
            MixSortType.CREATE_DATE -> artists.sortedBy { it.artist.bookmarkedAt }
            MixSortType.NAME -> artists.sortedWith(compareBy(collator) { it.artist.name })
            MixSortType.LAST_UPDATED -> artists.sortedBy { it.artist.lastUpdateTime }
        }
        if (sortDescending) sorted.asReversed() else sorted
    }

    val shortcuts = buildList {
        if (showLiked) {
            add(
                LibraryShortcutItem(
                    id = "liked",
                    title = likedPlaylist.playlist.name,
                    iconRes = R.drawable.favorite,
                    route = "auto_playlist/liked",
                    accentColor = ArmoryColors.Copper2,
                ),
            )
        }
        if (showDownloaded) {
            add(
                LibraryShortcutItem(
                    id = "downloaded",
                    title = downloadPlaylist.playlist.name,
                    iconRes = R.drawable.offline,
                    route = "auto_playlist/downloaded",
                    accentColor = ArmoryColors.Silver2,
                ),
            )
        }
        if (showCached) {
            add(
                LibraryShortcutItem(
                    id = "cached",
                    title = cachePlaylist.playlist.name,
                    iconRes = R.drawable.cached,
                    route = "cache_playlist/cached",
                    accentColor = ArmoryColors.Gold2,
                ),
            )
        }
        if (showTop) {
            add(
                LibraryShortcutItem(
                    id = "top",
                    title = topPlaylist.playlist.name,
                    iconRes = R.drawable.trending_up,
                    route = "top_playlist/$topSize",
                    accentColor = ArmoryColors.Gold1,
                ),
            )
        }
    }

    val lazyListState = rememberLazyListState()
    val customPlaylistMode = playlistSortType == PlaylistSortType.CUSTOM
    val canEnterReorderMode = customPlaylistMode && selectedTagIds.isEmpty()
    var reorderEnabled by rememberSaveable { mutableStateOf(false) }
    val canReorderPlaylists = canEnterReorderMode && reorderEnabled
    val playlistSectionLeadingItems = 3 + if (shortcuts.isNotEmpty()) 1 else 0
    val mutableVisiblePlaylists = remember { mutableStateListOf<Playlist>() }
    var dragInfo by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val reorderableState = rememberReorderableLazyListState(
        lazyListState = lazyListState,
        scrollThresholdPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
    ) { from, to ->
        if (!canReorderPlaylists) return@rememberReorderableLazyListState
        if (from.index < playlistSectionLeadingItems || to.index < playlistSectionLeadingItems) {
            return@rememberReorderableLazyListState
        }

        val fromIndex = from.index - playlistSectionLeadingItems
        val toIndex = to.index - playlistSectionLeadingItems
        if (fromIndex !in mutableVisiblePlaylists.indices || toIndex !in mutableVisiblePlaylists.indices) {
            return@rememberReorderableLazyListState
        }

        val currentDragInfo = dragInfo
        dragInfo = if (currentDragInfo == null) fromIndex to toIndex else currentDragInfo.first to toIndex
        mutableVisiblePlaylists.move(fromIndex, toIndex)
    }

    LaunchedEffect(visiblePlaylists, canReorderPlaylists, reorderableState.isAnyItemDragging, dragInfo) {
        if (!canReorderPlaylists) {
            mutableVisiblePlaylists.clear()
            mutableVisiblePlaylists.addAll(visiblePlaylists)
            return@LaunchedEffect
        }

        if (!reorderableState.isAnyItemDragging && dragInfo == null) {
            mutableVisiblePlaylists.clear()
            mutableVisiblePlaylists.addAll(visiblePlaylists)
        }
    }

    LaunchedEffect(reorderableState.isAnyItemDragging, canReorderPlaylists) {
        if (!canReorderPlaylists || reorderableState.isAnyItemDragging) return@LaunchedEffect

        dragInfo ?: return@LaunchedEffect
        val playlistsToReorder = mutableVisiblePlaylists.toList()
        database.withTransaction {
            playlistsToReorder.forEachIndexed { index, playlist ->
                setPlaylistCustomOrder(playlist.id, index)
            }
        }
        dragInfo = null
    }

    LaunchedEffect(canEnterReorderMode) {
        if (!canEnterReorderMode) reorderEnabled = false
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop = backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsState()

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            lazyListState.animateScrollToItem(0)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    LaunchedEffect(ytmSync) {
        if (ytmSync) {
            viewModel.syncAllLibrary()
        }
    }

    LazyColumn(
        state = lazyListState,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 120.dp),
    ) {
        item(key = "filter", contentType = "filter") {
            filterContent()
            Spacer(Modifier.height(8.dp))
            GoldDivider(modifier = Modifier.padding(horizontal = 20.dp))
        }

        item(key = "controls", contentType = "controls") {
            LibraryControlCard(
                canEnterReorderMode = canEnterReorderMode,
                reorderEnabled = reorderEnabled,
                onToggleReorder = { reorderEnabled = !reorderEnabled },
            ) {
                SortHeader(
                    sortType = sortType,
                    sortDescending = sortDescending,
                    onSortTypeChange = onSortTypeChange,
                    onSortDescendingChange = onSortDescendingChange,
                    sortTypeText = { type ->
                        when (type) {
                            MixSortType.CREATE_DATE -> R.string.sort_by_create_date
                            MixSortType.LAST_UPDATED -> R.string.sort_by_last_updated
                            MixSortType.NAME -> R.string.sort_by_name
                        }
                    },
                )
            }
        }

        if (shortcuts.isNotEmpty()) {
            item(key = "shortcuts", contentType = "shortcuts") {
                LibraryShortcutsRow(
                    shortcuts = shortcuts,
                    onClick = { navController.navigate(it) },
                )
            }
        }

        if (customPlaylistMode && canReorderPlaylists) {
            itemsIndexed(
                items = mutableVisiblePlaylists,
                key = { _, item -> item.id },
                contentType = { _, _ -> "playlist_item" }
            ) { _, item ->
                ReorderableItem(
                    state = reorderableState,
                    key = item.id,
                ) {
                    LibraryPlaylistListItem(
                        navController = navController,
                        menuState = menuState,
                        coroutineScope = coroutineScope,
                        playlist = item,
                        showDragHandle = true,
                        dragHandleModifier = Modifier.draggableHandle(),
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .animateItem(),
                    )
                }
            }
        } else {
            items(
                items = visiblePlaylists,
                key = { it.id },
                contentType = { "playlist_item" }
            ) { item ->
                LibraryPlaylistListItem(
                    navController = navController,
                    menuState = menuState,
                    coroutineScope = coroutineScope,
                    playlist = item,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .animateItem(),
                )
            }
        }

        if (sortedAlbums.isNotEmpty()) {
            items(
                items = sortedAlbums,
                key = { it.id },
                contentType = { "spotlight_album" }
            ) { album ->
                LibraryAlbumSpotlightCard(
                    album = album,
                    isActive = album.id == mediaMetadata?.album?.id,
                    isPlaying = isPlaying,
                    onPlay = {
                        coroutineScope.launch {
                            database.albumWithSongs(album.id).firstOrNull()?.let { albumWithSongs ->
                                playerConnection.playQueue(LocalAlbumRadio(albumWithSongs))
                            }
                        }
                    },
                    trailingContent = {
                        IconButton(
                            onClick = {
                                menuState.show {
                                    AlbumMenu(
                                        originalAlbum = album,
                                        navController = navController,
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.more_vert),
                                contentDescription = null,
                            )
                        }
                    },
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .combinedClickable(
                            onClick = { navController.navigate("album/${album.id}") },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuState.show {
                                    AlbumMenu(
                                        originalAlbum = album,
                                        navController = navController,
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            },
                        )
                        .animateItem(),
                )
            }
        }

        if (sortedArtists.isNotEmpty()) {
            items(
                items = sortedArtists,
                key = { it.id },
                contentType = { "spotlight_artist" }
            ) { artist ->
                LibraryArtistSpotlightCard(
                    artist = artist,
                    trailingContent = {
                        IconButton(
                            onClick = {
                                menuState.show {
                                    ArtistMenu(
                                        originalArtist = artist,
                                        coroutineScope = coroutineScope,
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.more_vert),
                                contentDescription = null,
                            )
                        }
                    },
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .combinedClickable(
                            onClick = { navController.navigate("artist/${artist.id}") },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuState.show {
                                    ArtistMenu(
                                        originalArtist = artist,
                                        coroutineScope = coroutineScope,
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            },
                        )
                        .animateItem(),
                )
            }
        }
    }
}

@Composable
private fun LibraryControlCard(
    canEnterReorderMode: Boolean,
    reorderEnabled: Boolean,
    onToggleReorder: () -> Unit,
    controls: @Composable RowScope.() -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ArmoryColors.Silver5.copy(alpha = 0.5f)),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.padding(horizontal = 16.dp)
            .border(1.dp, ArmoryColors.Silver4.copy(alpha = 0.3f), MaterialTheme.shapes.large),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
        ) {
            Row(
                modifier = Modifier.weight(1f),
                content = controls,
            )
            if (canEnterReorderMode) {
                IconButton(
                    onClick = onToggleReorder,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        painter = painterResource(if (reorderEnabled) R.drawable.lock_open else R.drawable.lock),
                        contentDescription = null,
                    )
                }
            }
        }
    }
}
