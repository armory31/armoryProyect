/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ruben.spotitube.ui.component

import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.zIndex
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.Download.STATE_COMPLETED
import androidx.media3.exoplayer.offline.Download.STATE_DOWNLOADING
import androidx.media3.exoplayer.offline.Download.STATE_QUEUED
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.ruben.spotitube.ArmoryColors
import com.ruben.spotitube.LocalDatabase
import com.ruben.spotitube.LocalDownloadUtil
import com.ruben.spotitube.LocalLibraryStateHolder
import com.ruben.spotitube.LocalPlayerConnection
import com.ruben.spotitube.R
import com.ruben.spotitube.constants.CropThumbnailToSquareKey
import com.ruben.spotitube.constants.DisableBlurKey
import com.ruben.spotitube.constants.GridThumbnailCornerRadius
import com.ruben.spotitube.constants.GridThumbnailHeight
import com.ruben.spotitube.constants.ListItemHeight
import com.ruben.spotitube.constants.ListThumbnailSize
import com.ruben.spotitube.constants.SwipeToSongKey
import com.ruben.spotitube.constants.ThumbnailCornerRadius
import com.ruben.spotitube.db.entities.Album
import com.ruben.spotitube.db.entities.Artist
import com.ruben.spotitube.db.entities.Playlist
import com.ruben.spotitube.db.entities.Song
import com.ruben.spotitube.extensions.toMediaItem
import com.ruben.spotitube.innertube.YouTube
import com.ruben.spotitube.innertube.models.AlbumItem
import com.ruben.spotitube.innertube.models.ArtistItem
import com.ruben.spotitube.innertube.models.PlaylistItem
import com.ruben.spotitube.innertube.models.SongItem
import com.ruben.spotitube.innertube.models.YTItem
import com.ruben.spotitube.models.ItemMetadata
import com.ruben.spotitube.models.MediaMetadata
import com.ruben.spotitube.playback.queues.LocalAlbumRadio
import com.ruben.spotitube.ui.utils.resize
import com.ruben.spotitube.utils.joinByBullet
import com.ruben.spotitube.utils.makeTimeString
import com.ruben.spotitube.utils.rememberPreference
import com.ruben.spotitube.utils.reportException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import androidx.compose.runtime.CompositionLocalProvider

const val ActiveBoxAlpha = 0.6f

@Composable
inline fun ListItem(
    modifier: Modifier = Modifier,
    title: String,
    noinline subtitle: (@Composable RowScope.() -> Unit)? = null,
    crossinline badges: @Composable RowScope.() -> Unit = {},
    thumbnailContent: @Composable () -> Unit,
    crossinline trailingContent: @Composable RowScope.() -> Unit = {},
    isActive: Boolean = false
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(ListItemHeight)
            .padding(horizontal = 8.dp)
            .then(if (isActive) Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.secondaryContainer) else Modifier)
    ) {
        Box(Modifier.padding(6.dp), contentAlignment = Alignment.Center) { thumbnailContent() }
        Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                badges()
                subtitle?.invoke(this)
            }
        }
        CompositionLocalProvider(LocalContentColor provides ArmoryColors.TextSecondary) { trailingContent() }
    }
}

