/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ruben.spotitube.ui.screens

import android.annotation.SuppressLint
import android.net.Uri
import com.ruben.spotitube.extensions.navigateSafe
import com.ruben.spotitube.ArmoryColors
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalCenteredHeroCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.ruben.spotitube.ui.menu.PlaylistMenu
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.ruben.spotitube.R
import com.ruben.spotitube.constants.GridThumbnailHeight
import com.ruben.spotitube.constants.ListItemHeight
import com.ruben.spotitube.constants.ListThumbnailSize
import com.ruben.spotitube.constants.ThumbnailCornerRadius
import com.ruben.spotitube.db.entities.Album
import com.ruben.spotitube.db.entities.Artist
import com.ruben.spotitube.db.entities.LocalItem
import com.ruben.spotitube.db.entities.Playlist
import com.ruben.spotitube.db.entities.Song
import com.ruben.spotitube.extensions.toMediaItem
import com.ruben.spotitube.extensions.togglePlayPause
import com.ruben.spotitube.innertube.models.AlbumItem
import com.ruben.spotitube.innertube.models.ArtistItem
import com.ruben.spotitube.innertube.models.PlaylistItem
import com.ruben.spotitube.innertube.models.SongItem
import com.ruben.spotitube.innertube.models.WatchEndpoint
import com.ruben.spotitube.innertube.models.YTItem
import com.ruben.spotitube.models.ItemMetadata
import com.ruben.spotitube.models.MediaMetadata
import com.ruben.spotitube.models.SimilarRecommendation
import com.ruben.spotitube.models.toMediaMetadata
import com.ruben.spotitube.playback.PlayerConnection
import com.ruben.spotitube.playback.queues.ListQueue
import com.ruben.spotitube.playback.queues.YouTubeQueue
import com.ruben.spotitube.ui.component.AlbumGridItem
import com.ruben.spotitube.ui.component.ArtistGridItem
import com.ruben.spotitube.ui.component.MenuState
import com.ruben.spotitube.ui.component.NavigationTitle
import com.ruben.spotitube.ui.component.SongGridItem
import com.ruben.spotitube.ui.component.SongListItem
import com.ruben.spotitube.ui.component.YouTubeGridItem
import com.ruben.spotitube.ui.menu.AlbumMenu
import com.ruben.spotitube.ui.menu.ArtistMenu
import com.ruben.spotitube.ui.menu.SongMenu
import com.ruben.spotitube.ui.menu.YouTubeAlbumMenu
import com.ruben.spotitube.ui.menu.YouTubeArtistMenu
import com.ruben.spotitube.ui.menu.YouTubePlaylistMenu
import com.ruben.spotitube.ui.menu.YouTubeSongMenu
import com.ruben.spotitube.viewmodels.HomeViewModel
import kotlinx.coroutines.CoroutineScope
import kotlin.math.ceil
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickPicksSection(
    quickPicks: List<Song>,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    haptic: HapticFeedback,
    metadataMap: Map<String, ItemMetadata> = emptyMap(),
    modifier: Modifier = Modifier
) {
    val distinctQuickPicks = remember(quickPicks) { quickPicks.distinctBy { it.id } }

    HorizontalCenteredHeroCarousel(
        state = rememberCarouselState { distinctQuickPicks.size },
        maxItemWidth = 250.dp,
        itemSpacing = 8.dp,
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(290.dp)
    ) { index ->
        val song = distinctQuickPicks[index]
        val isActive = song.id == mediaMetadata?.id

        Box(
            modifier = Modifier
                .fillMaxSize()
                .maskClip(MaterialTheme.shapes.extraLarge)
                .maskBorder(
                    BorderStroke(1.dp, ArmoryColors.SurfaceBorder),
                    MaterialTheme.shapes.extraLarge
                )
                .combinedClickable(
                    onClick = {
                        if (isActive) {
                            playerConnection.player.togglePlayPause()
                        } else {
                            playerConnection.playQueue(YouTubeQueue.radio(song.toMediaMetadata()))
                        }
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuState.show {
                            SongMenu(
                                originalSong = song,
                                navController = navController,
                        metadata = metadataMap[song.id],
                                onDismiss = menuState::dismiss
                            )
                        }
                    }
                )
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(song.song.thumbnailUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.7f)
                            )
                        )
                    )
            )

            if (isActive && isPlaying) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .size(32.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.volume_up),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                Text(
                    text = song.song.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artists.joinToString { it.name },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun SpeedDialSection(
    speedDialItems: List<LocalItem>,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    haptic: HapticFeedback,
    metadataMap: Map<String, ItemMetadata> = emptyMap(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val distinctSpeedDial = remember(speedDialItems) { speedDialItems.distinctBy { it.id }.take(9) }
    val speedDialSongs = remember(distinctSpeedDial) { distinctSpeedDial.filterIsInstance<Song>() }
    val speedDialIndexById = remember(speedDialSongs) { speedDialSongs.mapIndexed { index, song -> song.id to index }.toMap() }
    val tileSize = 155.dp
    val spacing = 12.dp
    val state = rememberLazyGridState()
    val rowCount = if (distinctSpeedDial.size <= 2) 1 else 2
    val gridHeight = (tileSize * rowCount) + (spacing * (rowCount - 1))

    @SuppressLint("LocalContextGetResourceValueCall")
    fun playSpeedDialQueue(startIndex: Int) {
        if (speedDialSongs.isEmpty()) return
        playerConnection.playQueue(
            ListQueue(
                title = context.getString(R.string.speed_dial),
                items = speedDialSongs.map { it.toMediaItem() },
                startIndex = startIndex,
            )
        )
    }

    val dotState by
        remember(state, distinctSpeedDial.size) {
            derivedStateOf {
                val songsPerDot = 8
                val totalSongs = distinctSpeedDial.size
                if (totalSongs <= 0) {
                    Triple(0, 0, 0)
                } else {
                    val pages = ceil(totalSongs / songsPerDot.toFloat()).toInt().coerceAtLeast(1)
                    val visibleSongIndex =
                        state.firstVisibleItemIndex.coerceIn(0, (totalSongs - 1).coerceAtLeast(0))
                    val currentPage = (visibleSongIndex / songsPerDot).coerceIn(0, pages - 1)
                    val dots = min(3, pages)
                    val selectedDot =
                        if (pages <= 3) currentPage
                        else ((currentPage.toFloat() / (pages - 1).coerceAtLeast(1)) * (dots - 1))
                            .toInt()
                            .coerceIn(0, dots - 1)
                    Triple(dots, selectedDot, pages)
                }
            }
        }

    val (dotsCount, selectedDotIndex) = dotState.let { (dots, selected, _) -> dots to selected }

    Column(modifier = modifier.fillMaxWidth()) {
        LazyHorizontalGrid(
            state = state,
            rows = GridCells.Fixed(rowCount),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
            contentPadding = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).asPaddingValues(),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(gridHeight),
        ) {
            items(
                items = distinctSpeedDial,
                key = { item ->
                    when (item) {
                        is Song -> "song_${item.id}"
                        is Album -> "album_${item.id}"
                        is Playlist -> "playlist_${item.id}"
                        is Artist -> "artist_${item.id}"
                    }
                },
                contentType = { "speed_dial_item" }
            ) { item ->
                val isActive = item.id == mediaMetadata?.id

                Box(
                    modifier = Modifier
                        .width(tileSize)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .border(BorderStroke(1.dp, ArmoryColors.SurfaceBorder), RoundedCornerShape(16.dp))
                        .combinedClickable(
                            onClick = {
                                when (item) {
                                    is Song -> {
                                        if (isActive) {
                                            playerConnection.player.togglePlayPause()
                                        } else {
                                            val songIndex = speedDialIndexById[item.id] ?: 0
                                            playSpeedDialQueue(songIndex)
                                        }
                                    }
                                    is Album -> navController.navigateSafe("album/${Uri.encode(item.id)}")
                                    is Playlist -> {
                                        val browseId = item.playlist.browseId
                                        if (browseId != null && (!item.playlist.isEditable || item.songCount == 0)) {
                                            navController.navigateSafe("online_playlist/${Uri.encode(browseId)}")
                                        } else {
                                            navController.navigateSafe("local_playlist/${Uri.encode(item.id)}")
                                        }
                                    }
                                    is Artist -> navController.navigateSafe("artist/${Uri.encode(item.id)}")
                                }
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuState.show {
                                    when (item) {
                                        is Song -> SongMenu(
                                            originalSong = item,
                                            navController = navController,
                                            metadata = metadataMap[item.id],
                                            onDismiss = menuState::dismiss
                                        )
                                        is Album -> AlbumMenu(
                                            originalAlbum = item,
                                            navController = navController,
                                            onDismiss = menuState::dismiss
                                        )
                                        is Playlist -> PlaylistMenu(
                                            playlist = item,
                                            coroutineScope = scope,
                                            onDismiss = menuState::dismiss
                                        )
                                        is Artist -> ArtistMenu(
                                            originalArtist = item,
                                            coroutineScope = scope,
                                            onDismiss = menuState::dismiss
                                        )
                                    }
                                }
                            }
                        )
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(item.thumbnailUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.7f)
                                    )
                                )
                            )
                    )

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        val subtitleText = when (item) {
                            is Song -> item.artists.joinToString { it.name }
                            is Album -> item.artists.joinToString { it.name }
                            is Playlist -> {
                                val count = item.playlist.remoteSongCount.takeIf { item.songCount == 0 && it != null } ?: item.songCount
                                pluralStringResource(R.plurals.n_song, count, count)
                            }
                            is Artist -> pluralStringResource(R.plurals.n_song, item.songCount, item.songCount)
                        }

                        if (subtitleText.isNotEmpty()) {
                            Text(
                                text = subtitleText,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.75f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (isActive && isPlaying) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.volume_up),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(6.dp).size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        if (dotsCount > 1) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                repeat(dotsCount) { index ->
                    val isSelected = index == selectedDotIndex
                    Surface(
                        color =
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        shape = CircleShape,
                        modifier =
                            Modifier.size(
                                if (isSelected) 8.dp else 6.dp
                            ),
                    ) {}
                }
            }
        }
    }
}

