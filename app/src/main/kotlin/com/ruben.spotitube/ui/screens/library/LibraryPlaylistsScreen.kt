/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ruben.spotitube.ui.screens.library

import com.ruben.spotitube.ArmoryGradients

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.ruben.spotitube.ArmoryColors
import com.ruben.spotitube.ArmoryFilterChip
import com.ruben.spotitube.LocalDatabase
import com.ruben.spotitube.LocalPlayerAwareWindowInsets
import com.ruben.spotitube.R
import com.ruben.spotitube.constants.PlaylistSortDescendingKey
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
import com.ruben.spotitube.LocalPlayerConnection
import com.ruben.spotitube.constants.AlbumViewTypeKey
import com.ruben.spotitube.constants.ArtistViewTypeKey
import com.ruben.spotitube.constants.LibraryFilter
import com.ruben.spotitube.constants.LibraryViewType
import com.ruben.spotitube.ui.component.LibraryAlbumListItem
import com.ruben.spotitube.ui.component.LibraryArtistListItem
import com.ruben.spotitube.ui.component.LibraryPlaylistListItem
import com.ruben.spotitube.ui.component.LocalMenuState
import com.ruben.spotitube.utils.rememberEnumPreference
import com.ruben.spotitube.utils.rememberPreference
import com.ruben.spotitube.viewmodels.LibraryAlbumsViewModel
import com.ruben.spotitube.viewmodels.LibraryArtistsViewModel
import com.ruben.spotitube.viewmodels.LibraryPlaylistsViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState



