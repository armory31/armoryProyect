/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ruben.spotitube.ui.screens.playlist

import com.ruben.spotitube.ArmoryColors
import com.ruben.spotitube.ArmoryArtworkBackdrop
import com.ruben.spotitube.ArmoryGradients
import com.ruben.spotitube.MainHeadingColor
import androidx.compose.material3.ButtonDefaults

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
import androidx.compose.foundation.layout.union
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import com.ruben.spotitube.ArmoryScrollTopBar
import com.ruben.spotitube.ArmoryIconButton
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
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
import androidx.compose.ui.util.fastSumBy
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.allowHardware
import com.ruben.spotitube.ui.LocalDownloadUtil
import com.ruben.spotitube.ui.LocalPlayerAwareWindowInsets
import com.ruben.spotitube.ui.LocalPlayerConnection
import com.ruben.spotitube.R
import com.ruben.spotitube.constants.AutoPlaylistSongSortDescendingKey
import com.ruben.spotitube.constants.AutoPlaylistSongSortType
import com.ruben.spotitube.constants.AutoPlaylistSongSortTypeKey
import com.ruben.spotitube.constants.DisableBlurKey
import com.ruben.spotitube.constants.HideExplicitKey
import com.ruben.spotitube.db.entities.DownloadOriginType
import com.ruben.spotitube.db.entities.PlaylistEntity
import com.ruben.spotitube.db.entities.Song
import com.ruben.spotitube.extensions.toMediaItem
import com.ruben.spotitube.extensions.togglePlayPause
import com.ruben.spotitube.models.DownloadedSongMetadata
import com.ruben.spotitube.playback.queues.ListQueue
import com.ruben.spotitube.ui.component.DefaultDialog
import com.ruben.spotitube.ui.component.DraggableScrollbar
import com.ruben.spotitube.ui.component.EmptyPlaceholder
import com.ruben.spotitube.ui.component.LocalMenuState
import com.ruben.spotitube.ui.component.SongListItem
import com.ruben.spotitube.ui.component.SortHeader
import com.ruben.spotitube.ui.menu.SelectionSongMenu
import com.ruben.spotitube.ui.menu.SongMenu
import com.ruben.spotitube.ui.utils.ItemWrapper
import com.ruben.spotitube.utils.makeTimeString
import com.ruben.spotitube.utils.rememberEnumPreference
import com.ruben.spotitube.utils.rememberPreference
import com.ruben.spotitube.viewmodels.AutoPlaylistViewModel
import kotlinx.coroutines.launch

