/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

@file:Suppress("UNUSED_EXPRESSION")

package com.ruben.spotitube.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.ruben.spotitube.LocalPlayerAwareWindowInsets
import com.ruben.spotitube.LocalPlayerConnection
import com.ruben.spotitube.R
import com.ruben.spotitube.constants.ListItemHeight
import com.ruben.spotitube.extensions.togglePlayPause
import com.ruben.spotitube.innertube.models.AlbumItem
import com.ruben.spotitube.innertube.models.ArtistItem
import com.ruben.spotitube.innertube.models.PlaylistItem
import com.ruben.spotitube.innertube.models.SongItem
import com.ruben.spotitube.models.toMediaMetadata
import com.ruben.spotitube.playback.queues.YouTubeQueue
import com.ruben.spotitube.ui.component.EmptyPlaceholder
import com.ruben.spotitube.ui.component.LocalMenuState
import com.ruben.spotitube.SectionHeader
import com.ruben.spotitube.ArmoryTopBar
import com.ruben.spotitube.ArmoryIconButton
import com.ruben.spotitube.ui.component.YouTubeGridItem
import com.ruben.spotitube.ui.component.YouTubeListItem
import com.ruben.spotitube.ui.component.shimmer.GridItemPlaceHolder
import com.ruben.spotitube.ui.component.shimmer.ShimmerHost
import com.ruben.spotitube.ui.component.shimmer.TextPlaceholder
import com.ruben.spotitube.ui.menu.YouTubeAlbumMenu
import com.ruben.spotitube.ui.menu.YouTubeArtistMenu
import com.ruben.spotitube.ui.menu.YouTubePlaylistMenu
import com.ruben.spotitube.ui.menu.YouTubeSongMenu
import com.ruben.spotitube.ui.utils.SnapLayoutInfoProvider
import com.ruben.spotitube.viewmodels.YouTubeBrowseViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun YouTubeBrowseScreen(
    navController: NavController,
    viewModel: YouTubeBrowseViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val browseResult by viewModel.result.collectAsStateWithLifecycle()
    val isError by viewModel.error.collectAsStateWithLifecycle()

    val passedTitle = viewModel.passedTitle.orEmpty()
    val displayTitle = browseResult?.title.takeUnless { it.isNullOrBlank() } ?: passedTitle

    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ArmoryTopBar(
                title = displayTitle,
                onBack = navController::navigateUp
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
            ) {
                val horizontalLazyGridItemWidthFactor = if (maxWidth * 0.475f >= 320.dp) 0.475f else 0.9f
                val lazyGridState = rememberLazyGridState()
                val snapLayoutInfoProvider = remember(lazyGridState) {
                    SnapLayoutInfoProvider(
                        lazyGridState = lazyGridState,
                        positionInLayout = { layoutSize, itemSize ->
                            (layoutSize * horizontalLazyGridItemWidthFactor / 2f - itemSize / 2f)
                        }
                    )
                }

                val bottomPadding = PaddingValues(
                    bottom = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding() + 16.dp,
                )

                LazyColumn(
                    contentPadding = bottomPadding,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (browseResult == null && !isError) {
                        item {
                            ShimmerHost(
                                modifier = Modifier.animateItem()
                            ) {
                                TextPlaceholder(
                                    height = 36.dp,
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .width(250.dp),
                                )
                                LazyRow {
                                    items(4) {
                                        GridItemPlaceHolder()
                                    }
                                }
                            }
                        }
                    } else if (isError) {
                        item {
                            EmptyPlaceholder(
                                icon = R.drawable.error,
                                text = stringResource(R.string.error_loading)
                            )
                        }
                    }

                    browseResult?.items?.fastForEach {
                        if (it.items.isNotEmpty()) {
                            it.title?.let { title ->
                                item {
                                    SectionHeader(text = title)
                                }
                            }
                            if (it.items.all { item -> item is SongItem }) {
                                item {
                                    LazyHorizontalGrid(
                                        state = lazyGridState,
                                        rows = GridCells.Fixed(4),
                                        flingBehavior = rememberSnapFlingBehavior(snapLayoutInfoProvider),
                                        contentPadding = WindowInsets.systemBars
                                            .only(WindowInsetsSides.Horizontal)
                                            .asPaddingValues(),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(ListItemHeight * 4)
                                            .animateItem()
                                    ) {
                                        items(
                                            items = it.items,
                                        ) { song ->
                                            Box(Modifier.width(350.dp)) {
                                                YouTubeListItem(
                                                    item = song as SongItem,
                                                    isActive = mediaMetadata?.id == song.id,
                                                    isPlaying = isPlaying,
                                                    isSwipeable = false,
                                                    trailingContent = {
                                                        ArmoryIconButton(
                                                            painter = painterResource(R.drawable.more_vert),
                                                            contentDescription = null,
                                                            onClick = {
                                                                menuState.show {
                                                                    YouTubeSongMenu(
                                                                        song = song,
                                                                        navController = navController,
                                                                        onDismiss = menuState::dismiss,
                                                                    )
                                                                }
                                                            }
                                                        )
                                                    },
                                                    modifier =
                                                    Modifier
                                                        .clickable {
                                                            if (song.id == mediaMetadata?.id) {
                                                                playerConnection.player.togglePlayPause()
                                                            } else {
                                                                playerConnection.playQueue(
                                                                    YouTubeQueue.radio(
                                                                        song.toMediaMetadata()
                                                                    )
                                                                )
                                                            }
                                                        }
                                                        .animateItem()
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                item {
                                    LazyRow {
                                        items(
                                            items = it.items,
                                        ) { item ->
                                            YouTubeGridItem(
                                                item = item,
                                                isActive =
                                                when (item) {
                                                    is AlbumItem -> mediaMetadata?.album?.id == item.id
                                                    else -> false
                                                },
                                                isPlaying = isPlaying,
                                                coroutineScope = coroutineScope,
                                                modifier =
                                                Modifier
                                                    .combinedClickable(
                                                        onClick = {
                                                            when (item) {
                                                                is AlbumItem -> navController.navigate("album/${item.id}")
                                                                is ArtistItem -> navController.navigate("artist/${item.id}")
                                                                is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
                                                                else -> item
                                                            }
                                                        },
                                                        onLongClick = {
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                                                    .animateItem()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