@Composable
fun ListItem(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String?,
    badges: @Composable RowScope.() -> Unit = {},
    thumbnailContent: @Composable () -> Unit,
    trailingContent: @Composable RowScope.() -> Unit = {},
    isActive: Boolean = false
) = ListItem(
    title = title,
    modifier = modifier,
    isActive = isActive,
    subtitle = {
        badges()
        if (!subtitle.isNullOrEmpty()) {
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    },
    thumbnailContent = thumbnailContent,
    trailingContent = trailingContent
)

@Composable
fun GridItem(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    subtitle: @Composable () -> Unit,
    badges: @Composable RowScope.() -> Unit = {},
    thumbnailContent: @Composable BoxWithConstraintsScope.() -> Unit,
    thumbnailRatio: Float = 1f,
    fillMaxWidth: Boolean = false,
) {
    Column(
        modifier = if (fillMaxWidth) {
            modifier
                .padding(12.dp)
                .fillMaxWidth()
        } else {
            modifier
                .padding(12.dp)
                .width(GridThumbnailHeight * thumbnailRatio)
        }
    ) {
        BoxWithConstraints(
            contentAlignment = Alignment.Center,
            modifier = if (fillMaxWidth) {
                Modifier.fillMaxWidth()
            } else {
                Modifier.height(GridThumbnailHeight)
            }
                .aspectRatio(thumbnailRatio)
        ) {
            thumbnailContent()
        }

        Spacer(modifier = Modifier.height(6.dp))

        title()

        Row(verticalAlignment = Alignment.CenterVertically) {
            badges()

            subtitle()
        }
    }
}

@Composable
private fun playlistCountText(
    playlist: Playlist,
    autoPlaylist: Boolean,
): String =
    if (autoPlaylist) {
        ""
    } else if (playlist.songCount == 0 && playlist.playlist.remoteSongCount != null) {
        pluralStringResource(
            R.plurals.n_song,
            playlist.playlist.remoteSongCount,
            playlist.playlist.remoteSongCount,
        )
    } else {
        pluralStringResource(
            R.plurals.n_song,
            playlist.songCount,
            playlist.songCount,
        )
    }

@Composable
private fun playlistPlaceholderIcon(
    playlist: Playlist,
    autoPlaylist: Boolean,
): Int =
    when (playlist.playlist.name) {
        stringResource(R.string.liked) -> R.drawable.favorite_border
        stringResource(R.string.offline) -> R.drawable.offline
        stringResource(R.string.cached_playlist) -> R.drawable.cached
        else -> if (autoPlaylist) R.drawable.trending_up else R.drawable.queue_music
    }

@Composable
fun LibraryPlaylistFeatureCard(
    playlist: Playlist,
    modifier: Modifier = Modifier,
    autoPlaylist: Boolean = false,
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    val subtitleText = playlistCountText(playlist = playlist, autoPlaylist = autoPlaylist)
    val thumbnailSize = 80.dp
    val thumbnailShape = RoundedCornerShape(12.dp)
    Card(
        colors = CardDefaults.cardColors(
            containerColor = ArmoryColors.Silver5.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.border(
            width = 0.5.dp,
            color = ArmoryColors.Silver4.copy(alpha = 0.2f),
            shape = RoundedCornerShape(16.dp)
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            PlaylistThumbnail(
                thumbnails = playlist.thumbnails,
                size = thumbnailSize,
                placeHolder = {
                    Icon(
                        painter = painterResource(playlistPlaceholderIcon(playlist, autoPlaylist)),
                        contentDescription = null,
                        tint = ArmoryColors.Gold2,
                        modifier = Modifier.size(thumbnailSize / 2.5f),
                    )
                },
                shape = thumbnailShape,
            )
            Spacer(Modifier.width(16.dp))
            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = playlist.playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.padding(start = 8.dp),
            ) {
                trailingContent()
            }
        }
    }
}


@Composable
fun LibraryAlbumSpotlightCard(
    album: Album,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    onPlay: (() -> Unit)? = null,
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    val subtitle = joinByBullet(
        album.artists.joinToString { it.name },
        pluralStringResource(R.plurals.n_song, album.album.songCount, album.album.songCount),
    )

    val containerColor by animateColorAsState(
        if (isActive)
            MaterialTheme.colorScheme.secondaryContainer
        else
            MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = spring()
    )

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Box(modifier = Modifier.size(88.dp)) {
                LocalThumbnail(
                    thumbnailUrl = album.album.thumbnailUrl,
                    isActive = isActive,
                    isPlaying = isPlaying,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxSize(),
                )
                if (onPlay != null) {
                    AlbumPlayButton(
                        visible = !isActive,
                        onClick = onPlay,
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = album.album.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row {
                trailingContent()
            }
        }
    }
}


@Composable
fun LibraryArtistSpotlightCard(
    artist: Artist,
    modifier: Modifier = Modifier,
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            LocalThumbnail(
                thumbnailUrl = artist.artist.thumbnailUrl,
                isActive = false,
                isPlaying = false,
                shape = CircleShape,
                modifier = Modifier.size(80.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = artist.artist.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = pluralStringResource(R.plurals.n_song, artist.songCount, artist.songCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row {
                trailingContent()
            }
        }
    }
}


@Composable
fun LibraryPinnedCollectionTile(
    title: String,
    @DrawableRes iconRes: Int,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    accentColor: Color = ArmoryColors.Gold3,
) {
    val animatedColor by animateColorAsState(accentColor, spring())

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = ArmoryColors.AbsoluteBlack
        ),
        border = BorderStroke(
            1.dp,
            Brush.verticalGradient(
                listOf(
                    animatedColor.copy(alpha = 0.5f),
                    animatedColor.copy(alpha = 0.15f)
                )
            )
        ),
        modifier = modifier
            .width(125.dp)
            .height(120.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            animatedColor.copy(alpha = 0.18f),
                            Color.Transparent
                        )
                    )
                )
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(animatedColor.copy(alpha = 0.18f), CircleShape)
                        .border(1.dp, animatedColor.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        tint = animatedColor,
                        modifier = Modifier.size(24.dp),
                    )
                }

                Spacer(Modifier.height(10.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun GridItem(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    badges: @Composable RowScope.() -> Unit = {},
    thumbnailContent: @Composable BoxWithConstraintsScope.() -> Unit,
    thumbnailRatio: Float = 1f,
    fillMaxWidth: Boolean = false,
) = GridItem(
    modifier = modifier,
    title = {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )
    },
    subtitle = {
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    },
    thumbnailContent = thumbnailContent,
    thumbnailRatio = thumbnailRatio,
    fillMaxWidth = fillMaxWidth
)

@Composable
fun SongListItem(
    song: Song,
    modifier: Modifier = Modifier,
    albumIndex: Int? = null,
    viewCountText: String? = null,
    metadata: ItemMetadata? = null,
    showSavedIcon: Boolean = true,
    showInLibraryIcon: Boolean = false,
    showDownloadIcon: Boolean = true,
    badges: @Composable RowScope.() -> Unit = {
        val libraryStateHolder = LocalLibraryStateHolder.current
        val isSaved by libraryStateHolder.isSavedSong(song.id).collectAsStateWithLifecycle(initialValue = false)
        if (showSavedIcon && isSaved) Icon.Favorite()

        if (song.song.explicit) {
            Icon.Explicit()
        }

        if (showDownloadIcon) {
            val downloadState = metadata?.downloadState ?: run {
                val download by LocalDownloadUtil.current.getDownload(song.id)
                    .collectAsStateWithLifecycle(initialValue = null)
                download?.state
            }
            Icon.Download(downloadState)
        }
    },
    isSelected: Boolean = false,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    isSwipeable: Boolean = true,
    swipeContentBackgroundColor: Color? = null,
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    val swipeEnabled by rememberPreference(SwipeToSongKey, defaultValue = false)
    val resolvedSwipeContentBackgroundColor = swipeContentBackgroundColor ?: MaterialTheme.colorScheme.surface

    val content: @Composable () -> Unit = {
        ListItem(
            title = song.song.title,
            subtitle = joinByBullet(
                song.artists.joinToString { it.name },
                makeTimeString(song.song.duration * 1000L),
                viewCountText
            ),
            badges = badges,
            thumbnailContent = {
                ItemThumbnail(
                    thumbnailUrl = song.song.thumbnailUrl?.resize(200, 200),
                    albumIndex = albumIndex,
                    isSelected = isSelected,
                    isActive = isActive,
                    isPlaying = isPlaying,
                    shape = RoundedCornerShape(ThumbnailCornerRadius),
                    modifier = Modifier.size(ListThumbnailSize)
                )
            },
            trailingContent = trailingContent,
            modifier = modifier,
            isActive = isActive
        )
    }

    if (isSwipeable && swipeEnabled) {
        SwipeToSongBox(
            mediaItem = song.toMediaItem(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            content()
        }
    } else {
        content()
    }
}

@Composable
fun SongGridItem(
    song: Song,
    modifier: Modifier = Modifier,
    metadata: ItemMetadata? = null,
    showSavedIcon: Boolean = true,
    showInLibraryIcon: Boolean = false,
    showDownloadIcon: Boolean = true,
    badges: @Composable RowScope.() -> Unit = {
        val libraryStateHolder = LocalLibraryStateHolder.current
        val isSaved by libraryStateHolder.isSavedSong(song.id).collectAsStateWithLifecycle(initialValue = false)
        if (showSavedIcon && isSaved) {
            Icon.Favorite()
        }
        if (showDownloadIcon) {
            val downloadState = metadata?.downloadState ?: run {
                val download by LocalDownloadUtil.current.getDownload(song.id).collectAsStateWithLifecycle(initialValue = null)
                download?.state
            }
            Icon.Download(downloadState)
        }
    },
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    fillMaxWidth: Boolean = false,
) = GridItem(
    title = {
        Text(
            text = song.song.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.basicMarquee().fillMaxWidth()
        )
    },
    subtitle = {
        Text(
            text = joinByBullet(
                song.artists.joinToString { it.name },
                makeTimeString(song.song.duration * 1000L)
            ),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    },
    badges = badges,
    thumbnailContent = {
        ItemThumbnail(
            thumbnailUrl = song.song.thumbnailUrl,
            isActive = isActive,
            isPlaying = isPlaying,
            shape = RoundedCornerShape(GridThumbnailCornerRadius),
            modifier = Modifier.size(GridThumbnailHeight)
        )
        if (!isActive) {
            OverlayPlayButton(
                visible = true
            )
        }
    },
    fillMaxWidth = fillMaxWidth,
    modifier = modifier
)

@Composable
fun ArtistListItem(
    artist: Artist,
    modifier: Modifier = Modifier,
    metadata: ItemMetadata? = null,
    badges: @Composable RowScope.() -> Unit = {
        val libraryStateHolder = LocalLibraryStateHolder.current
        val isSaved by libraryStateHolder.isSavedArtist(artist.id).collectAsStateWithLifecycle(initialValue = false)
        if (isSaved) {
            Icon.Library()
        }
    },
    trailingContent: @Composable RowScope.() -> Unit = {},
) = ListItem(
    title = artist.artist.name,
    subtitle = pluralStringResource(R.plurals.n_song, artist.songCount, artist.songCount),
    badges = badges,
    thumbnailContent = {
        AsyncImage(
            model = artist.artist.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .size(ListThumbnailSize)
                .clip(CircleShape),
        )
    },
    trailingContent = trailingContent,
    modifier = modifier,
)

@Composable
fun ArtistGridItem(
    artist: Artist,
    modifier: Modifier = Modifier,
    metadata: ItemMetadata? = null,
    badges: @Composable RowScope.() -> Unit = {
        val libraryStateHolder = LocalLibraryStateHolder.current
        val isSaved by libraryStateHolder.isSavedArtist(artist.id).collectAsStateWithLifecycle(initialValue = false)
        if (isSaved) {
            Icon.Library()
        }
    },
    fillMaxWidth: Boolean = false,
) = GridItem(
    title = artist.artist.name,
    subtitle = pluralStringResource(R.plurals.n_song, artist.songCount, artist.songCount),
    badges = badges,
    thumbnailContent = {
        AsyncImage(
            model = artist.artist.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
        )
    },
    fillMaxWidth = fillMaxWidth,
    modifier = modifier
)

@Composable
fun AlbumListItem(
    album: Album,
    modifier: Modifier = Modifier,
    metadata: ItemMetadata? = null,
    showSavedIcon: Boolean = true,
    badges: @Composable RowScope.() -> Unit = {
        val libraryStateHolder = LocalLibraryStateHolder.current
        val isSaved by libraryStateHolder.isSavedAlbum(album.id).collectAsStateWithLifecycle(initialValue = false)
        if (showSavedIcon && isSaved) {
            Icon.Favorite()
        }

        if (album.album.explicit) {
            Icon.Explicit()
        }

        val downloadState = metadata?.downloadState ?: run {
            val database = LocalDatabase.current
            val downloadUtil = LocalDownloadUtil.current
            var songs by remember {
                mutableStateOf(emptyList<Song>())
            }

            LaunchedEffect(Unit) {
                database.albumSongs(album.id).collect {
                    songs = it
                }
            }

            var dState by remember {
                mutableIntStateOf(Download.STATE_STOPPED)
            }

            LaunchedEffect(songs) {
                if (songs.isEmpty()) return@LaunchedEffect
                downloadUtil.downloads.collect { downloads ->
                    dState = when {
                        songs.all { downloads[it.id]?.state == STATE_COMPLETED } -> STATE_COMPLETED
                        songs.all { downloads[it.id]?.state in listOf(STATE_QUEUED, STATE_DOWNLOADING, STATE_COMPLETED) } -> STATE_DOWNLOADING
                        else -> Download.STATE_STOPPED
                    }
                }
            }
            dState
        }
        Icon.Download(downloadState)
    },
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    trailingContent: @Composable RowScope.() -> Unit = {},
) = ListItem(
    title = album.album.title,
    subtitle = joinByBullet(
        album.artists.joinToString { it.name },
        pluralStringResource(R.plurals.n_song, album.album.songCount, album.album.songCount),
        album.album.year?.toString()
    ),
    badges = badges,
    thumbnailContent = {
        ItemThumbnail(
            thumbnailUrl = album.album.thumbnailUrl,
            isActive = isActive,
            isPlaying = isPlaying,
            shape = RoundedCornerShape(ThumbnailCornerRadius),
            modifier = Modifier.size(ListThumbnailSize)
        )
    },
    trailingContent = trailingContent,
    modifier = modifier
)

@Composable
fun AlbumGridItem(
    album: Album,
    modifier: Modifier = Modifier,
    coroutineScope: CoroutineScope,
    metadata: ItemMetadata? = null,
    badges: @Composable RowScope.() -> Unit = {
        val libraryStateHolder = LocalLibraryStateHolder.current
        val isSaved by libraryStateHolder.isSavedAlbum(album.id).collectAsStateWithLifecycle(initialValue = false)
        if (isSaved) {
            Icon.Favorite()
        }

        if (album.album.explicit) {
            Icon.Explicit()
        }

        val downloadState = metadata?.downloadState ?: run {
            val database = LocalDatabase.current
            val downloadUtil = LocalDownloadUtil.current
            var songs by remember { mutableStateOf(emptyList<Song>()) }

            LaunchedEffect(Unit) {
                database.albumSongs(album.id).collect { songs = it }
            }

            var dState by remember { mutableIntStateOf(Download.STATE_STOPPED) }

            LaunchedEffect(songs) {
                if (songs.isEmpty()) return@LaunchedEffect
                downloadUtil.downloads.collect { downloads ->
                    dState = when {
                        songs.all { downloads[it.id]?.state == STATE_COMPLETED } -> STATE_COMPLETED
                        songs.all { downloads[it.id]?.state in listOf(STATE_QUEUED, STATE_DOWNLOADING, STATE_COMPLETED) } -> STATE_DOWNLOADING
                        else -> Download.STATE_STOPPED
                    }
                }
            }
            dState
        }
        Icon.Download(downloadState)
    },
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    fillMaxWidth: Boolean = false,
) = GridItem(
    title = {
        Text(
            text = album.album.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    },
    subtitle = {
        val artistNames = album.artists.joinToString { it.name }.ifEmpty { album.album.artistName.orEmpty() }
        val subtitleText = joinByBullet(artistNames, album.album.year?.toString())
        if (subtitleText.isNotBlank()) {
            Text(
                text = subtitleText,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    },
    badges = badges,
    thumbnailContent = {
        val database = LocalDatabase.current
        val playerConnection = LocalPlayerConnection.current ?: return@GridItem

        ItemThumbnail(
            thumbnailUrl = album.album.thumbnailUrl,
            isActive = isActive,
            isPlaying = isPlaying,
            shape = RoundedCornerShape(GridThumbnailCornerRadius),
        )

        AlbumPlayButton(
            visible = !isActive,
            onClick = {
                coroutineScope.launch {
                    database.albumWithSongs(album.id).firstOrNull()?.let { albumWithSongs ->
                        playerConnection.playQueue(LocalAlbumRadio(albumWithSongs))
                    }
                }
            }
        )
    },
    fillMaxWidth = fillMaxWidth,
    modifier = modifier
)

@Composable
fun PlaylistListItem(
    playlist: Playlist,
    modifier: Modifier = Modifier,
    autoPlaylist: Boolean = false,
    metadata: ItemMetadata? = null,
    badges: @Composable RowScope.() -> Unit = {
        val libraryStateHolder = LocalLibraryStateHolder.current
        val isSaved by libraryStateHolder.isSavedPlaylist(playlist.id).collectAsStateWithLifecycle(initialValue = false)
        if (isSaved) {
            Icon.Favorite()
        }
    },
    trailingContent: @Composable RowScope.() -> Unit = {}
) = ListItem(
    title = playlist.playlist.name,
    subtitle = if (autoPlaylist) {
        ""
    } else {
        if (playlist.songCount == 0 && playlist.playlist.remoteSongCount != null) {
            pluralStringResource(
                R.plurals.n_song,
                playlist.playlist.remoteSongCount,
                playlist.playlist.remoteSongCount
            )
        } else {
            pluralStringResource(
                R.plurals.n_song,
                playlist.songCount,
                playlist.songCount
            )
        }
    },
    badges = badges,
    thumbnailContent = {
        PlaylistThumbnail(
            thumbnails = playlist.thumbnails,
            size = ListThumbnailSize,
            placeHolder = {
                val painter = when (playlist.playlist.name) {
                    stringResource(R.string.liked) -> R.drawable.favorite_border
                    stringResource(R.string.offline) -> R.drawable.offline
                    stringResource(R.string.cached_playlist) -> R.drawable.cached
                    else -> if (autoPlaylist) R.drawable.trending_up else R.drawable.queue_music
                }
                Icon(
                    painter = painterResource(painter),
                    contentDescription = null,
                    tint = LocalContentColor.current.copy(alpha = 0.8f),
                    modifier = Modifier.size(ListThumbnailSize / 2)
                )
            },
            shape = RoundedCornerShape(ThumbnailCornerRadius)
        )
    },
    trailingContent = trailingContent,
    modifier = modifier
)

@Composable
fun OverlayPlaylistListItem(
    playlist: Playlist,
    modifier: Modifier = Modifier,
    autoPlaylist: Boolean = false,
    badges: @Composable RowScope.() -> Unit = {},
    trailingContent: @Composable RowScope.() -> Unit = {},
    onClick: (() -> Unit)? = null,
) {
    var showPreview by remember { mutableStateOf(false) }
    val backgroundUrl = playlist.thumbnails.getOrNull(0)
    val (disableBlur) = rememberPreference(DisableBlurKey, false)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick?.invoke() }
    ) {
        Box(modifier = Modifier.height(120.dp)) {
            if (!backgroundUrl.isNullOrBlank()) {
                AsyncImage(
                    model = backgroundUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().let {
                        if (disableBlur) it else it.blur(8.dp)
                    }
                )
                Box(modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)),
                            startY = 40f
                        )
                    )
                )
            } else {
                Box(modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlaylistThumbnail(
                    thumbnails = playlist.thumbnails,
                    size = 72.dp,
                    placeHolder = {
                        val painter = when (playlist.playlist.name) {
                            stringResource(R.string.liked) -> R.drawable.favorite_border
                            stringResource(R.string.offline) -> R.drawable.offline
                            stringResource(R.string.cached_playlist) -> R.drawable.cached
                            else -> if (autoPlaylist) R.drawable.trending_up else R.drawable.queue_music
                        }
                        Icon(
                            painter = painterResource(painter),
                            contentDescription = null,
                            tint = LocalContentColor.current.copy(alpha = 0.9f),
                            modifier = Modifier.size(36.dp)
                        )
                    },
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlist.playlist.name,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    val subtitle = if (autoPlaylist) "" else {
                        if (playlist.songCount == 0 && playlist.playlist.remoteSongCount != null) {
                            pluralStringResource(R.plurals.n_song, playlist.playlist.remoteSongCount, playlist.playlist.remoteSongCount)
                        } else {
                            pluralStringResource(R.plurals.n_song, playlist.songCount, playlist.songCount)
                        }
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(modifier = Modifier.padding(start = 8.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.End) {
                    trailingContent()
                }
            }
        }
    }

    if (showPreview) {
        AlertDialog(
            onDismissRequest = { showPreview = false },
            confirmButton = { TextButton(onClick = { showPreview = false }) { Text(stringResource(R.string.close_dialog)) } },
            text = {
                Box(modifier = Modifier.fillMaxWidth().height(360.dp)) {
                    AsyncImage(model = backgroundUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
            }
        )
    }
}

@Composable
fun PlaylistGridItem(
    playlist: Playlist,
    modifier: Modifier = Modifier,
    autoPlaylist: Boolean = false,
    metadata: ItemMetadata? = null,
    badges: @Composable RowScope.() -> Unit = {
        val libraryStateHolder = LocalLibraryStateHolder.current
        val isSaved by libraryStateHolder.isSavedPlaylist(playlist.id).collectAsStateWithLifecycle(initialValue = false)
        if (isSaved) {
            Icon.Favorite()
        }
    },
    fillMaxWidth: Boolean = false,
) = GridItem(
    title = {
        Text(
            text = playlist.playlist.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    },
    subtitle = {
        val subtitle = if (autoPlaylist) {
            ""
        } else {
            if (playlist.songCount == 0 && playlist.playlist.remoteSongCount != null) {
                pluralStringResource(
                    R.plurals.n_song,
                    playlist.playlist.remoteSongCount,
                    playlist.playlist.remoteSongCount
                )
            } else {
                pluralStringResource(
                    R.plurals.n_song,
                    playlist.songCount,
                    playlist.songCount
                )
            }
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    },
    badges = badges,
    thumbnailContent = {
        val width = maxWidth
        PlaylistThumbnail(
            thumbnails = playlist.thumbnails,
            size = width,
            placeHolder = {
                val painter = when (playlist.playlist.name) {
                    stringResource(R.string.liked) -> R.drawable.favorite_border
                    stringResource(R.string.offline) -> R.drawable.offline
                    stringResource(R.string.cached_playlist) -> R.drawable.cached
                    else -> if (autoPlaylist) R.drawable.trending_up else R.drawable.queue_music
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        painter = painterResource(painter),
                        contentDescription = null,
                        tint = LocalContentColor.current.copy(alpha = 0.8f),
                        modifier = Modifier.size(width / 2)
                    )
                }
            },
            shape = RoundedCornerShape(GridThumbnailCornerRadius)
        )
    },
    fillMaxWidth = fillMaxWidth,
    modifier = modifier
)

@Composable
fun MediaMetadataListItem(
    mediaMetadata: MediaMetadata,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    shouldLoadImage: Boolean = true,
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    ListItem(
        title = mediaMetadata.title,
        subtitle = joinByBullet(
            mediaMetadata.artists.joinToString { it.name },
            makeTimeString(mediaMetadata.duration * 1000L)
        ),
        thumbnailContent = {
            ItemThumbnail(
                thumbnailUrl = mediaMetadata.thumbnailUrl,
                albumIndex = null,
                isSelected = isSelected,
                isActive = isActive,
                isPlaying = isPlaying,
                shouldLoadImage = shouldLoadImage,
                shape = RoundedCornerShape(ThumbnailCornerRadius),
                modifier = Modifier.size(ListThumbnailSize)
            )
        },
        trailingContent = trailingContent,
        modifier = modifier,
        isActive = isActive
    )
}

@Composable
fun YouTubeListItem(
    item: YTItem,
    modifier: Modifier = Modifier,
    albumIndex: Int? = null,
    viewCountText: String? = null,
    metadata: ItemMetadata? = null,
    isSelected: Boolean = false,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    isSwipeable: Boolean = true,
    trailingContent: @Composable RowScope.() -> Unit = {},
    badges: @Composable RowScope.() -> Unit = {
        val libraryStateHolder = LocalLibraryStateHolder.current
        val isSaved by remember(item.id, libraryStateHolder) { libraryStateHolder.isSavedSong(item.id) }.collectAsStateWithLifecycle(initialValue = false)
        val isAlbumSaved by remember(item.id, libraryStateHolder) { libraryStateHolder.isSavedAlbum(item.id) }.collectAsStateWithLifecycle(initialValue = false)
        val isArtistSaved by remember(item.id, libraryStateHolder) { libraryStateHolder.isSavedArtist(item.id) }.collectAsStateWithLifecycle(initialValue = false)
        val isPlaylistSaved by remember(item.id, libraryStateHolder) { libraryStateHolder.isSavedPlaylist(item.id) }.collectAsStateWithLifecycle(initialValue = false)

        if ((item is SongItem && isSaved) ||
            (item is AlbumItem && isAlbumSaved) ||
            (item is ArtistItem && isArtistSaved) ||
            (item is PlaylistItem && isPlaylistSaved)
        ) {
            Icon.Favorite()
        }
        if (item.explicit) Icon.Explicit()

        if (item is SongItem) {
            val downloadState = metadata?.downloadState ?: run {
                val downloads by LocalDownloadUtil.current.downloads.collectAsStateWithLifecycle()
                downloads[item.id]?.state
            }
            Icon.Download(downloadState)
        }
    },
) {
    val swipeEnabled by rememberPreference(SwipeToSongKey, defaultValue = false)

    val content: @Composable () -> Unit = {
        ListItem(
            title = item.title,
            subtitle = when (item) {
                is SongItem -> joinByBullet(
                    item.artists.joinToString { it.name },
                    makeTimeString(item.duration?.times(1000L)),
                    viewCountText
                )
                is AlbumItem -> joinByBullet(item.artists?.joinToString { it.name }, item.year?.toString())
                is ArtistItem -> null
                is PlaylistItem -> joinByBullet(item.author?.name, item.songCountText)
            },
            badges = badges,
            thumbnailContent = {
                ItemThumbnail(
                    thumbnailUrl = item.thumbnail,
                    albumIndex = albumIndex,
                    isSelected = isSelected,
                    isActive = isActive,
                    isPlaying = isPlaying,
                    shape = if (item is ArtistItem) CircleShape else RoundedCornerShape(ThumbnailCornerRadius),
                    modifier = Modifier.size(ListThumbnailSize)
                )
            },
            trailingContent = trailingContent,
            modifier = modifier,
            isActive = isActive
        )
    }

    if (item is SongItem && isSwipeable && swipeEnabled) {
        SwipeToSongBox(
            mediaItem = item.copy(thumbnail = item.thumbnail.resize(1080, 1080)).toMediaItem(),
            modifier = Modifier.fillMaxWidth()
        ) {
            content()
        }
    } else {
        content()
    }
}

@Composable
fun YouTubeGridItem(
    item: YTItem,
    modifier: Modifier = Modifier,
    coroutineScope: CoroutineScope? = null,
    metadata: ItemMetadata? = null,
    badges: @Composable RowScope.() -> Unit = {
        val libraryStateHolder = LocalLibraryStateHolder.current
        val isSaved by remember(item.id, libraryStateHolder) { libraryStateHolder.isSavedSong(item.id) }.collectAsStateWithLifecycle(initialValue = false)
        val isAlbumSaved by remember(item.id, libraryStateHolder) { libraryStateHolder.isSavedAlbum(item.id) }.collectAsStateWithLifecycle(initialValue = false)
        val isArtistSaved by remember(item.id, libraryStateHolder) { libraryStateHolder.isSavedArtist(item.id) }.collectAsStateWithLifecycle(initialValue = false)
        val isPlaylistSaved by remember(item.id, libraryStateHolder) { libraryStateHolder.isSavedPlaylist(item.id) }.collectAsStateWithLifecycle(initialValue = false)

        if ((item is SongItem && isSaved) ||
            (item is AlbumItem && isAlbumSaved) ||
            (item is ArtistItem && isArtistSaved) ||
            (item is PlaylistItem && isPlaylistSaved)
        ) {
            Icon.Favorite()
        }
        if (item.explicit) Icon.Explicit()
        if (item is SongItem) {
            val downloadState = metadata?.downloadState ?: run {
                val downloads by LocalDownloadUtil.current.downloads.collectAsStateWithLifecycle()
                downloads[item.id]?.state
            }
            Icon.Download(downloadState)
        }
    },
    thumbnailRatio: Float = if (item is SongItem) 16f / 9 else 1f,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    fillMaxWidth: Boolean = false,
) = GridItem(
    title = {
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (item is ArtistItem) TextAlign.Center else TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )
    },
    subtitle = {
        val subtitle = when (item) {
            is SongItem -> joinByBullet(item.artists.joinToString { it.name }, makeTimeString(item.duration?.times(1000L)))
            is AlbumItem -> joinByBullet(item.artists?.joinToString { it.name }, item.year?.toString())
            is ArtistItem -> null
            is PlaylistItem -> joinByBullet(item.author?.name, item.songCountText)
        }
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    },
    badges = badges,
    thumbnailContent = {
        val database = LocalDatabase.current
        val playerConnection = LocalPlayerConnection.current ?: return@GridItem
        val shape = if (item is ArtistItem) CircleShape else RoundedCornerShape(GridThumbnailCornerRadius)

        ItemThumbnail(
            thumbnailUrl = item.thumbnail,
            isActive = isActive,
            isPlaying = isPlaying,
            shape = shape,
            thumbnailRatio = thumbnailRatio
        )

        if (item is SongItem && !isActive) {
            OverlayPlayButton(
                visible = true
            )
        }

        AlbumPlayButton(
            visible = item is AlbumItem && !isActive,
            onClick = {
                coroutineScope?.launch(Dispatchers.IO) {
                    var albumWithSongs = database.albumWithSongs(item.id).first()
                    if (albumWithSongs?.songs.isNullOrEmpty()) {
                        YouTube.album(item.id).onSuccess { albumPage ->
                            database.withTransaction { insert(albumPage) }
                            albumWithSongs = database.albumWithSongs(item.id).first()
                        }.onFailure { reportException(it) }
                    }
                    albumWithSongs?.let {
                        withContext(Dispatchers.Main) {
                            playerConnection.playQueue(LocalAlbumRadio(it))
                        }
                    }
                }
            }
        )
    },
    thumbnailRatio = thumbnailRatio,
    fillMaxWidth = fillMaxWidth,
    modifier = modifier
)

@Composable
fun LocalSongsGrid(
    title: String,
    subtitle: String,
    badges: @Composable RowScope.() -> Unit = {},
    thumbnailUrl: String?,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    fillMaxWidth: Boolean = false,
    modifier: Modifier = Modifier
) = GridItem(
    title = title,
    subtitle = subtitle,
    badges = badges,
    thumbnailContent = {
        LocalThumbnail(
            thumbnailUrl = thumbnailUrl,
            isActive = isActive,
            isPlaying = isPlaying,
            shape = RoundedCornerShape(GridThumbnailCornerRadius),
            modifier = if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier,
            showCenterPlay = true,
            playButtonVisible = false
        )
    },
    fillMaxWidth = fillMaxWidth,
    modifier = modifier
)

@Composable
fun LocalArtistsGrid(
    title: String,
    subtitle: String,
    badges: @Composable RowScope.() -> Unit = {},
    thumbnailUrl: String?,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    fillMaxWidth: Boolean = false,
    modifier: Modifier = Modifier
) = GridItem(
    title = title,
    subtitle = subtitle,
    badges = badges,
    thumbnailContent = {
        LocalThumbnail(
            thumbnailUrl = thumbnailUrl,
            isActive = false,
            isPlaying = false,
            shape = CircleShape,
            modifier = if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier,
            showCenterPlay = false,
            playButtonVisible = false
        )
    },
    fillMaxWidth = fillMaxWidth,
    modifier = modifier
)

@Composable
fun LocalAlbumsGrid(
    title: String,
    subtitle: String,
    badges: @Composable RowScope.() -> Unit = {},
    thumbnailUrl: String?,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    fillMaxWidth: Boolean = false,
    modifier: Modifier = Modifier
) = GridItem(
    title = title,
    subtitle = subtitle,
    badges = badges,
    thumbnailContent = {
        LocalThumbnail(
            thumbnailUrl = thumbnailUrl,
            isActive = isActive,
            isPlaying = isPlaying,
            shape = RoundedCornerShape(GridThumbnailCornerRadius),
            modifier = if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier,
            showCenterPlay = false,
            playButtonVisible = true
        )
    },
    fillMaxWidth = fillMaxWidth,
    modifier = modifier
)

@Composable
fun ItemThumbnail(
    thumbnailUrl: String?,
    isActive: Boolean,
    isPlaying: Boolean,
    shape: Shape,
    modifier: Modifier = Modifier,
    albumIndex: Int? = null,
    isSelected: Boolean = false,
    shouldLoadImage: Boolean = true,
    thumbnailRatio: Float = 1f
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .aspectRatio(thumbnailRatio)
            .clip(shape)
    ) {
        val (cropThumbnailToSquare, _) = rememberPreference(CropThumbnailToSquareKey, false)
        val isYouTubeThumb = thumbnailUrl?.contains("ytimg.com", ignoreCase = true) == true
        val shouldApplySquareCrop = cropThumbnailToSquare && isYouTubeThumb && kotlin.math.abs(thumbnailRatio - 1f) < 0.001f
        val widthPx = if (maxWidth == Dp.Infinity) null else with(density) { maxWidth.roundToPx().coerceAtLeast(1) }
        val heightPx = if (maxHeight == Dp.Infinity) null else with(density) { maxHeight.roundToPx().coerceAtLeast(1) }

        if (albumIndex == null) {
            if (shouldLoadImage) {
                val request = remember(thumbnailUrl, widthPx, heightPx) {
                    ImageRequest.Builder(context)
                        .data(thumbnailUrl?.resize(544, 544))
                        .allowHardware(true)
                        .apply {
                            if (widthPx != null && heightPx != null) {
                                size(widthPx, heightPx)
                            }
                        }
                        .build()
                }
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = if (shouldApplySquareCrop) ContentScale.Crop else ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .let { if (shouldApplySquareCrop) it.aspectRatio(1f) else it }
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }
        }

        if (albumIndex != null) {
            AnimatedVisibility(
                visible = !isActive,
                enter = fadeIn() + expandIn(expandFrom = Alignment.Center),
                exit = shrinkOut(shrinkTowards = Alignment.Center) + fadeOut()
            ) {
                Text(
                    text = albumIndex.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = ArmoryColors.Rust2,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (isSelected) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f)
                    .clip(shape)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(
                    painter = painterResource(R.drawable.done),
                    contentDescription = null
                )
            }
        }

        PlayingIndicatorBox(
            isActive = isActive,
            playWhenReady = isPlaying,
            color = if (albumIndex != null) MaterialTheme.colorScheme.onBackground else Color.White,
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = if (albumIndex != null)
                        Color.Transparent
                    else
                        Color.Black.copy(alpha = ActiveBoxAlpha),
                    shape = shape
                )
        )
    }
}

@Composable
fun LocalThumbnail(
    thumbnailUrl: String?,
    isActive: Boolean,
    isPlaying: Boolean,
    shape: Shape,
    modifier: Modifier = Modifier,
    showCenterPlay: Boolean = false,
    playButtonVisible: Boolean = false,
    thumbnailRatio: Float = 1f
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .aspectRatio(thumbnailRatio)
            .clip(shape)
    ) {
        val (cropThumbnailToSquare, _) = rememberPreference(CropThumbnailToSquareKey, false)
        val isYouTubeThumb = thumbnailUrl?.contains("ytimg.com", ignoreCase = true) == true
        val shouldApplySquareCrop = cropThumbnailToSquare && isYouTubeThumb && kotlin.math.abs(thumbnailRatio - 1f) < 0.001f
        val widthPx = if (maxWidth == Dp.Infinity) null else with(density) { maxWidth.roundToPx().coerceAtLeast(1) }
        val heightPx = if (maxHeight == Dp.Infinity) null else with(density) { maxHeight.roundToPx().coerceAtLeast(1) }
        val request = remember(thumbnailUrl, widthPx, heightPx) {
            ImageRequest.Builder(context)
                .data(thumbnailUrl)
                .allowHardware(true)
                .apply {
                    if (widthPx != null && heightPx != null) {
                        size(widthPx, heightPx)
                    }
                }
                .build()
        }
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = if (shouldApplySquareCrop) ContentScale.Crop else ContentScale.Fit,
            modifier = Modifier.fillMaxSize().let { if (shouldApplySquareCrop) it.aspectRatio(1f) else it }
        )

        AnimatedVisibility(
            visible = isActive,
            enter = fadeIn(tween(500)),
            exit = fadeOut(tween(500))
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f), shape)
            ) {
                if (isPlaying) {
                    PlayingIndicator(
                        color = Color.White,
                        modifier = Modifier.height(24.dp)
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = null,
                        tint = Color.White
                    )
                }
            }
        }

        if (showCenterPlay) {
            AnimatedVisibility(
                visible = !(isActive && isPlaying),
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(8.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                ) {
                    Icon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = null,
                        tint = Color.White
                    )
                }
            }
        }

        if (playButtonVisible) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = ActiveBoxAlpha))
                ) {
                    Icon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = null,
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistThumbnail(
    thumbnails: List<String>,
    size: Dp,
    placeHolder: @Composable () -> Unit,
    shape: Shape
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val sizePx = with(density) { size.roundToPx().coerceAtLeast(1) }

    when (thumbnails.size) {
        0 -> Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            placeHolder()
        }
        1 -> {
            val request = remember(thumbnails, sizePx) {
                ImageRequest.Builder(context)
                    .data(thumbnails[0].resize((sizePx * 1.5).toInt(), (sizePx * 1.5).toInt()))
                    .size(sizePx, sizePx)
                    .allowHardware(true)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .clip(shape)
            )
        }
        else -> Box(
            modifier = Modifier
                .size(size)
                .clip(shape)
        ) {
            listOf(
                Alignment.TopStart,
                Alignment.TopEnd,
                Alignment.BottomStart,
                Alignment.BottomEnd
            ).fastForEachIndexed { index, alignment ->
                val halfPx = (sizePx / 2).coerceAtLeast(1)
                val url = thumbnails.getOrNull(index)
                val request = remember(url, halfPx) {
                    ImageRequest.Builder(context)
                        .data(url?.resize((halfPx * 1.5).toInt(), (halfPx * 1.5).toInt()))
                        .size(halfPx, halfPx)
                        .allowHardware(true)
                        .build()
                }
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .align(alignment)
                        .size(size / 2)
                )
            }
        }
    }
}