@Composable
fun AutoPlaylistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: AutoPlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val playlist =
        if (viewModel.playlist == "liked") stringResource(R.string.liked) else stringResource(R.string.offline)

    val songs by viewModel.savedSongs.collectAsStateWithLifecycle(initialValue = null)
    val mutableSongs = remember { mutableStateListOf<Song>() }

    var isSearching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf(TextFieldValue()) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isSearching) {
        if (isSearching) {
            focusRequester.requestFocus()
        }
    }

    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)
    val (disableBlur) = rememberPreference(DisableBlurKey, false)

    val likeLength = remember(songs) {
        songs?.fastSumBy { it.song.duration } ?: 0
    }

    val playlistId = viewModel.playlist
    val playlistType = when (playlistId) {
        "liked" -> PlaylistType.SAVED
        "downloaded" -> PlaylistType.DOWNLOAD
        else -> PlaylistType.OTHER
    }

    val wrappedSongs = remember(songs) {
        songs?.map { item -> ItemWrapper(item) }?.toMutableStateList() ?: mutableStateListOf()
    }

    var selection by remember { mutableStateOf(false) }

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

    val (sortType, onSortTypeChange) = rememberEnumPreference(
        AutoPlaylistSongSortTypeKey,
        AutoPlaylistSongSortType.CREATE_DATE
    )
    val (sortDescending, onSortDescendingChange) = rememberPreference(AutoPlaylistSongSortDescendingKey, true)

    val coroutineScope = rememberCoroutineScope()
    val originId = remember(playlistId) {
        if (playlistId == "liked") PlaylistEntity.SAVED_PLAYLIST_ID else playlistId.removePrefix("VL")
    }
    val downloadUtil = LocalDownloadUtil.current
    val downloadState = downloadUtil.originState(DownloadOriginType.PLAYLIST, originId)
        .collectAsStateWithLifecycle(initialValue = Download.STATE_STOPPED).value

    LaunchedEffect(songs) {
        mutableSongs.apply {
            clear()
            songs?.let { addAll(it) }
        }
    }

    var showRemoveDownloadDialog by remember { mutableStateOf(false) }

    if (showRemoveDownloadDialog) {
        DefaultDialog(
            onDismiss = { showRemoveDownloadDialog = false },
            content = {
                Text(
                    text = stringResource(R.string.remove_download_playlist_confirm, playlist),
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
                        coroutineScope.launch {
                            downloadUtil.removeOrigin(DownloadOriginType.PLAYLIST, originId)
                        }
                    },
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }

    val filteredSongs = remember(wrappedSongs, query) {
        if (query.text.isEmpty()) wrappedSongs
        else wrappedSongs.filter { wrapper ->
            val song = wrapper.item
            song.song.title.contains(query.text, true) ||
                    song.artists.any { it.name.contains(query.text, true) }
        }
    }

    val lazyListState = rememberLazyListState()

    val surfaceColor = MaterialTheme.colorScheme.background

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


    val headerItems by remember {
        derivedStateOf {
            val currentSongs = songs
            if (!currentSongs.isNullOrEmpty() && !isSearching) 2 else 0
        }
    }

    // System bars padding
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(surfaceColor),
    ) {
        ArmoryArtworkBackdrop(
            imageUrls = listOf(songs?.firstOrNull()?.song?.thumbnailUrl),
            alpha = gradientAlpha,
            enabled = !disableBlur,
            backgroundColor = surfaceColor
        )

        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            if (songs != null) {
                if (songs!!.isEmpty()) {
                    item {
                        EmptyPlaceholder(
                            icon = R.drawable.music_note,
                            text = stringResource(R.string.playlist_is_empty),
                        )
                    }
                } else {
                    if (!isSearching) {
                        // Hero Header Item
                        item(key = "header") {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                                    .padding(horizontal = 24.dp)
                                    .padding(bottom = 16.dp)
                            ) {
                                // Large centered artwork with shadow
                                Box(
                                    modifier = Modifier
                                        .size(240.dp)
                                        .shadow(
                                            elevation = 24.dp,
                                            shape = RoundedCornerShape(16.dp),
                                            ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                            spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                        )
                                ) {
                                    AsyncImage(
                                        model = songs!!.firstOrNull()?.song?.thumbnailUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(16.dp))
                                    )
                                }

                                // Metadata chips row
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Song count chip
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                                    ) {
                                        Text(
                                            text = pluralStringResource(
                                                R.plurals.n_song,
                                                songs!!.size,
                                                songs!!.size
                                            ),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = ArmoryColors.TextGold,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }

                                    // Duration chip
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                                    ) {
                                        Text(
                                            text = makeTimeString(likeLength * 1000L),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = ArmoryColors.TextGold,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                // Action buttons row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                if (playlistType != PlaylistType.DOWNLOAD) {
                                    // Download Button (Bronce Metálico)
                                    Surface(
                                        onClick = {
                                            when (downloadState) {
                                                Download.STATE_COMPLETED -> {
                                                    showRemoveDownloadDialog = true
                                                }
                                                Download.STATE_DOWNLOADING, Download.STATE_QUEUED -> {
                                                    coroutineScope.launch {
                                                        downloadUtil.cancelPending(DownloadOriginType.PLAYLIST, originId)
                                                    }
                                                }
                                                else -> {
                                                    if (!songs.isNullOrEmpty()) {
                                                        coroutineScope.launch {
                                                            downloadUtil.enqueue(
                                                                DownloadOriginType.PLAYLIST,
                                                                originId,
                                                                playlist,
                                                                songs?.firstOrNull()?.song?.thumbnailUrl,
                                                                songs!!.map { DownloadedSongMetadata.fromSong(it) }
                                                            )
                                                        }
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
                                }

                                    // Play Button (Plata Metálico)
                                    Button(
                                        onClick = {
                                            playerConnection.playQueue(
                                                ListQueue(
                                                    title = playlist,
                                                    items = songs!!.map { it.toMediaItem() },
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
                                                    title = playlist,
                                                    items = songs!!.shuffled().map { it.toMediaItem() },
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

                                    // Add to Queue Button (Bronce Metálico - El de al lado)
                                    Surface(
                                        onClick = {
                                            playerConnection.addToQueue(
                                                items = songs!!.map { it.toMediaItem() },
                                            )
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
                                            Icon(
                                                painter = painterResource(R.drawable.queue_music),
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(24.dp))
                            }
                        }
                    }

                    // Sort Header
                    item(key = "sortHeader") {
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
                                        AutoPlaylistSongSortType.CREATE_DATE -> R.string.sort_by_create_date
                                        AutoPlaylistSongSortType.NAME -> R.string.sort_by_name
                                        AutoPlaylistSongSortType.ARTIST -> R.string.sort_by_artist
                                        AutoPlaylistSongSortType.PLAY_TIME -> R.string.sort_by_play_time
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    // Song items
                    itemsIndexed(
                        items = filteredSongs,
                        key = { _, song -> song.item.id },
                    ) { index, songWrapper ->
                        SongListItem(
                            song = songWrapper.item,
                            isActive = songWrapper.item.song.id == mediaMetadata?.id,
                            isPlaying = isPlaying,
                            showInLibraryIcon = true,
                            trailingContent = {
                                androidx.compose.material3.IconButton(
                                    onClick = {
                                        menuState.show {
                                            SongMenu(
                                                originalSong = songWrapper.item,
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
                                                        title = playlist,
                                                        items = songs!!.map { it.toMediaItem() },
                                                        startIndex = songs!!.indexOfFirst { it.id == songWrapper.item.id }
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
                                            wrappedSongs.forEach { it.isSelected = false }
                                            songWrapper.isSelected = true
                                        }
                                    },
                                )
                                .animateItem()
                        )
                    }
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

        ArmoryScrollTopBar(
            title = {
                when {
                    selection -> {
                        val count = wrappedSongs.count { it.isSelected }
                        Text(
                            text = pluralStringResource(R.plurals.n_song, count, count),
                            style = MaterialTheme.typography.titleLarge,
                            color = MainHeadingColor
                        )
                    }
                    isSearching -> {
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
                    }
                    else -> {
                        Text(
                            text = playlist,
                            style = MaterialTheme.typography.titleLarge,
                            color = MainHeadingColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            listState = lazyListState,
            forceOpaque = disableBlur || selection,
            onBack = {
                when {
                    isSearching -> {
                        isSearching = false
                        query = TextFieldValue()
                        focusManager.clearFocus()
                    }
                    selection -> {
                        selection = false
                    }
                    else -> {
                        navController.navigateUp()
                    }
                }
            },
            centerTitle = true,
            actions = { transparentAppBar ->
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
                                        .map { it.item },
                                    onDismiss = menuState::dismiss,
                                    clearAction = { selection = false },
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
    }
}

enum class PlaylistType {
    SAVED, DOWNLOAD, OTHER
}