/**
 * Keep Listening section - horizontal grid of local items
 */
@Composable
fun KeepListeningSection(
    keepListening: List<LocalItem>,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    haptic: HapticFeedback,
    scope: CoroutineScope,
    metadataMap: Map<String, ItemMetadata> = emptyMap(),
    modifier: Modifier = Modifier
) {
    val rows = if (keepListening.size > 6) 2 else 1
    val gridHeight = (GridThumbnailHeight + with(LocalDensity.current) {
        MaterialTheme.typography.bodyLarge.lineHeight.toDp() * 2 +
                MaterialTheme.typography.bodyMedium.lineHeight.toDp() * 2
    }) * rows

    LazyHorizontalGrid(
        state = rememberLazyGridState(),
        rows = GridCells.Fixed(rows),
        modifier = modifier
            .fillMaxWidth()
            .height(gridHeight)
    ) {
        items(
            items = keepListening,
            key = { item -> 
                when (item) {
                    is Song -> "song_${item.id}"
                    is Album -> "album_${item.id}"
                    is Artist -> "artist_${item.id}"
                    is Playlist -> "playlist_${item.id}"
                }
            },
            contentType = { "keep_listening_item" }
        ) { item ->
            val itemId = when (item) {
                is Song -> item.id
                is Album -> item.id
                is Artist -> item.id
                is Playlist -> item.id
            }
            LocalGridItem(
                item = item,
                mediaMetadata = mediaMetadata,
                isPlaying = isPlaying,
                navController = navController,
                playerConnection = playerConnection,
                menuState = menuState,
                haptic = haptic,
                scope = scope,
                metadata = metadataMap[itemId]
            )
        }
    }
}

