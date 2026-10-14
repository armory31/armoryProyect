/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ruben.spotitube.ui.screens.artist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ruben.spotitube.LocalPlayerAwareWindowInsets
import com.ruben.spotitube.LocalPlayerConnection
import com.ruben.spotitube.ArmoryTopBar
import com.ruben.spotitube.ArmoryIconButton
import com.ruben.spotitube.ArmoryIcon
import com.ruben.spotitube.R
import com.ruben.spotitube.constants.GridThumbnailHeight
import com.ruben.spotitube.extensions.toMediaItem
import com.ruben.spotitube.extensions.togglePlayPause
import com.ruben.spotitube.innertube.models.AlbumItem
import com.ruben.spotitube.innertube.models.ArtistItem
import com.ruben.spotitube.innertube.models.PlaylistItem
import com.ruben.spotitube.innertube.models.SongItem
import com.ruben.spotitube.innertube.models.WatchEndpoint
import com.ruben.spotitube.models.toAlbum
import com.ruben.spotitube.models.toMediaMetadata
import com.ruben.spotitube.playback.queues.ListQueue
import com.ruben.spotitube.playback.queues.YouTubeQueue
import com.ruben.spotitube.ui.component.AlbumGridItem
import com.ruben.spotitube.ui.component.IconButton
import com.ruben.spotitube.ui.component.LocalMenuState
import com.ruben.spotitube.ui.component.YouTubeGridItem
import com.ruben.spotitube.ui.component.YouTubeListItem
import com.ruben.spotitube.ui.component.shimmer.GridItemPlaceHolder
import com.ruben.spotitube.ui.component.shimmer.ListItemPlaceHolder
import com.ruben.spotitube.ui.component.shimmer.ShimmerHost
import com.ruben.spotitube.ui.menu.YouTubeAlbumMenu
import com.ruben.spotitube.ui.menu.YouTubeArtistMenu
import com.ruben.spotitube.ui.menu.YouTubePlaylistMenu
import com.ruben.spotitube.ui.menu.YouTubeSongMenu
import com.ruben.spotitube.ui.utils.backToMain
import com.ruben.spotitube.viewmodels.ArtistItemsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistItemsScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: ArtistItemsViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val lazyListState = rememberLazyListState()
    val lazyGridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()

    val title by viewModel.title.collectAsStateWithLifecycle()
    val itemsPage by viewModel.itemsPage.collectAsStateWithLifecycle()

    val browseId = viewModel.browseId
    val passedTitle = viewModel.passedTitle.orEmpty()
    val displayTitle = when {
        title.isNotBlank() && title != "Detalles" -> title
        passedTitle.isNotBlank() && passedTitle != "Detalles" -> passedTitle
        browseId.startsWith("MPAD") || browseId.contains("SINGLE") -> "Singles y EPs"
        browseId.contains("ALBUM") -> "Álbumes"
        browseId.contains("PLAYLIST") -> "Listas de reproducción"
        else -> "Música"
    }

    LaunchedEffect(lazyListState) {
        snapshotFlow {
            lazyListState.layoutInfo.visibleItemsInfo.any { it.key == "loading" }
        }.collect { shouldLoadMore ->
            if (!shouldLoadMore) return@collect
            viewModel.loadMore()
        }
    }

    LaunchedEffect(lazyGridState) {
        snapshotFlow {
            lazyGridState.layoutInfo.visibleItemsInfo.any { it.key == "loading" }
        }.collect { shouldLoadMore ->
            if (!shouldLoadMore) return@collect
            viewModel.loadMore()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ArmoryTopBar(
                title = {
                    if (itemsPage != null) {
                        Column {
                            Text(
                                text = displayTitle,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            itemsPage?.items?.takeIf { it.isNotEmpty() }?.let { items ->
                                Text(
                                    text = "${items.size} elementos",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                },
                onBack = navController::navigateUp,
                scrollBehavior = scrollBehavior,
                actions = {
                    val songs = itemsPage?.items.orEmpty().filterIsInstance<SongItem>()
                    if (songs.isNotEmpty()) {
                        ArmoryIconButton(
                            painter = painterResource(R.drawable.play),
                            contentDescription = "Reproducir",
                            onClick = {
                                playerConnection.playQueue(
                                    ListQueue(
                                        title = title,
                                        items = songs.map { it.toMediaItem() },
                                    ),
                                )
                            }
                        )
                        ArmoryIconButton(
                            painter = painterResource(R.drawable.shuffle),
                            contentDescription = "Aleatorio",
                            onClick = {
                                playerConnection.playQueue(
                                    ListQueue(
                                        title = title,
                                        items = songs.shuffled().map { it.toMediaItem() },
                                    ),
                                )
                            }
                        )
                    }
                }
            )
        },
    ) { paddingValues ->
        val page = itemsPage
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            if (page == null) {
                ShimmerHost(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    repeat(8) {
                        ListItemPlaceHolder()
                    }
                }
            } else if (page.items.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Text(
                        text = "No hay elementos disponibles",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                val isListType = page.items.all { it is SongItem }
                val bottomPadding = PaddingValues(
                    bottom = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding() + 16.dp,
                )
                if (isListType) {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = bottomPadding,
                    ) {
                        itemsIndexed(
                            items = page.items.distinctBy { it.id.ifEmpty { it.title } },
                            key = { index, item -> item.id.ifEmpty { "${item.title}_$index" } },
                        ) { _, item ->
                            YouTubeListItem(
                                item = item,
                                isActive = when (item) {
                                    is SongItem -> mediaMetadata?.id == item.id
                                    is AlbumItem -> mediaMetadata?.album?.id == item.id
                                    else -> false
                                },
                                isPlaying = isPlaying,
                                trailingContent = {
                                    ArmoryIconButton(
                                        painter = painterResource(R.drawable.more_vert),
                                        contentDescription = null,
                                        onClick = {
                                            menuState.show {
                                                when (item) {
                                                    is SongItem ->
                                                        YouTubeSongMenu(
                                                            song = item,
                                                            navController = navController,
                                                            onDismiss = menuState::dismiss,
                                                        )

                                                    is AlbumItem ->
                                                        YouTubeAlbumMenu(
                                                            albumItem = item,
                                                            navController = navController,
                                                            onDismiss = menuState::dismiss,
                                                        )

                                                    is ArtistItem ->
                                                        YouTubeArtistMenu(
                                                            artist = item,
                                                            onDismiss = menuState::dismiss,
                                                        )

                                                    is PlaylistItem ->
                                                        YouTubePlaylistMenu(
                                                            playlist = item,
                                                            coroutineScope = coroutineScope,
                                                            onDismiss = menuState::dismiss,
                                                        )
                                                }
                                            }
                                        }
                                    )
                                },
                                modifier = Modifier.clickable {
                                    when (item) {
                                        is SongItem -> {
                                            if (item.id == mediaMetadata?.id) {
                                                playerConnection.player.togglePlayPause()
                                            } else {
                                                val songs = page.items.filterIsInstance<SongItem>()
                                                playerConnection.playQueue(
                                                    ListQueue(
                                                        title = title,
                                                        items = songs.map { it.toMediaItem() },
                                                        startIndex = songs.indexOfFirst { it.id == item.id }.coerceAtLeast(0),
                                                    ),
                                                )
                                            }
                                        }

                                        is AlbumItem -> navController.navigate("album/${item.id}")
                                        is ArtistItem -> navController.navigate("artist/${item.id}")
                                        is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
                                    }
                                },
                            )
                        }

                        if (page.continuation != null) {
                            item(key = "loading") {
                                ShimmerHost {
                                    repeat(3) {
                                        ListItemPlaceHolder()
                                    }
                                }
                            }
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        state = lazyGridState,
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = bottomPadding,
                    ) {
                        itemsIndexed(
                            items = page.items.distinctBy { it.id.ifEmpty { it.title } },
                            key = { index, item -> item.id.ifEmpty { "${item.title}_$index" } },
                        ) { _, item ->
                            if (item is AlbumItem) {
                                AlbumGridItem(
                                    album = item.toAlbum(),
                                    isActive = mediaMetadata?.album?.id == item.id,
                                    isPlaying = isPlaying,
                                    fillMaxWidth = true,
                                    coroutineScope = coroutineScope,
                                    modifier = Modifier
                                        .combinedClickable(
                                            onClick = { navController.navigate("album/${item.id}") },
                                            onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                menuState.show {
                                                    YouTubeAlbumMenu(
                                                        albumItem = item,
                                                        navController = navController,
                                                        onDismiss = menuState::dismiss,
                                                    )
                                                }
                                            },
                                        )
                                        .animateItem(),
                                )
                            } else {
                                YouTubeGridItem(
                                    item = item,
                                    isActive = when (item) {
                                        is SongItem -> mediaMetadata?.id == item.id
                                        else -> false
                                    },
                                    isPlaying = isPlaying,
                                    fillMaxWidth = true,
                                    coroutineScope = coroutineScope,
                                    modifier = Modifier
                                        .combinedClickable(
                                            onClick = {
                                                when (item) {
                                                    is SongItem -> playerConnection.playQueue(
                                                        YouTubeQueue(
                                                            item.endpoint ?: WatchEndpoint(videoId = item.id),
                                                            item.toMediaMetadata(),
                                                        ),
                                                    )

                                                    is ArtistItem -> navController.navigate("artist/${item.id}")
                                                    is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
                                                    else -> {}
                                                }
                                            },
                                            onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                menuState.show {
                                                    when (item) {
                                                        is SongItem -> YouTubeSongMenu(
                                                            song = item,
                                                            navController = navController,
                                                            onDismiss = menuState::dismiss,
                                                        )

                                                        is ArtistItem -> YouTubeArtistMenu(
                                                            artist = item,
                                                            onDismiss = menuState::dismiss,
                                                        )

                                                        is PlaylistItem -> YouTubePlaylistMenu(
                                                            playlist = item,
                                                            coroutineScope = coroutineScope,
                                                            onDismiss = menuState::dismiss,
                                                        )

                                                        else -> {}
                                                    }
                                                }
                                            },
                                        )
                                        .animateItem(),
                                )
                            }
                        }

                        if (page.continuation != null) {
                            item(key = "loading") {
                                ShimmerHost(Modifier.animateItem()) {
                                    GridItemPlaceHolder(fillMaxWidth = true)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
