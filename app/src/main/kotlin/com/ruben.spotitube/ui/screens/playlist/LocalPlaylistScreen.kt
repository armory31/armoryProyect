/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ruben.spotitube.ui.screens.playlist

import com.ruben.spotitube.ArmoryColors
import com.ruben.spotitube.ArmoryGradients
import androidx.compose.material3.ButtonDefaults

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import com.ruben.spotitube.ArmoryTopBar
import com.ruben.spotitube.ArmoryIconButton
import com.ruben.spotitube.ArmoryIcon
import com.ruben.spotitube.SectionHeader
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.util.fastSumBy
import androidx.compose.ui.zIndex
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.ruben.spotitube.LocalDatabase
import com.ruben.spotitube.LocalDownloadUtil
import com.ruben.spotitube.LocalPlayerAwareWindowInsets
import com.ruben.spotitube.LocalPlayerConnection
import com.ruben.spotitube.R
import com.ruben.spotitube.constants.AppBarHeight
import com.ruben.spotitube.constants.DisableBlurKey
import com.ruben.spotitube.constants.PlaylistEditLockKey
import com.ruben.spotitube.constants.PlaylistSongSortDescendingKey
import com.ruben.spotitube.constants.PlaylistSongSortType
import com.ruben.spotitube.constants.PlaylistSongSortTypeKey
import com.ruben.spotitube.constants.SwipeToSongKey
import com.ruben.spotitube.db.entities.PlaylistSong
import com.ruben.spotitube.db.entities.PlaylistSongMap
import com.ruben.spotitube.db.entities.SavedPlaylistEntity
import com.ruben.spotitube.extensions.move
import com.ruben.spotitube.extensions.toMediaItem
import com.ruben.spotitube.extensions.togglePlayPause
import com.ruben.spotitube.innertube.YouTube
import com.ruben.spotitube.innertube.models.SongItem
import com.ruben.spotitube.innertube.utils.completed
import com.ruben.spotitube.models.toMediaMetadata
import com.ruben.spotitube.playback.ExoDownloadService
import com.ruben.spotitube.playback.queues.ListQueue
import com.ruben.spotitube.playback.queues.LocalMixQueue
import com.ruben.spotitube.ui.component.AssignTagsDialog
import com.ruben.spotitube.ui.component.DefaultDialog
import com.ruben.spotitube.ui.component.DraggableScrollbar
import com.ruben.spotitube.ui.component.EditPlaylistDialog
import com.ruben.spotitube.ui.component.EmptyPlaceholder
import com.ruben.spotitube.ui.component.IconButton
import com.ruben.spotitube.ui.component.LocalMenuState
import com.ruben.spotitube.ui.component.SongListItem
import com.ruben.spotitube.ui.component.SortHeader
import com.ruben.spotitube.ui.menu.SelectionSongMenu
import com.ruben.spotitube.ui.menu.SongMenu
import com.ruben.spotitube.ui.theme.PlayerColorExtractor
import com.ruben.spotitube.ui.utils.ItemWrapper
import com.ruben.spotitube.ui.utils.backToMain
import com.ruben.spotitube.ui.utils.formatCompactCount
import com.ruben.spotitube.utils.makeTimeString
import com.ruben.spotitube.utils.rememberEnumPreference
import com.ruben.spotitube.utils.rememberPreference
import com.ruben.spotitube.viewmodels.LocalPlaylistViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.time.LocalDateTime