@Composable
fun BoxScope.OverlayPlayButton(
    visible: Boolean
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(Alignment.Center)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = ActiveBoxAlpha))
        ) {
            Icon(
                painter = painterResource(R.drawable.play),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun BoxScope.AlbumPlayButton(
    visible: Boolean,
    onClick: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(8.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = ActiveBoxAlpha))
                .clickable(onClick = onClick)
        ) {
            Icon(
                painter = painterResource(R.drawable.play),
                contentDescription = null,
                tint = Color.White
            )
        }
    }
}

@Composable
fun SwipeToSongBox(
    modifier: Modifier = Modifier,
    mediaItem: MediaItem,
    content: @Composable BoxScope.() -> Unit
) {
    val ctx = LocalContext.current
    val player = LocalPlayerConnection.current
    val scope = rememberCoroutineScope()
    val offset = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val threshold = 300f

    val dragState = rememberDraggableState { delta ->
        offset.floatValue = (offset.floatValue + delta).coerceIn(-threshold, threshold)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .draggable(
                orientation = Orientation.Horizontal,
                state = dragState,
                onDragStopped = {
                    when {
                        offset.floatValue >= threshold -> {
                            player?.playNext(listOf(mediaItem))
                            Toast.makeText(ctx, R.string.play_next, Toast.LENGTH_SHORT).show()
                            reset(offset, scope)
                        }

                        offset.floatValue <= -threshold -> {
                            player?.addToQueue(listOf(mediaItem))
                            Toast.makeText(ctx, R.string.add_to_queue, Toast.LENGTH_SHORT).show()
                            reset(offset, scope)
                        }

                        else -> reset(offset, scope)
                    }
                }
            )
    ) {
        if (offset.floatValue != 0f) {
            val (iconRes, bg, tint, align) = if (offset.floatValue > 0)
                Quadruple(
                    R.drawable.playlist_play,
                    MaterialTheme.colorScheme.secondary,
                    MaterialTheme.colorScheme.onSecondary,
                    Alignment.CenterStart
                ) else
                Quadruple(
                    R.drawable.queue_music,
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.onPrimary,
                    Alignment.CenterEnd
                )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .align(Alignment.Center)
                    .background(bg),
                contentAlignment = align
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .size(30.dp)
                        .alpha(0.9f),
                    tint = tint
                )
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offset.floatValue.roundToInt(), 0) }
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface),
            content = content
        )
    }
}