/**
 * Forgotten Favorites section - horizontal grid of songs
 */
@Composable
fun ForgottenFavoritesSection(
    forgottenFavorites: List<Song>,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    horizontalLazyGridItemWidth: Dp,
    lazyGridState: LazyGridState,
    snapLayoutInfoProvider: SnapLayoutInfoProvider,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    haptic: HapticFeedback,
    metadataMap: Map<String, ItemMetadata> = emptyMap(),
    modifier: Modifier = Modifier
) {
    val rows = min(4, forgottenFavorites.size)
    val distinctForgottenFavorites = remember(forgottenFavorites) { forgottenFavorites.distinctBy { it.id } }
    
    LazyHorizontalGrid(
        state = lazyGridState,
        rows = GridCells.Fixed(rows),
        flingBehavior = rememberSnapFlingBehavior(snapLayoutInfoProvider),
        contentPadding = WindowInsets.systemBars
            .only(WindowInsetsSides.Horizontal)
            .asPaddingValues(),
        modifier = modifier
            .fillMaxWidth()
            .height(ListItemHeight * rows)
    ) {
        items(
            items = distinctForgottenFavorites,
            key = { it.id },
            contentType = { "song_item" }
        ) { song ->
            SongListItem(
                song = song,
                showInLibraryIcon = true,
                isActive = song.id == mediaMetadata?.id,
                isPlaying = isPlaying,
                isSwipeable = false,
                metadata = metadataMap[song.id],
                trailingContent = {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuState.show {
                                SongMenu(
                                    originalSong = song,
                                    navController = navController,
                                    metadata = metadataMap[song.id],
                                    onDismiss = menuState::dismiss
                                )
                            }
                        }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.more_vert),
                            contentDescription = null
                        )
                    }
                },
                modifier = Modifier
                    .width(horizontalLazyGridItemWidth)
                    .clip(RoundedCornerShape(12.dp))
                    .border(BorderStroke(1.dp, ArmoryColors.SurfaceBorder), RoundedCornerShape(12.dp))
                    .combinedClickable(
                        onClick = {
                            if (song.id == mediaMetadata?.id) {
                                playerConnection.player.togglePlayPause()
                            } else {
                                playerConnection.playQueue(YouTubeQueue.radio(song.toMediaMetadata()))
                            }
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuState.show {
                                SongMenu(
                                    originalSong = song,
                                    navController = navController,
                                    metadata = metadataMap[song.id],
                                    onDismiss = menuState::dismiss
                                )
                            }
                        }
                    )
            )
        }
    }
}