@Composable
fun LibraryPlaylistsScreen(
    navController: NavController,
    headerContent: @Composable () -> Unit,
    filterType: LibraryFilter = LibraryFilter.PLAYLISTS,
    onFilterTypeChange: (LibraryFilter) -> Unit = {},
    viewModel: LibraryPlaylistsViewModel = hiltViewModel(),
    albumsViewModel: LibraryAlbumsViewModel = hiltViewModel(),
    artistsViewModel: LibraryArtistsViewModel = hiltViewModel(),
    initialTextFieldValue: String? = null,
    allowSyncing: Boolean = true,
) {
    val menuState = LocalMenuState.current
    val coroutineScope = rememberCoroutineScope()
    val database = LocalDatabase.current

    val playerConnection = LocalPlayerConnection.current
    val isPlaying by playerConnection?.isPlaying?.collectAsStateWithLifecycle(initialValue = false) ?: remember { mutableStateOf(false) }
    val mediaMetadata by playerConnection?.mediaMetadata?.collectAsStateWithLifecycle(initialValue = null) ?: remember { mutableStateOf(null) }

    var albumViewType by rememberEnumPreference(AlbumViewTypeKey, LibraryViewType.LIST)
    var artistViewType by rememberEnumPreference(ArtistViewTypeKey, LibraryViewType.LIST)

    val albums by albumsViewModel.allAlbums.collectAsStateWithLifecycle()
    val artists by artistsViewModel.allArtists.collectAsStateWithLifecycle()

    val (sortType, onSortTypeChange) = rememberEnumPreference(
        PlaylistSortTypeKey,
        PlaylistSortType.CUSTOM,
    )
    val (sortDescending, onSortDescendingChange) = rememberPreference(
        PlaylistSortDescendingKey,
        true,
    )
    val (selectedTagsFilter) = rememberPreference(PlaylistTagsFilterKey, "")
    val selectedTagIds = remember(selectedTagsFilter) {
        selectedTagsFilter.split(",").filter { it.isNotBlank() }.toSet()
    }
    val filteredPlaylistIds by database.playlistIdsByTags(
        if (selectedTagIds.isEmpty()) emptyList() else selectedTagIds.toList(),
    ).collectAsStateWithLifecycle(initialValue = emptyList())

    val playlists by viewModel.allPlaylists.collectAsStateWithLifecycle()
    val visiblePlaylists = remember(playlists, selectedTagIds, filteredPlaylistIds) {
        playlists.filter { playlist ->
            val name = playlist.playlist.name
            val matchesName = !name.contains("episode", ignoreCase = true)
            val matchesTags = selectedTagIds.isEmpty() || playlist.id in filteredPlaylistIds
            matchesName && matchesTags
        }
    }

    val topSize by viewModel.topValue.collectAsStateWithLifecycle(initialValue = "50")
    val likedTitle = stringResource(R.string.liked)
    val downloadedTitle = stringResource(R.string.offline)
    val cachedTitle = stringResource(R.string.cached_playlist)
    val topTitle = stringResource(R.string.my_top) + " $topSize"

    val likedPlaylist = remember(likedTitle) {
        Playlist(
            playlist = PlaylistEntity(id = "AUTO_LIKED_PLAYLISTS", name = likedTitle, isEditable = false),
            songCount = 0,
            songThumbnails = emptyList(),
        )
    }
    val downloadPlaylist = remember(downloadedTitle) {
        Playlist(
            playlist = PlaylistEntity(id = "AUTO_DOWNLOADED_PLAYLISTS", name = downloadedTitle, isEditable = false),
            songCount = 0,
            songThumbnails = emptyList(),
        )
    }
    val topPlaylist = remember(topTitle) {
        Playlist(
            playlist = PlaylistEntity(id = "AUTO_TOP_PLAYLISTS", name = topTitle, isEditable = false),
            songCount = 0,
            songThumbnails = emptyList(),
        )
    }
    val cachePlaylist = remember(cachedTitle) {
        Playlist(
            playlist = PlaylistEntity(id = "AUTO_CACHED_PLAYLISTS", name = cachedTitle, isEditable = false),
            songCount = 0,
            songThumbnails = emptyList(),
        )
    }

    val (showLiked) = rememberPreference(ShowLikedPlaylistKey, true)
    val (showDownloaded) = rememberPreference(ShowDownloadedPlaylistKey, true)
    val (showTop) = rememberPreference(ShowTopPlaylistKey, true)
    val (showCached) = rememberPreference(ShowCachedPlaylistKey, true)
    val (ytmSync) = rememberPreference(YtmSyncKey, true)

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
    val canEnterReorderMode = sortType == PlaylistSortType.CUSTOM && selectedTagIds.isEmpty()
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
    val scrollToTop = backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsStateWithLifecycle()

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            lazyListState.animateScrollToItem(0)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }



    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val pullRefreshState = rememberPullToRefreshState()
    val summary = pluralStringResource(R.plurals.n_playlist, visiblePlaylists.size, visiblePlaylists.size)

    var showCreatePlaylistDialog by rememberSaveable { mutableStateOf(false) }

    if (showCreatePlaylistDialog) {
        com.ruben.spotitube.ui.component.CreatePlaylistDialog(
            onDismiss = { showCreatePlaylistDialog = false }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullToRefresh(
                state = pullRefreshState,
                isRefreshing = isRefreshing,
                onRefresh = {
                    if (ytmSync && allowSyncing) {
                        viewModel.sync()
                    }
                },
            ),
    ) {
        LazyColumn(
            state = lazyListState,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 120.dp),
        ) {
            item(key = "header") {
                headerContent()
            }

            item(key = "filter_chips") {
                LibraryFilterChipsRow(
                    selectedFilter = filterType,
                    onFilterSelected = onFilterTypeChange,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            if (shortcuts.isNotEmpty()) {
                item(key = "shortcuts") {
                    LibraryShortcutsRow(
                        shortcuts = shortcuts,
                        onClick = { navController.navigate(it) },
                    )
                }
            }

            item(key = "playlist_section_header") {
                val headerTitle = when (filterType) {
                    LibraryFilter.PLAYLISTS -> stringResource(R.string.playlists)
                    LibraryFilter.ALBUMS -> stringResource(R.string.albums)
                    LibraryFilter.ARTISTS -> stringResource(R.string.artists)
                    else -> stringResource(R.string.playlists)
                }

                val headerSummary = when (filterType) {
                    LibraryFilter.PLAYLISTS -> summary
                    LibraryFilter.ALBUMS -> pluralStringResource(R.plurals.n_album, albums.size, albums.size)
                    LibraryFilter.ARTISTS -> pluralStringResource(R.plurals.n_artist, artists.size, artists.size)
                    else -> summary
                }

                val btn1Icon = when (filterType) {
                    LibraryFilter.PLAYLISTS -> R.drawable.add
                    LibraryFilter.ALBUMS -> if (albumViewType == LibraryViewType.LIST) R.drawable.grid_view else R.drawable.list
                    LibraryFilter.ARTISTS -> if (artistViewType == LibraryViewType.LIST) R.drawable.grid_view else R.drawable.list
                    else -> R.drawable.add
                }

                val btn1Click = {
                    when (filterType) {
                        LibraryFilter.PLAYLISTS -> showCreatePlaylistDialog = true
                        LibraryFilter.ALBUMS -> albumViewType = albumViewType.toggle()
                        LibraryFilter.ARTISTS -> artistViewType = artistViewType.toggle()
                        else -> showCreatePlaylistDialog = true
                    }
                }

                val btn2Icon = if (reorderEnabled) R.drawable.lock_open else R.drawable.lock
                val btn2Click = {
                    reorderEnabled = !reorderEnabled
                }

                PlaylistHeaderSplitRow(
                    title = headerTitle,
                    summary = headerSummary,
                    button1IconRes = btn1Icon,
                    onButton1Click = btn1Click,
                    button2IconRes = btn2Icon,
                    onButton2Click = btn2Click,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            when (filterType) {
                LibraryFilter.PLAYLISTS -> {
                    if (canReorderPlaylists) {
                        itemsIndexed(
                            items = mutableVisiblePlaylists,
                            key = { _, item -> item.id },
                        ) { _, playlist ->
                            ReorderableItem(
                                state = reorderableState,
                                key = playlist.id,
                            ) {
                                LibraryPlaylistListItem(
                                    navController = navController,
                                    menuState = menuState,
                                    coroutineScope = coroutineScope,
                                    playlist = playlist,
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
                        ) { playlist ->
                            LibraryPlaylistListItem(
                                navController = navController,
                                menuState = menuState,
                                coroutineScope = coroutineScope,
                                playlist = playlist,
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .animateItem(),
                            )
                        }
                    }
                }
                LibraryFilter.ALBUMS -> {
                    items(
                        items = albums.distinctBy { it.id },
                        key = { "album_${it.id}" }
                    ) { album ->
                        LibraryAlbumListItem(
                            navController = navController,
                            menuState = menuState,
                            album = album,
                            isActive = album.id == mediaMetadata?.album?.id,
                            isPlaying = isPlaying ?: false,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .animateItem()
                        )
                    }
                }
                LibraryFilter.ARTISTS -> {
                    items(
                        items = artists,
                        key = { "artist_${it.id}" }
                    ) { artist ->
                        LibraryArtistListItem(
                            navController = navController,
                            menuState = menuState,
                            coroutineScope = coroutineScope,
                            artist = artist,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .animateItem()
                        )
                    }
                }
                else -> {}
            }
        }

        PullToRefreshDefaults.Indicator(
            isRefreshing = isRefreshing,
            state = pullRefreshState,
            containerColor = ArmoryColors.Silver5,
            color = ArmoryColors.Gold3,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp),
        )
    }
}

@Composable
private fun LibraryFilterChipsRow(
    selectedFilter: LibraryFilter,
    onFilterSelected: (LibraryFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            LibraryFilter.PLAYLISTS to "Playlists",
            LibraryFilter.ALBUMS to "Álbumes",
            LibraryFilter.ARTISTS to "Artistas",
        ).forEach { (filter, label) ->
            ArmoryFilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterSelected(filter) },
                label = { Text(label, style = MaterialTheme.typography.labelLarge) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}



@Composable
private fun PlaylistHeaderSplitRow(
    title: String,
    summary: String,
    button1IconRes: Int? = R.drawable.add,
    onButton1Click: () -> Unit = {},
    button2IconRes: Int? = R.drawable.lock,
    onButton2Click: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        // 3/5 - Bloque Plata con Título y contador
        Box(
            modifier = Modifier
                .weight(3f)
                .height(52.dp)
                .background(
                    brush = ArmoryGradients.MetallicSilver,
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(title.uppercase(), style = MaterialTheme.typography.titleMedium.copy(color = ArmoryColors.AbsoluteBlack, letterSpacing = 1.sp))
                Text(summary, style = MaterialTheme.typography.labelMedium.copy(color = ArmoryColors.AbsoluteBlack))
            }
        }

        // 1/5 - Bloque Bronce con Botón 1
        Box(
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .background(
                    brush = ArmoryGradients.MetallicCopper,
                    shape = RoundedCornerShape(16.dp)
                )
                .combinedClickable(onClick = onButton1Click),
            contentAlignment = Alignment.Center
        ) {
            if (button1IconRes != null) {
                Icon(
                    painter = painterResource(button1IconRes),
                    contentDescription = null,
                    tint = ArmoryColors.AbsoluteBlack,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // 1/5 - Bloque Bronce con Botón 2
        Box(
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .background(
                    brush = ArmoryGradients.MetallicCopper,
                    shape = RoundedCornerShape(16.dp)
                )
                .combinedClickable(onClick = onButton2Click),
            contentAlignment = Alignment.Center
        ) {
            if (button2IconRes != null) {
                Icon(
                    painter = painterResource(button2IconRes),
                    contentDescription = null,
                    tint = ArmoryColors.AbsoluteBlack,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}