// Helper to animate reset of swipe offset
private fun reset(offset: MutableState<Float>, scope: CoroutineScope) {
    scope.launch {
        animate(
            initialValue = offset.value,
            targetValue = 0f,
            animationSpec = tween(durationMillis = 300)
        ) { value, _ -> offset.value = value }
    }
}

// Data holder for swipe visuals
data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

private object Icon {
    @Composable
    fun Favorite() {
        Icon(
            painter = painterResource(R.drawable.favorite),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .size(18.dp)
                .padding(end = 2.dp)
        )
    }

    @Composable
    fun Library() {
        Icon(
            painter = painterResource(R.drawable.library_add_check),
            contentDescription = null,
            modifier = Modifier
                .size(18.dp)
                .padding(end = 2.dp)
        )
    }

    @Composable
    fun Download(state: Int?) {
        when (state) {
            STATE_COMPLETED -> Icon(
                painter = painterResource(R.drawable.offline),
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .padding(end = 2.dp)
            )
            STATE_QUEUED, STATE_DOWNLOADING -> CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier
                    .size(16.dp)
                    .padding(end = 2.dp)
            )
            else -> { /* no icon */ }
        }
    }

    @Composable
    fun Explicit() {
        Icon(
            painter = painterResource(R.drawable.explicit),
            contentDescription = null,
            modifier = Modifier
                .size(18.dp)
                .padding(end = 2.dp)
        )
    }
}