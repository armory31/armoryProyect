/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ruben.spotitube.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ruben.spotitube.ArmoryTopBar
import com.ruben.spotitube.LocalPlayerAwareWindowInsets
import com.ruben.spotitube.LocalPlayerConnection
import com.ruben.spotitube.R
import com.ruben.spotitube.SectionHeader
import com.ruben.spotitube.constants.ListItemHeight
import com.ruben.spotitube.extensions.togglePlayPause
import com.ruben.spotitube.innertube.models.AlbumItem
import com.ruben.spotitube.innertube.models.ArtistItem
import com.ruben.spotitube.innertube.models.PlaylistItem
import com.ruben.spotitube.innertube.models.SongItem
import com.ruben.spotitube.innertube.models.WatchEndpoint
import com.ruben.spotitube.innertube.pages.ChartsPage
import com.ruben.spotitube.models.toMediaMetadata
import com.ruben.spotitube.playback.queues.YouTubeQueue
import com.ruben.spotitube.ui.component.LocalMenuState
import com.ruben.spotitube.ui.component.YouTubeGridItem
import com.ruben.spotitube.ui.component.YouTubeListItem
import com.ruben.spotitube.ui.component.shimmer.GridItemPlaceHolder
import com.ruben.spotitube.ui.component.shimmer.ShimmerHost
import com.ruben.spotitube.ui.component.shimmer.TextPlaceholder
import com.ruben.spotitube.ui.menu.YouTubeAlbumMenu
import com.ruben.spotitube.ui.menu.YouTubeSongMenu
import com.ruben.spotitube.ui.utils.SnapLayoutInfoProvider
import com.ruben.spotitube.viewmodels.ChartsError
import com.ruben.spotitube.viewmodels.ChartsViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChartsScreen(
    navController: NavController,
    viewModel: ChartsViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val chartsPage by viewModel.chartsPage.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val chartsError by viewModel.error.collectAsStateWithLifecycle()

    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val layoutDirection = LocalLayoutDirection.current

    LaunchedEffect(Unit) {
        if (chartsPage == null) {
            viewModel.loadCharts()
        }
    }

    Scaffold(
        topBar = {
            ArmoryTopBar(
                title = stringResource(R.string.charts),
                onBack = navController::navigateUp
            )
        }
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            if (isLoading && chartsPage == null) {
                ShimmerHost(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        TextPlaceholder(
                            height = 36.dp,
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(0.5f),
                        )
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val horizontalLazyGridItemWidthFactor = if (maxWidth * 0.475f >= 320.dp) 0.475f else 0.9f
                            val horizontalLazyGridItemWidth = maxWidth * horizontalLazyGridItemWidthFactor

                            LazyHorizontalGrid(
                                rows = GridCells.Fixed(4),
                                contentPadding = PaddingValues(start = 4.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(ListItemHeight * 4),
                            ) {
                                items(4) {
                                    Row(
                                        modifier = Modifier
                                            .width(horizontalLazyGridItemWidth)
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(ListItemHeight - 16.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(MaterialTheme.colorScheme.onSurface),
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(
                                            modifier = Modifier.fillMaxHeight(),
                                            verticalArrangement = Arrangement.Center,
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .height(16.dp)
                                                    .width(120.dp)
                                                    .background(MaterialTheme.colorScheme.onSurface),
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Box(
                                                modifier = Modifier
                                                    .height(12.dp)
                                                    .width(80.dp)
                                                    .background(MaterialTheme.colorScheme.onSurface),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        TextPlaceholder(
                            height = 36.dp,
                            modifier = Modifier
                                .padding(vertical = 12.dp, horizontal = 12.dp)
                                .width(250.dp),
                        )
                        Row {
                            repeat(2) {
                                GridItemPlaceHolder()
                            }
                        }
                    }
                }
            } else if (chartsError != null && (chartsPage == null || chartsPage?.sections.orEmpty().isEmpty())) {
                val errorMsg = when (chartsError) {
                    ChartsError.NO_DATA -> stringResource(R.string.no_data_for_country)
                    else -> stringResource(R.string.charts_load_error)
                }
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = errorMsg,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (chartsError == ChartsError.NETWORK_ERROR) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadCharts() }) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
            } else if (chartsPage == null || chartsPage?.sections.orEmpty().isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_data_for_country),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = lazyListState,
                    contentPadding = LocalPlayerAwareWindowInsets.current
                        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                        .asPaddingValues(),
                ) {
                    chartsPage?.sections?.forEach { section ->
                        item {
                            SectionHeader(
                                text = when (section.title) {
                                    "ESPAÑA" -> stringResource(R.string.region_spain)
                                    "EUROPA" -> stringResource(R.string.region_europe)
                                    "LATINOAMÉRICA" -> stringResource(R.string.region_latin_america)
                                    "Trending" -> stringResource(R.string.trending)
                                    "New releases", "Álbumes y singles nuevos" -> stringResource(R.string.new_releases)
                                    else -> section.title ?: stringResource(R.string.charts)
                                },
                                modifier = Modifier.animateItem(),
                            )
                        }
                        item {
                            val systemBarsInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).asPaddingValues()
                            val rowContentPadding = PaddingValues(
                                start = 12.dp + systemBarsInsets.calculateStartPadding(layoutDirection),
                                end = 12.dp + systemBarsInsets.calculateEndPadding(layoutDirection)
                            )
                            val hasAlbums = section.chartType == ChartsPage.ChartType.NEW_RELEASES || section.items.any { it is AlbumItem || it is PlaylistItem || it is ArtistItem }
                            if (hasAlbums) {
                                LazyRow(
                                    contentPadding = rowContentPadding,
                                    modifier = Modifier.animateItem(),
                                ) {
                                    items(
                                        items = section.items.distinctBy { it.id },
                                        key = { it.id },
                                    ) { item ->
                                        YouTubeGridItem(
                                            item = item,
                                            isActive = (item as? SongItem)?.id == mediaMetadata?.id,
                                            isPlaying = isPlaying,
                                            coroutineScope = coroutineScope,
                                            modifier = Modifier
                                                .combinedClickable(
                                                    onClick = {
                                                        when (item) {
                                                            is SongItem -> {
                                                                if (item.id == mediaMetadata?.id) {
                                                                    playerConnection.player.togglePlayPause()
                                                                } else {
                                                                    playerConnection.playQueue(
                                                                        YouTubeQueue(
                                                                            endpoint = WatchEndpoint(videoId = item.id),
                                                                            preloadItem = item.toMediaMetadata(),
                                                                        ),
                                                                    )
                                                                }
                                                            }
                                                            is AlbumItem -> navController.navigate("album/${item.id}")
                                                            is ArtistItem -> navController.navigate("artist/${item.id}")
                                                            is PlaylistItem -> navController.navigate("online_playlist/${item.id.removePrefix("VL")}")
                                                        }
                                                    },
                                                    onLongClick = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        menuState.show {
                                                            when (item) {
                                                                is SongItem -> YouTubeSongMenu(song = item, navController = navController, onDismiss = menuState::dismiss)
                                                                is AlbumItem -> YouTubeAlbumMenu(albumItem = item, navController = navController, onDismiss = menuState::dismiss)
                                                                else -> {}
                                                            }
                                                        }
                                                    },
                                                )
                                                .animateItem(),
                                        )
                                    }
                                }
                            } else {
                                val songs = section.items.filterIsInstance<SongItem>().distinctBy { it.id }
                                if (songs.isNotEmpty()) {
                                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                        val horizontalLazyGridItemWidthFactor = if (maxWidth * 0.475f >= 320.dp) 0.475f else 0.9f
                                        val horizontalLazyGridItemWidth = maxWidth * horizontalLazyGridItemWidthFactor

                                        val lazyGridState = rememberLazyGridState()
                                        val snapLayoutInfoProvider = remember(lazyGridState) {
                                            SnapLayoutInfoProvider(
                                                lazyGridState = lazyGridState,
                                                positionInLayout = { layoutSize, itemSize ->
                                                    (layoutSize * horizontalLazyGridItemWidthFactor / 2f - itemSize / 2f)
                                                },
                                            )
                                        }

                                        val rowCount = minOf(4, songs.size)
                                        LazyHorizontalGrid(
                                            state = lazyGridState,
                                            rows = GridCells.Fixed(rowCount),
                                            flingBehavior = rememberSnapFlingBehavior(snapLayoutInfoProvider),
                                            contentPadding = rowContentPadding,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(ListItemHeight * rowCount)
                                                .animateItem(),
                                        ) {
                                            items(
                                                items = songs,
                                                key = { it.id },
                                            ) { song ->
                                                YouTubeListItem(
                                                    item = song,
                                                    isActive = song.id == mediaMetadata?.id,
                                                    isPlaying = isPlaying,
                                                    trailingContent = {
                                                        IconButton(
                                                            onClick = {
                                                                menuState.show {
                                                                    YouTubeSongMenu(
                                                                        song = song,
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
                                                        .width(horizontalLazyGridItemWidth)
                                                        .combinedClickable(
                                                            onClick = {
                                                                if (song.id == mediaMetadata?.id) {
                                                                    playerConnection.player.togglePlayPause()
                                                                } else {
                                                                    playerConnection.playQueue(
                                                                        YouTubeQueue(
                                                                            endpoint = WatchEndpoint(videoId = song.id),
                                                                            preloadItem = song.toMediaMetadata(),
                                                                        ),
                                                                    )
                                                                }
                                                            },
                                                            onLongClick = {
                                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                menuState.show {
                                                                    YouTubeSongMenu(
                                                                        song = song,
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