@SuppressLint("RememberReturnType", "LocalContextGetResourceValueCall")
@Composable
fun LocalPlaylistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: LocalPlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val playlist by viewModel.playlist.collectAsState()
    val isLiked by viewModel.isLiked.collectAsState()
    val songs by viewModel.playlistSongs.collectAsState()
    val viewCounts by viewModel.viewCounts.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val mutableSongs = remember { mutableStateListOf<PlaylistSong>() }
    val playlistLength = remember(songs) {
        songs.fastSumBy { it.song.song.duration }
    }
    val (sortType, onSortTypeChange) = rememberEnumPreference(
        PlaylistSongSortTypeKey,
        PlaylistSongSortType.CUSTOM
    )
    val (sortDescending, onSortDescendingChange) = rememberPreference(
        PlaylistSongSortDescendingKey,
        true
    )
    var locked by rememberPreference(PlaylistEditLockKey, defaultValue = true)
    val swipeToSongEnabled by rememberPreference(SwipeToSongKey, defaultValue = false)
    val (disableBlur) = rememberPreference(DisableBlurKey, false)
    var showAssignTagsDialog by remember { mutableStateOf(false) }

    if (showAssignTagsDialog && playlist != null) {
        AssignTagsDialog(
            database = database,
            playlistId = playlist!!.id,
            onDismiss = { showAssignTagsDialog = false }
        )
    }

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val pullRefreshState = rememberPullToRefreshState()

    // System bars padding
    val systemBarsTopPadding = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()

    var isSearching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }

    val filteredSongs = remember(songs, query) {
        if (query.text.isEmpty()) {
            songs
        } else {
            songs.filter { song ->
                song.song.song.title.contains(query.text, ignoreCase = true) ||
                        song.song.artists.fastAny { it.name.contains(query.text, ignoreCase = true) }
            }
        }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isSearching) {
        if (isSearching) {
            focusRequester.requestFocus()
        }
    }

    var selection by remember { mutableStateOf(false) }

    val wrappedSongs = remember(filteredSongs) {
        filteredSongs.map { item -> ItemWrapper(item) }
    }.toMutableStateList()

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

    val downloadUtil = LocalDownloadUtil.current
    var downloadState by remember { mutableIntStateOf(Download.STATE_STOPPED) }

    val editable: Boolean = playlist?.playlist?.isEditable == true

    LaunchedEffect(songs) {
        mutableSongs.apply {
            clear()
            addAll(songs)
        }
        if (songs.isEmpty()) return@LaunchedEffect
        downloadUtil.downloads.collect { downloads ->
            downloadState =
                if (songs.all { downloads[it.song.id]?.state == Download.STATE_COMPLETED }) {
                    Download.STATE_COMPLETED
                } else if (songs.all {
                        downloads[it.song.id]?.state == Download.STATE_QUEUED ||
                                downloads[it.song.id]?.state == Download.STATE_DOWNLOADING ||
                                downloads[it.song.id]?.state == Download.STATE_COMPLETED
                    }
                ) {
                    Download.STATE_DOWNLOADING
                } else {
                    Download.STATE_STOPPED
                }
        }
    }

    var showEditDialog by remember { mutableStateOf(false) }

    if (showEditDialog) {
        playlist?.let { playlistData ->
            EditPlaylistDialog(
                initialName = playlistData.playlist.name,
                initialThumbnailUrl = playlistData.playlist.thumbnailUrl,
                fallbackThumbnails = playlistData.songThumbnails.filterNotNull(),
                onDismiss = { showEditDialog = false },
                onSave = { name, thumbnailUrl ->
                    database.query {
                        val updated = playlistData.playlist.copy(
                            name = name,
                            thumbnailUrl = thumbnailUrl,
                            lastUpdateTime = LocalDateTime.now(),
                        )
                        database.insertSavedPlaylist(
                            SavedPlaylistEntity(
                                id = updated.id,
                                name = updated.name,
                                browseId = updated.browseId,
                                isEditable = updated.isEditable,
                                thumbnailUrl = updated.thumbnailUrl,
                                createdAt = updated.createdAt ?: LocalDateTime.now()
                            )
                        )
                        update(updated)
                    }
                    viewModel.viewModelScope.launch(Dispatchers.IO) {
                        playlistData.playlist.browseId?.let { YouTube.renamePlaylist(it, name) }
                    }
                },
            )
        }
    }

    var showRemoveDownloadDialog by remember { mutableStateOf(false) }

    if (showRemoveDownloadDialog) {
        DefaultDialog(
            onDismiss = { showRemoveDownloadDialog = false },
            content = {
                Text(
                    text = stringResource(
                        R.string.remove_download_playlist_confirm,
                        playlist?.playlist!!.name
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                TextButton(
                    onClick = { showRemoveDownloadDialog = false },
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }

                TextButton(
                    onClick = {
                        showRemoveDownloadDialog = false
                        if (!editable) {
                            coroutineScope.launch(Dispatchers.IO) {
                                database.withTransaction {
                                    playlist?.id?.let { clearPlaylist(it) }
                                }
                            }
                        }
                        songs.forEach { song ->
                            DownloadService.sendRemoveDownload(
                                context,
                                ExoDownloadService::class.java,
                                song.song.id,
                                false
                            )
                        }
                    }
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }

    var showDeletePlaylistDialog by remember { mutableStateOf(false) }
    if (showDeletePlaylistDialog) {
        DefaultDialog(
            onDismiss = { showDeletePlaylistDialog = false },
            content = {
                Text(
                    text = stringResource(
                        R.string.delete_playlist_confirm,
                        playlist?.playlist!!.name
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp)
                )
            },
            buttons = {
                TextButton(
                    onClick = { showDeletePlaylistDialog = false }
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = {
                        showDeletePlaylistDialog = false
                        database.query {
                            playlist?.let { database.deleteSavedPlaylist(it.playlist.id) }
                        }
                        viewModel.viewModelScope.launch(Dispatchers.IO) {
                            playlist?.playlist?.browseId?.let { YouTube.deletePlaylist(it) }
                        }
                        navController.popBackStack()
                    }
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            }
        )
    }

    val headerItems by remember {
        derivedStateOf {
            val current = playlist
            val hasContent = current != null &&
                (current.songCount > 0 || current.playlist.remoteSongCount != 0)
            if (hasContent && !isSearching) 2 else 0
        }
    }
    val lazyListState = rememberLazyListState()
    var dragInfo by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val reorderableState = rememberReorderableLazyListState(
        lazyListState = lazyListState,
        scrollThresholdPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    ) { from, to ->
        if (to.index >= headerItems && from.index >= headerItems) {
            val currentDragInfo = dragInfo
            dragInfo = if (currentDragInfo == null) {
                (from.index - headerItems) to (to.index - headerItems)
            } else {
                currentDragInfo.first to (to.index - headerItems)
            }
            mutableSongs.move(from.index - headerItems, to.index - headerItems)
        }
    }

    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging) {
            dragInfo?.let { (from, to) ->
                database.withTransaction {
                    move(viewModel.playlistId, from, to)
                }

                if (viewModel.playlist.value?.playlist?.browseId != null) {
                    viewModel.viewModelScope.launch(Dispatchers.IO) {
                        val playlistSongMap = database.playlistSongMaps(viewModel.playlistId, 0)
                        val successorIndex = if (from > to) to else to + 1
                        val successorSetVideoId = playlistSongMap.getOrNull(successorIndex)?.setVideoId

                        playlistSongMap.getOrNull(from)?.setVideoId?.let { setVideoId ->
                            YouTube.moveSongPlaylist(
                                viewModel.playlist.value?.playlist?.browseId!!,
                                setVideoId,
                                successorSetVideoId
                            )
                        }
                    }
                }
                dragInfo = null
            }
        }
    }

    val showTopBarTitle by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex > 0
        }
    }

    // Gradient colors state for playlist cover
    var gradientColors by remember { mutableStateOf<List<Color>>(emptyList()) }
    val fallbackColor = MaterialTheme.colorScheme.background.toArgb() // Inactivado surface para forzar pure black
    val surfaceColor = MaterialTheme.colorScheme.background // Inactivado surface para forzar pure black

    // Extract gradient colors from playlist cover
    LaunchedEffect(playlist?.thumbnails) {
        val thumbnailUrl = playlist?.thumbnails?.firstOrNull()
        if (thumbnailUrl != null) {
            val request = ImageRequest.Builder(context)
                .data(thumbnailUrl)
                .size(PlayerColorExtractor.Config.IMAGE_SIZE, PlayerColorExtractor.Config.IMAGE_SIZE)
                .allowHardware(false)
                .build()

            val result = runCatching {
                context.imageLoader.execute(request)
            }.getOrNull()

            if (result != null) {
                val bitmap = result.image?.toBitmap()
                if (bitmap != null) {
                    val palette = withContext(Dispatchers.Default) {
                        Palette.from(bitmap)
                            .maximumColorCount(PlayerColorExtractor.Config.MAX_COLOR_COUNT)
                            .resizeBitmapArea(PlayerColorExtractor.Config.BITMAP_AREA)
                            .generate()
                    }

                    val extractedColors = PlayerColorExtractor.extractGradientColors(
                        palette = palette,
                        fallbackColor = fallbackColor
                    )
                    gradientColors = extractedColors
                }
            }
        } else {
            gradientColors = emptyList()
        }
    }

    // Calculate gradient opacity based on scroll position
    val gradientAlpha by remember {
        derivedStateOf {
            if (lazyListState.firstVisibleItemIndex == 0) {
                val offset = lazyListState.firstVisibleItemScrollOffset
                (1f - (offset / 600f)).coerceIn(0f, 1f)
            } else {
                0f
            }
        }
    }

    val transparentAppBar by remember {
        derivedStateOf {
            !disableBlur && !selection && !showTopBarTitle
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(surfaceColor)
            .pullToRefresh(
                state = pullRefreshState,
                isRefreshing = isRefreshing,
                onRefresh = viewModel::refresh
            ),
    ) {
        // Mesh gradient background layer
        if (!disableBlur && gradientColors.isNotEmpty() && gradientAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxSize(0.55f)
                    .align(Alignment.TopCenter)
                    .zIndex(-1f)
                    .drawBehind {
                        val width = size.width
                        val height = size.height

                        if (gradientColors.size >= 3) {
                            val c0 = gradientColors[0]
                            val c1 = gradientColors[1]
                            val c2 = gradientColors[2]
                            val c3 = gradientColors.getOrElse(3) { c0 }
                            val c4 = gradientColors.getOrElse(4) { c1 }
                            // Primary color blob - top center
                            drawRect(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        c0.copy(alpha = gradientAlpha * 0.75f),
                                        c0.copy(alpha = gradientAlpha * 0.4f),
                                        Color.Transparent
                                    ),
                                    center = Offset(width * 0.5f, height * 0.15f),
                                    radius = width * 0.8f
                                )
                            )

                            // Secondary color blob - left side
                            drawRect(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        c1.copy(alpha = gradientAlpha * 0.55f),
                                        c1.copy(alpha = gradientAlpha * 0.3f),
                                        Color.Transparent
                                    ),
                                    center = Offset(width * 0.1f, height * 0.4f),
                                    radius = width * 0.6f
                                )
                            )

                            // Third color blob - right side
                            drawRect(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        c2.copy(alpha = gradientAlpha * 0.5f),
                                        c2.copy(alpha = gradientAlpha * 0.25f),
                                        Color.Transparent
                                    ),
                                    center = Offset(width * 0.9f, height * 0.35f),
                                    radius = width * 0.55f
                                )
                            )

                            drawRect(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        c3.copy(alpha = gradientAlpha * 0.35f),
                                        c3.copy(alpha = gradientAlpha * 0.18f),
                                        Color.Transparent
                                    ),
                                    center = Offset(width * 0.25f, height * 0.65f),
                                    radius = width * 0.75f
                                )
                            )

                            drawRect(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        c4.copy(alpha = gradientAlpha * 0.3f),
                                        c4.copy(alpha = gradientAlpha * 0.15f),
                                        Color.Transparent
                                    ),
                                    center = Offset(width * 0.55f, height * 0.85f),
                                    radius = width * 0.9f
                                )
                            )
                        } else if (gradientColors.isNotEmpty()) {
                            drawRect(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        gradientColors[0].copy(alpha = gradientAlpha * 0.7f),
                                        gradientColors[0].copy(alpha = gradientAlpha * 0.35f),
                                        Color.Transparent
                                    ),
                                    center = Offset(width * 0.5f, height * 0.25f),
                                    radius = width * 0.85f
                                )
                            )
                        }

                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Transparent,
                                    surfaceColor.copy(alpha = gradientAlpha * 0.22f),
                                    surfaceColor.copy(alpha = gradientAlpha * 0.55f),
                                    surfaceColor
                                ),
                                startY = height * 0.4f,
                                endY = height
                            )
                        )
                    }
            )
        }

        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime).asPaddingValues(),
        ) {
            playlist?.let { playlist ->
                if (playlist.songCount == 0 && playlist.playlist.remoteSongCount == 0) {
                    item {
                        EmptyPlaceholder(
                            icon = R.drawable.music_note,
                            text = stringResource(R.string.playlist_is_empty),
                        )
                    }
                } else {
                    if (!isSearching) {
                        // Hero Header
                        item(key = "header") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = systemBarsTopPadding + AppBarHeight),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Playlist Thumbnail(s) - Large centered with shadow
                                Box(
                                    modifier = Modifier
                                        .padding(top = 8.dp, bottom = 20.dp)
                                ) {
                                    if (playlist.thumbnails.size == 1) {
                                        // Single thumbnail
                                        Surface(
                                            modifier = Modifier
                                                .size(240.dp)
                                                .shadow(
                                                    elevation = 24.dp,
                                                    shape = RoundedCornerShape(16.dp),
                                                    spotColor = gradientColors.getOrNull(0)?.copy(alpha = 0.5f)
                                                        ?: MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                                ),
                                            shape = RoundedCornerShape(16.dp)
                                        ) {
                                            AsyncImage(
                                                model = playlist.thumbnails[0],
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    } else if (playlist.thumbnails.size > 1) {
                                        // Grid of 4 thumbnails
                                        Surface(
                                            modifier = Modifier
                                                .size(240.dp)
                                                .shadow(
                                                    elevation = 24.dp,
                                                    shape = RoundedCornerShape(16.dp),
                                                    spotColor = gradientColors.getOrNull(0)?.copy(alpha = 0.5f)
                                                        ?: MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                                ),
                                            shape = RoundedCornerShape(16.dp)
                                        ) {
                                            Box(modifier = Modifier.fillMaxSize()) {
                                                listOf(
                                                    Alignment.TopStart,
                                                    Alignment.TopEnd,
                                                    Alignment.BottomStart,
                                                    Alignment.BottomEnd,
                                                ).fastForEachIndexed { index, alignment ->
                                                    AsyncImage(
                                                        model = playlist.thumbnails.getOrNull(index),
                                                        contentDescription = null,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier
                                                            .align(alignment)
                                                            .size(120.dp)
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        // No thumbnail placeholder
                                        Surface(
                                            modifier = Modifier
                                                .size(240.dp)
                                                .shadow(
                                                    elevation = 16.dp,
                                                    shape = RoundedCornerShape(16.dp)
                                                ),
                                            shape = RoundedCornerShape(16.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    painter = painterResource(R.drawable.queue_music),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(80.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }

                                // Playlist Name
                                Text(
                                    text = playlist.playlist.name,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 32.dp)
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // Metadata Row - Song Count, Duration
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 48.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Song Count
                                    val songCount = if (songs.isNotEmpty()) songs.size else (playlist.songCount.takeIf { it > 0 } ?: playlist.playlist.remoteSongCount ?: 0)
                                    MetadataChip(
                                        icon = R.drawable.music_note,
                                        text = pluralStringResource(R.plurals.n_song, songCount, songCount),
                                        textColor = ArmoryColors.Rust2,
                                        iconTint = ArmoryColors.Rust2
                                    )

                                    // Duration
                                    if (playlistLength > 0) {
                                        MetadataChip(
                                            icon = R.drawable.timer,
                                            text = makeTimeString(playlistLength * 1000L),
                                            textColor = ArmoryColors.Rust2,
                                            iconTint = ArmoryColors.Rust2
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                // Action Buttons Row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Like/Delete Button (depending on editable)
                                    if (editable) {
                                        Surface(
                                            onClick = { showDeletePlaylistDialog = true },
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    painter = painterResource(R.drawable.delete),
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                    } else {
                                        Surface(
                                            onClick = viewModel::toggleLike,
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    painter = painterResource(
                                                        if (isLiked) R.drawable.favorite else R.drawable.favorite_border
                                                    ),
                                                    contentDescription = null,
                                                    tint = if (isLiked)
                                                        MaterialTheme.colorScheme.error
                                                    else
                                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Play Button (Plata Metálico)
                                    Button(
                                        onClick = {
                                            playerConnection.playQueue(
                                                ListQueue(
                                                    title = playlist.playlist.name,
                                                    items = songs.map { it.song.toMediaItem() },
                                                ),
                                            )
                                        },
                                        shape = RoundedCornerShape(24.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color.Transparent,
                                            contentColor = Color.Black
                                        ),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .background(brush = ArmoryGradients.MetallicSilver, shape = RoundedCornerShape(24.dp))
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.play),
                                            contentDescription = stringResource(R.string.play),
                                            tint = Color.Black,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    // Shuffle Button (Plata Metálico)
                                    Button(
                                        onClick = {
                                            playerConnection.playQueue(
                                                ListQueue(
                                                    title = playlist.playlist.name,
                                                    items = songs.shuffled().map { it.song.toMediaItem() },
                                                ),
                                            )
                                        },
                                        shape = RoundedCornerShape(24.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color.Transparent,
                                            contentColor = Color.Black
                                        ),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .background(brush = ArmoryGradients.MetallicSilver, shape = RoundedCornerShape(24.dp))
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.shuffle),
                                            contentDescription = stringResource(R.string.shuffle),
                                            tint = Color.Black,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    // Download Button (Bronce Metálico)
                                    Surface(
                                        onClick = {
                                            when (downloadState) {
                                                Download.STATE_COMPLETED -> {
                                                    showRemoveDownloadDialog = true
                                                }
                                                Download.STATE_DOWNLOADING -> {
                                                    songs.forEach { song ->
                                                        DownloadService.sendRemoveDownload(
                                                            context,
                                                            ExoDownloadService::class.java,
                                                            song.song.id,
                                                            false,
                                                        )
                                                    }
                                                }
                                                else -> {
                                                    songs.forEach { song ->
                                                        val downloadRequest = DownloadRequest
                                                            .Builder(song.song.id, song.song.id.toUri())
                                                            .setCustomCacheKey(song.song.id)
                                                            .setData(song.song.song.title.toByteArray())
                                                            .build()
                                                        DownloadService.sendAddDownload(
                                                            context,
                                                            ExoDownloadService::class.java,
                                                            downloadRequest,
                                                            false,
                                                        )
                                                    }
                                                }
                                            }
                                        },
                                        shape = CircleShape,
                                        color = Color.Transparent,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .background(brush = ArmoryGradients.MetallicCopper, shape = CircleShape)
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            when (downloadState) {
                                                Download.STATE_COMPLETED -> {
                                                    Icon(
                                                        painter = painterResource(R.drawable.offline),
                                                        contentDescription = null,
                                                        tint = Color.Black,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }
                                                Download.STATE_DOWNLOADING -> {
                                                    CircularProgressIndicator(
                                                        strokeWidth = 2.dp,
                                                        modifier = Modifier.size(24.dp),
                                                        color = Color.Black
                                                    )
                                                }
                                                else -> {
                                                    Icon(
                                                        painter = painterResource(R.drawable.download),
                                                        contentDescription = null,
                                                        tint = Color.Black,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // More Options Button (Bronce Metálico - El de al lado)
                                    Surface(
                                        onClick = {
                                            // Show more options (edit, sync, queue)
                                            if (editable) {
                                                showEditDialog = true
                                            } else if (playlist.playlist.browseId != null) {
                                                coroutineScope.launch(Dispatchers.IO) {
                                                    val playlistPage = YouTube.playlist(playlist.playlist.browseId)
                                                        .completed()
                                                        .getOrNull() ?: return@launch
                                                    database.withTransaction {
                                                        clearPlaylist(playlist.id)
                                                        playlistPage.songs
                                                            .map(SongItem::toMediaMetadata)
                                                            .onEach(::insert)
                                                            .mapIndexed { position, song ->
                                                                PlaylistSongMap(
                                                                    songId = song.id,
                                                                    playlistId = playlist.id,
                                                                    position = position,
                                                                    setVideoId = song.setVideoId
                                                                )
                                                            }
                                                            .forEach(::insert)
                                                    }
                                                }
                                                coroutineScope.launch(Dispatchers.Main) {
                                                    snackbarHostState.showSnackbar(context.getString(R.string.playlist_synced))
                                                }
                                            }
                                        },
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                painter = painterResource(
                                                    if (editable) R.drawable.edit else R.drawable.sync
                                                ),
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 20.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Start Mix Button
                                    Button(
                                        onClick = {
                                            playerConnection.playQueue(
                                                LocalMixQueue(
                                                    database = database,
                                                    playlistId = playlist.id,
                                                    maxMixSize = 50,
                                                ),
                                            )
                                        },
                                        shape = RoundedCornerShape(24.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.mix),
                                            contentDescription = "Start Mix",
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(24.dp))
                            }
                        }
                    }

                    // Sort Header
                    item(key = "sort_header") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 16.dp),
                        ) {
                            SortHeader(
                                sortType = sortType,
                                sortDescending = sortDescending,
                                onSortTypeChange = onSortTypeChange,
                                onSortDescendingChange = onSortDescendingChange,
                                sortTypeText = { sortType ->
                                    when (sortType) {
                                        PlaylistSongSortType.CUSTOM -> R.string.sort_by_custom
                                        PlaylistSongSortType.CREATE_DATE -> R.string.sort_by_create_date
                                        PlaylistSongSortType.NAME -> R.string.sort_by_name
                                        PlaylistSongSortType.ARTIST -> R.string.sort_by_artist
                                        PlaylistSongSortType.PLAY_TIME -> R.string.sort_by_play_time
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            )
                            if (editable && sortType == PlaylistSongSortType.CUSTOM) {
                                IconButton(
                                    onClick = { locked = !locked },
                                    onLongClick = {},
                                    modifier = Modifier.padding(horizontal = 6.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(if (locked) R.drawable.lock else R.drawable.lock_open),
                                        contentDescription = null,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Songs List
            if (!selection) {
                itemsIndexed(
                    items = if (isSearching) filteredSongs else mutableSongs,
                    key = { _, song -> song.map.id },
                ) { index, song ->
                    ReorderableItem(
                        state = reorderableState,
                        key = song.map.id,
                        modifier = Modifier.graphicsLayer {
                            compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                        }
                    ) {
                        val currentItem by rememberUpdatedState(song)

                        fun deleteFromPlaylist() {
                            val map = currentItem.map
                            val browseId = playlist?.playlist?.browseId
                            coroutineScope.launch(Dispatchers.IO) {
                                database.withTransaction {
                                    move(map.playlistId, map.position, Int.MAX_VALUE)
                                    delete(map.copy(position = Int.MAX_VALUE))
                                }
                                if (browseId != null) {
                                    val setVideoId = map.setVideoId ?: database.getSetVideoId(map.songId)?.setVideoId
                                    if (setVideoId != null) {
                                        YouTube.removeFromPlaylist(browseId, map.songId, setVideoId)
                                    }
                                }
                            }
                        }

                        val dismissBoxState = rememberSwipeToDismissBoxState(
                            positionalThreshold = { totalDistance -> totalDistance },
                            confirmValueChange = { targetValue ->
                                targetValue == SwipeToDismissBoxValue.Settled || !lazyListState.isScrollInProgress
                            }
                        )
                        var processedDismiss by remember { mutableStateOf(false) }
                        LaunchedEffect(dismissBoxState.currentValue) {
                            val dv = dismissBoxState.currentValue
                            if (!processedDismiss && (
                                        dv == SwipeToDismissBoxValue.StartToEnd ||
                                                dv == SwipeToDismissBoxValue.EndToStart
                                        )
                            ) {
                                processedDismiss = true
                                deleteFromPlaylist()
                            }
                            if (dv == SwipeToDismissBoxValue.Settled) {
                                processedDismiss = false
                            }
                        }

                        val content: @Composable () -> Unit = {
                            SongListItem(
                                song = song.song,
                                viewCountText =
                                    viewCounts[song.song.id]?.let { count -> formatCompactCount(count.toLong()) },
                                isActive = song.song.id == mediaMetadata?.id,
                                isPlaying = isPlaying,
                                trailingContent = {
                                    IconButton(
                                        onClick = {
                                            menuState.show {
                                                SongMenu(
                                                    originalSong = song.song,
                                                    playlistSong = song,
                                                    playlistBrowseId = playlist?.playlist?.browseId,
                                                    navController = navController,
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }
                                        },
                                        onLongClick = {}
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.more_vert),
                                            contentDescription = null,
                                        )
                                    }

                                    if (sortType == PlaylistSongSortType.CUSTOM && !locked && !selection && !isSearching && editable) {
                                        IconButton(
                                            onClick = { },
                                            onLongClick = {},
                                            modifier = Modifier
                                                .draggableHandle()
                                                .graphicsLayer { alpha = 0.99f },
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.drag_handle),
                                                contentDescription = null,
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            if (song.song.id == mediaMetadata?.id) {
                                                playerConnection.player.togglePlayPause()
                                            } else {
                                                playerConnection.playQueue(
                                                    ListQueue(
                                                        title = playlist!!.playlist.name,
                                                        items = songs.map { it.song.toMediaItem() },
                                                        startIndex = songs.indexOfFirst { it.map.id == song.map.id },
                                                    ),
                                                )
                                            }
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            if (!selection) {
                                                selection = true
                                            }
                                            wrappedSongs.forEach { it.isSelected = false }
                                            wrappedSongs.find { it.item.map.id == song.map.id }?.isSelected = true
                                        },
                                    ),
                            )
                        }

                        if (locked || selection || swipeToSongEnabled) {
                            content()
                        } else {
                            SwipeToDismissBox(
                                state = dismissBoxState,
                                backgroundContent = {},
                            ) {
                                content()
                            }
                        }
                    }
                }
            } else {
                itemsIndexed(
                    items = wrappedSongs,
                    key = { _, song -> song.item.map.id },
                ) { index, songWrapper ->
                    ReorderableItem(
                        state = reorderableState,
                        key = songWrapper.item.map.id,
                        modifier = Modifier.graphicsLayer {
                            compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                        }
                    ) {
                        val currentItem by rememberUpdatedState(songWrapper.item)

                        fun deleteFromPlaylist() {
                            val map = currentItem.map
                            coroutineScope.launch(Dispatchers.IO) {
                                database.withTransaction {
                                    move(map.playlistId, map.position, Int.MAX_VALUE)
                                    delete(map.copy(position = Int.MAX_VALUE))
                                }
                            }
                        }

                        val dismissBoxState = rememberSwipeToDismissBoxState(
                            positionalThreshold = { totalDistance -> totalDistance },
                            confirmValueChange = { targetValue ->
                                targetValue == SwipeToDismissBoxValue.Settled || !lazyListState.isScrollInProgress
                            }
                        )
                        var processedDismiss2 by remember { mutableStateOf(false) }
                        LaunchedEffect(dismissBoxState.currentValue) {
                            val dv = dismissBoxState.currentValue
                            if (!processedDismiss2 && (
                                        dv == SwipeToDismissBoxValue.StartToEnd ||
                                                dv == SwipeToDismissBoxValue.EndToStart
                                        )
                            ) {
                                processedDismiss2 = true
                                deleteFromPlaylist()
                            }
                            if (dv == SwipeToDismissBoxValue.Settled) {
                                processedDismiss2 = false
                            }
                        }

                        val content: @Composable () -> Unit = {
                            SongListItem(
                                song = songWrapper.item.song,
                                viewCountText =
                                    viewCounts[songWrapper.item.song.id]?.let { count -> formatCompactCount(count.toLong()) },
                                isActive = songWrapper.item.song.id == mediaMetadata?.id,
                                isPlaying = isPlaying,
                                trailingContent = {
                                    IconButton(
                                        onClick = {
                                            menuState.show {
                                                SongMenu(
                                                    originalSong = songWrapper.item.song,
                                                    playlistBrowseId = playlist?.playlist?.browseId,
                                                    navController = navController,
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }
                                        },
                                        onLongClick = {}
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.more_vert),
                                            contentDescription = null,
                                        )
                                    }
                                    if (sortType == PlaylistSongSortType.CUSTOM && !locked && !selection && !isSearching && editable) {
                                        IconButton(
                                            onClick = { },
                                            onLongClick = {},
                                            modifier = Modifier
                                                .draggableHandle()
                                                .graphicsLayer { alpha = 0.99f },
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.drag_handle),
                                                contentDescription = null,
                                            )
                                        }
                                    }
                                },
                                isSelected = songWrapper.isSelected && selection,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            if (!selection) {
                                                if (songWrapper.item.song.id == mediaMetadata?.id) {
                                                    playerConnection.player.togglePlayPause()
                                                } else {
                                                    playerConnection.playQueue(
                                                        ListQueue(
                                                            title = playlist!!.playlist.name,
                                                            items = songs.map { it.song.toMediaItem() },
                                                            startIndex = index,
                                                        ),
                                                    )
                                                }
                                            } else {
                                                songWrapper.isSelected = !songWrapper.isSelected
                                            }
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            if (!selection) {
                                                selection = true
                                            }
                                            wrappedSongs.forEach { it.isSelected = false }
                                            songWrapper.isSelected = true
                                        },
                                    ),
                            )
                        }

                        if (locked || !editable || swipeToSongEnabled) {
                            content()
                        } else {
                            SwipeToDismissBox(
                                state = dismissBoxState,
                                backgroundContent = {},
                            ) {
                                content()
                            }
                        }
                    }
                }
            }

            // Playlist Suggestions Section
            if (!selection && !isSearching) {
                item {
                    PlaylistSuggestionsSection(
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            }

        }

        DraggableScrollbar(
            modifier = Modifier
                .padding(
                    LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime)
                        .asPaddingValues()
                )
                .align(Alignment.CenterEnd),
            scrollState = lazyListState,
            headerItems = headerItems
        )

        ArmoryTopBar(
            title = {
                if (selection) {
                    val count = wrappedSongs.count { it.isSelected }
                    Text(
                        text = pluralStringResource(R.plurals.n_song, count, count),
                        style = MaterialTheme.typography.titleLarge
                    )
                } else if (isSearching) {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.search),
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleMedium,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                    )
                } else if (showTopBarTitle) {
                    Text(
                        text = playlist?.playlist?.name.orEmpty(),
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            },
            onBack = {
                if (isSearching) {
                    isSearching = false
                    query = TextFieldValue()
                } else if (selection) {
                    selection = false
                } else {
                    navController.navigateUp()
                }
            },
            overImage = transparentAppBar,
            actions = {
                if (selection) {
                    val count = wrappedSongs.count { it.isSelected }
                    ArmoryIconButton(
                        painter = painterResource(
                            if (count == wrappedSongs.size) R.drawable.deselect else R.drawable.select_all
                        ),
                        contentDescription = "Seleccionar",
                        onClick = {
                            if (count == wrappedSongs.size) {
                                wrappedSongs.forEach { it.isSelected = false }
                            } else {
                                wrappedSongs.forEach { it.isSelected = true }
                            }
                        },
                        overImage = transparentAppBar
                    )

                    ArmoryIconButton(
                        painter = painterResource(R.drawable.more_vert),
                        contentDescription = "Más",
                        onClick = {
                            menuState.show {
                                SelectionSongMenu(
                                    songSelection = wrappedSongs.filter { it.isSelected }
                                        .map { it.item.song },
                                    songPosition = wrappedSongs.filter { it.isSelected }
                                        .map { it.item.map },
                                    onDismiss = menuState::dismiss,
                                    clearAction = {
                                        selection = false
                                        wrappedSongs.clear()
                                    }
                                )
                            }
                        },
                        overImage = transparentAppBar
                    )
                } else if (!isSearching) {
                    ArmoryIconButton(
                        painter = painterResource(R.drawable.search),
                        contentDescription = "Buscar",
                        onClick = { isSearching = true },
                        overImage = transparentAppBar
                    )
                }
            }
        )

        PullToRefreshDefaults.Indicator(
            isRefreshing = isRefreshing,
            state = pullRefreshState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues()),
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime))
                .align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun MetadataChip(
    icon: Int,
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color? = null,
    iconTint: Color? = null
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = iconTint ?: MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = textColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