/**
 * Similar Recommendations section
 */
@Composable
fun SimilarRecommendationsSection(
    recommendation: SimilarRecommendation,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    haptic: HapticFeedback,
    scope: CoroutineScope,
    metadataMap: Map<String, ItemMetadata> = emptyMap(),
    modifier: Modifier = Modifier
) {
    LazyRow(
        contentPadding = WindowInsets.systemBars
            .only(WindowInsetsSides.Horizontal)
            .asPaddingValues(),
        modifier = modifier
    ) {
        items(
            items = recommendation.items.distinctBy { it.id }.take(4),
            key = { it.id },
            contentType = { "recommendation_card" }
        ) { item ->
            YouTubeGridItemWrapper(
                item = item,
                mediaMetadata = mediaMetadata,
                isPlaying = isPlaying,
                navController = navController,
                playerConnection = playerConnection,
                menuState = menuState,
                haptic = haptic,
                scope = scope,
                metadata = metadataMap[item.id]
            )
        }
    }
}

/**
 * Cuadrado estático gris para representar una canción vacía
 */
@Composable
fun StaticItemPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color.DarkGray.copy(alpha = 0.3f), RoundedCornerShape(ThumbnailCornerRadius))
    )
}

/**
 * Loading shimmer for home page sections
 */
@Composable
fun HomeLoadingShimmer(modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(5) {
                StaticItemPlaceholder(
                    modifier = Modifier
                        .size(120.dp)
                )
            }
        }
    }
}

// ============== Helper Composables ==============

/**
 * Wrapper for YouTube grid items with click handling
 */
@Composable
private fun YouTubeGridItemWrapper(
    item: YTItem,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    haptic: HapticFeedback,
    scope: CoroutineScope,
    metadata: ItemMetadata? = null,
    modifier: Modifier = Modifier
) {
    YouTubeGridItem(
        item = item,
        isActive = item.id in listOf(mediaMetadata?.album?.id, mediaMetadata?.id),
        isPlaying = isPlaying,
        coroutineScope = scope,
        thumbnailRatio = 1f,
        metadata = metadata,
        modifier = modifier.combinedClickable(
            onClick = {
                when (item) {
                    is SongItem -> playerConnection.playQueue(
                        YouTubeQueue(
                            item.endpoint ?: WatchEndpoint(videoId = item.id),
                            item.toMediaMetadata()
                        )
                    )
                    is AlbumItem -> navController.navigate("album/${item.id}")
                    is ArtistItem -> navController.navigate("artist/${item.id}")
                    is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
                }
            },
            onLongClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                menuState.show {
                    when (item) {
                        is SongItem -> YouTubeSongMenu(
                            song = item,
                            navController = navController,
                            onDismiss = menuState::dismiss
                        )
                        is AlbumItem -> YouTubeAlbumMenu(
                            albumItem = item,
                            navController = navController,
                            onDismiss = menuState::dismiss
                        )
                        is ArtistItem -> YouTubeArtistMenu(
                            artist = item,
                            onDismiss = menuState::dismiss
                        )
                        is PlaylistItem -> YouTubePlaylistMenu(
                            playlist = item,
                            coroutineScope = scope,
                            onDismiss = menuState::dismiss
                        )
                    }
                }
            }
        )
    )
}

/**
 * Local item grid item for songs, albums, artists
 */
@Composable
private fun LocalGridItem(
    item: LocalItem,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    haptic: HapticFeedback,
    scope: CoroutineScope,
    metadata: ItemMetadata? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(BorderStroke(1.dp, ArmoryColors.SurfaceBorder), RoundedCornerShape(16.dp))
    ) {
        when (item) {
            is Song -> SongGridItem(
                song = item,
                metadata = metadata,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {
                            if (item.id == mediaMetadata?.id) {
                                playerConnection.player.togglePlayPause()
                            } else {
                                playerConnection.playQueue(YouTubeQueue.radio(item.toMediaMetadata()))
                            }
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuState.show {
                                SongMenu(
                                    originalSong = item,
                                    navController = navController,
                                    metadata = metadata,
                                    onDismiss = menuState::dismiss
                                )
                            }
                        }
                    ),
                isActive = item.id == mediaMetadata?.id,
                isPlaying = isPlaying
            )

            is Album -> AlbumGridItem(
                album = item,
                isActive = item.id == mediaMetadata?.album?.id,
                isPlaying = isPlaying,
                coroutineScope = scope,
                metadata = metadata,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { navController.navigate("album/${item.id}") },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuState.show {
                                AlbumMenu(
                                    originalAlbum = item,
                                    navController = navController,
                                    onDismiss = menuState::dismiss
                                )
                            }
                        }
                    )
            )

            is Artist -> ArtistGridItem(
                artist = item,
                metadata = metadata,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { navController.navigate("artist/${item.id}") },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuState.show {
                                ArtistMenu(
                                    originalArtist = item,
                                    coroutineScope = scope,
                                    onDismiss = menuState::dismiss
                                )
                            }
                        }
                    )
            )

            is Playlist -> { /* Not displayed */ }
        }
    }
}

/**
 * Similar recommendations navigation title
 */
@Composable
fun SimilarRecommendationsTitle(
    recommendation: SimilarRecommendation,
    navController: NavController,
    modifier: Modifier = Modifier
) {
    NavigationTitle(
        title = recommendation.title.title,
        thumbnail = recommendation.title.thumbnailUrl?.let { thumbnailUrl ->
            {
                val shape = if (recommendation.title is Artist) CircleShape 
                    else RoundedCornerShape(ThumbnailCornerRadius)
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(ListThumbnailSize)
                        .clip(shape)
                )
            }
        },
        onClick = {
            when (recommendation.title) {
                is Song -> navController.navigate("album/${recommendation.title.album!!.id}")
                is Album -> navController.navigate("album/${recommendation.title.id}")
                is Artist -> navController.navigate("artist/${recommendation.title.id}")
                is Playlist -> {}
            }
        },
        modifier = modifier
    )
}

fun LazyListScope.similarRecommendationsContainer(
    viewModel: HomeViewModel,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    haptic: HapticFeedback,
    scope: CoroutineScope
) {
     item(
         key = "similar_recommendations",
         contentType = "section"
     ) {
        val similarRecommendations by viewModel.similarRecommendations.collectAsStateWithLifecycle()
        val allItemsMetadata by viewModel.allItemsMetadata.collectAsStateWithLifecycle()
        
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (similarRecommendations == null) {
                NavigationTitle(
                    title = stringResource(R.string.similar_to),
                    modifier = Modifier.padding(top = 24.dp)
                )
                HomeLoadingShimmer()
            } else {
                NavigationTitle(
                    title = stringResource(R.string.similar_to),
                    modifier = Modifier.padding(top = 24.dp)
                )

                similarRecommendations?.take(2)?.forEach { recommendation ->
                    SimilarRecommendationsTitle(
                        recommendation = recommendation,
                        navController = navController,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    SimilarRecommendationsSection(
                        recommendation = recommendation,
                        mediaMetadata = mediaMetadata,
                        isPlaying = isPlaying,
                        navController = navController,
                        playerConnection = playerConnection,
                        menuState = menuState,
                        haptic = haptic,
                        scope = scope,
                        metadataMap = allItemsMetadata,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }
        }
    }
}
