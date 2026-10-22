/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ruben.spotitube.ui.menu

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadService
import coil3.compose.AsyncImage
import com.ruben.spotitube.LocalDownloadUtil
import com.ruben.spotitube.LocalLibraryStateHolder
import com.ruben.spotitube.LocalPlayerConnection
import com.ruben.spotitube.LocalSyncManager
import com.ruben.spotitube.LocalSyncUtils
import com.ruben.spotitube.R
import com.ruben.spotitube.constants.ListThumbnailSize
import com.ruben.spotitube.constants.ThumbnailCornerRadius
import com.ruben.spotitube.constants.SpeedDialSongIdsKey
import com.ruben.spotitube.utils.rememberPreference
import com.ruben.spotitube.db.entities.PlaylistEntity
import com.ruben.spotitube.db.entities.SavedPlaylistEntity
import com.ruben.spotitube.extensions.toMediaItem
import com.ruben.spotitube.innertube.YouTube
import com.ruben.spotitube.innertube.models.PlaylistItem
import com.ruben.spotitube.innertube.models.SongItem
import com.ruben.spotitube.innertube.utils.completed
import com.ruben.spotitube.models.MediaMetadata
import com.ruben.spotitube.models.toMediaMetadata
import com.ruben.spotitube.playback.ExoDownloadService
import com.ruben.spotitube.playback.queues.YouTubeQueue
import com.ruben.spotitube.ui.component.DefaultDialog
import com.ruben.spotitube.ui.component.ListDialog
import com.ruben.spotitube.ui.component.MenuSurfaceSection
import com.ruben.spotitube.ui.component.NewAction
import com.ruben.spotitube.ui.component.NewActionGrid
import com.ruben.spotitube.ui.component.YouTubeListItem
import com.ruben.spotitube.utils.joinByBullet
import com.ruben.spotitube.utils.makeTimeString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("MutableCollectionMutableState", "LocalContextGetResourceValueCall")
@Composable
fun YouTubePlaylistMenu(
    playlist: PlaylistItem,
    songs: List<SongItem> = emptyList(),
    coroutineScope: CoroutineScope,
    onDismiss: () -> Unit,
    selectAction: () -> Unit = {},
    canSelect: Boolean = false,
    snackbarHostState: androidx.compose.material3.SnackbarHostState? = null,
) {
    val context = LocalContext.current
    val database = com.ruben.spotitube.LocalDatabase.current
    val libraryStateHolder = LocalLibraryStateHolder.current
    val syncManager = LocalSyncManager.current
    val downloadUtil = LocalDownloadUtil.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val syncUtils = LocalSyncUtils.current
    val isLiked by remember(playlist.id) {
        libraryStateHolder.savedPlaylistIds.map { it.contains(playlist.id) }
    }.collectAsStateWithLifecycle(initialValue = false)
    val dbPlaylist by database.playlistByBrowseId(playlist.id).collectAsStateWithLifecycle(initialValue = null)

    var showChoosePlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var showImportPlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var showErrorPlaylistAddDialog by rememberSaveable { mutableStateOf(false) }

    val notAddedList by remember {
        mutableStateOf(mutableListOf<MediaMetadata>())
    }

    AddToPlaylistDialog(
        isVisible = showChoosePlaylistDialog,
        onGetSong = { targetPlaylist ->
            val allSongs = songs
                .ifEmpty {
                    YouTube.playlist(playlist.id).completed().getOrNull()?.songs.orEmpty()
                }
            allSongs.map { it.id }
        },
        onDismiss = { showChoosePlaylistDialog = false },
        onAddComplete = { songCount, playlistNames ->
            val message = when {
                songCount == 1 && playlistNames.size == 1 -> context.getString(R.string.added_to_playlist, playlistNames.first())
                songCount > 1 && playlistNames.size == 1 -> context.getString(R.string.added_n_songs_to_playlist, songCount, playlistNames.first())
                songCount == 1 -> context.getString(R.string.added_to_n_playlists, playlistNames.size)
                else -> context.getString(R.string.added_n_songs_to_n_playlists, songCount, playlistNames.size)
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        },
    )

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        YouTubeListItem(
            item = playlist,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            trailingContent = {
                if (playlist.id != "LM" && !playlist.isEditable) {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                syncManager.togglePlaylistLike(
                                    PlaylistEntity(
                                        name = playlist.title,
                                        browseId = playlist.id,
                                        thumbnailUrl = playlist.thumbnail,
                                        isEditable = true,
                                        remoteSongCount = playlist.songCountText?.let {
                                            Regex("""\d+""").find(it)?.value?.toIntOrNull()
                                        },
                                        playEndpointParams = playlist.playEndpoint?.params,
                                        shuffleEndpointParams = playlist.shuffleEndpoint?.params,
                                        radioEndpointParams = playlist.radioEndpoint?.params,
                                    )
                                )
                            }
                        },
                    ) {
                        Icon(
                            painter = painterResource(if (isLiked) R.drawable.favorite else R.drawable.favorite_border),
                            tint = if (isLiked) MaterialTheme.colorScheme.error else LocalContentColor.current,
                            contentDescription = null,
                        )
                    }
                }
            },
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    var downloadState by remember {
        mutableIntStateOf(Download.STATE_STOPPED)
    }
    LaunchedEffect(songs) {
        if (songs.isEmpty()) return@LaunchedEffect
        downloadUtil.downloads.collect { downloads ->
            downloadState =
                if (songs.all { downloads[it.id]?.state == Download.STATE_COMPLETED })
                    Download.STATE_COMPLETED
                else if (songs.all {
                        downloads[it.id]?.state == Download.STATE_QUEUED
                                || downloads[it.id]?.state == Download.STATE_DOWNLOADING
                                || downloads[it.id]?.state == Download.STATE_COMPLETED
                    })
                    Download.STATE_DOWNLOADING
                else
                    Download.STATE_STOPPED
        }
    }
    var showRemoveDownloadDialog by remember {
        mutableStateOf(false)
    }
    if (showRemoveDownloadDialog) {
        DefaultDialog(
            onDismiss = { showRemoveDownloadDialog = false },
            content = {
                Text(
                    text = stringResource(
                        R.string.remove_download_playlist_confirm,
                        playlist.title
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp)
                )
            },
            buttons = {
                TextButton(
                    onClick = { showRemoveDownloadDialog = false }
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = {
                        showRemoveDownloadDialog = false
                        songs.forEach { song ->
                            DownloadService.sendRemoveDownload(
                                context,
                                ExoDownloadService::class.java,
                                song.id,
                                false
                            )
                        }
                    }
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            }
        )
    }

    ImportPlaylistDialog(
        isVisible = showImportPlaylistDialog,
        onGetSong = {
            val allSongs = songs
                .ifEmpty {
                    YouTube.playlist(playlist.id).completed().getOrNull()?.songs.orEmpty()
                }
            allSongs.map { it.id }
        },
        playlistTitle = playlist.title,
        browseId = playlist.id,
        snackbarHostState = snackbarHostState,
        onDismiss = { showImportPlaylistDialog = false }
    )

    if (showErrorPlaylistAddDialog) {
        ListDialog(
            onDismiss = {
                showErrorPlaylistAddDialog = false
                onDismiss()
            },
        ) {
            item {
                ListItem(
                    headlineContent = { Text(text = stringResource(R.string.already_in_playlist)) },
                    leadingContent = {
                        Image(
                            painter = painterResource(R.drawable.close),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
                            modifier = Modifier.size(ListThumbnailSize),
                        )
                    },
                    modifier = Modifier.clickable { showErrorPlaylistAddDialog = false },
                )
            }

            items(notAddedList) { song ->
                ListItem(
                    headlineContent = { Text(text = song.title) },
                    leadingContent = {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(ListThumbnailSize),
                        ) {
                            AsyncImage(
                                model = song.thumbnailUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(ThumbnailCornerRadius)),
                            )
                        }
                    },
                    supportingContent = {
                        Text(
                            text = joinByBullet(
                                song.artists.joinToString { it.name },
                                makeTimeString(song.duration * 1000L),
                            )
                        )
                    },
                )
            }
        }
    }

    val dividerModifier = Modifier.padding(start = 56.dp)
    val playText = stringResource(R.string.play)
    val shuffleText = stringResource(R.string.shuffle)
    val startRadioText = stringResource(R.string.start_radio)
    val playNextText = stringResource(R.string.play_next)
    val addToQueueText = stringResource(R.string.add_to_queue)
    val addToPlaylistText = stringResource(R.string.add_to_playlist)
    val importPlaylistText = stringResource(R.string.import_playlist)
    val ytSyncText = stringResource(R.string.yt_sync)
    val shareText = stringResource(R.string.share)
    val selectText = stringResource(R.string.select)

    val primaryActions = remember(
        playlist.playEndpoint,
        playlist.shuffleEndpoint,
        playlist.radioEndpoint,
        playText,
        shuffleText,
        startRadioText,
        playerConnection,
        onDismiss,
    ) {
        buildList {
            playlist.playEndpoint?.let { playEndpoint ->
                add(
                    NewAction(
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.play),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        text = playText,
                        onClick = {
                            playerConnection.playQueue(YouTubeQueue(playEndpoint))
                            onDismiss()
                        },
                    ),
                )
            }
            playlist.shuffleEndpoint?.let { shuffleEndpoint ->
                add(
                    NewAction(
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.shuffle),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        text = shuffleText,
                        onClick = {
                            playerConnection.playQueue(YouTubeQueue(shuffleEndpoint))
                            onDismiss()
                        },
                    ),
                )
            }
            playlist.radioEndpoint?.let { radioEndpoint ->
                add(
                    NewAction(
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.radio),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        text = startRadioText,
                        onClick = {
                            playerConnection.playQueue(YouTubeQueue(radioEndpoint))
                            onDismiss()
                        },
                    ),
                )
            }
        }
    }

    LazyColumn(
        userScrollEnabled = !isPortrait,
        contentPadding = PaddingValues(
            start = 0.dp,
            top = 0.dp,
            end = 0.dp,
            bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
        ),
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            MenuSurfaceSection(modifier = Modifier.padding(vertical = 6.dp)) {
                NewActionGrid(
                    actions = primaryActions,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            MenuSurfaceSection(modifier = Modifier.padding(vertical = 6.dp)) {
                Column {
                    ListItem(
                        headlineContent = { Text(text = playNextText) },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.playlist_play),
                                contentDescription = null,
                            )
                        },
                        modifier = Modifier.clickable {
                            coroutineScope.launch {
                                songs
                                    .ifEmpty {
                                        withContext(Dispatchers.IO) {
                                            YouTube.playlist(playlist.id).completed().getOrNull()?.songs.orEmpty()
                                        }
                                    }.let { list ->
                                        playerConnection.playNext(list.map { it.toMediaItem() })
                                    }
                            }
                            onDismiss()
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )

                    HorizontalDivider(
                        modifier = dividerModifier,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )

                    ListItem(
                        headlineContent = { Text(text = addToQueueText) },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.queue_music),
                                contentDescription = null,
                            )
                        },
                        modifier = Modifier.clickable {
                            coroutineScope.launch {
                                songs
                                    .ifEmpty {
                                        withContext(Dispatchers.IO) {
                                            YouTube.playlist(playlist.id).completed().getOrNull()?.songs.orEmpty()
                                        }
                                    }.let { list ->
                                        playerConnection.addToQueue(list.map { it.toMediaItem() })
                                    }
                            }
                            onDismiss()
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )

                    HorizontalDivider(
                        modifier = dividerModifier,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )

                    ListItem(
                        headlineContent = { Text(text = addToPlaylistText) },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.playlist_add),
                                contentDescription = null,
                            )
                        },
                        modifier = Modifier.clickable {
                            showChoosePlaylistDialog = true
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )

                    HorizontalDivider(
                        modifier = dividerModifier,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )

                    ListItem(
                        headlineContent = { Text(text = importPlaylistText) },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.add),
                                contentDescription = null,
                            )
                        },
                        modifier = Modifier.clickable {
                            showImportPlaylistDialog = true
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )

                    HorizontalDivider(
                        modifier = dividerModifier,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )

                    ListItem(
                        headlineContent = { Text(text = ytSyncText) },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.sync),
                                contentDescription = null,
                            )
                        },
                        trailingContent = {
                            val checked = dbPlaylist?.playlist?.isAutoSync ?: false
                            Switch(
                                checked = checked,
                                onCheckedChange = { newValue ->
                                    coroutineScope.launch(Dispatchers.IO) {
                                        try {
                                            val currentDbPlaylist = dbPlaylist
                                            if (currentDbPlaylist?.playlist == null) {
                                                val playlistPage = YouTube.playlist(playlist.id).completed().getOrNull()
                                                val fetchedSongs = playlistPage?.songs.orEmpty().map { it.toMediaMetadata() }

                                                if (fetchedSongs.isEmpty() && newValue) {
                                                    withContext(Dispatchers.Main) {
                                                        snackbarHostState?.showSnackbar(context.getString(R.string.import_failed))
                                                            ?: Toast.makeText(context, context.getString(R.string.import_failed), Toast.LENGTH_SHORT).show()
                                                    }
                                                    return@launch
                                                }

                                                database.withTransaction {
                                                    val playlistEntity =
                                                        PlaylistEntity(
                                                            name = playlist.title,
                                                            browseId = playlist.id,
                                                            thumbnailUrl = playlist.thumbnail,
                                                            isEditable = true,
                                                            isAutoSync = newValue,
                                                            remoteSongCount = playlist.songCountText?.let {
                                                                Regex("""\d+""").find(it)?.value?.toIntOrNull()
                                                            },
                                                            playEndpointParams = playlist.playEndpoint?.params,
                                                            shuffleEndpointParams = playlist.shuffleEndpoint?.params,
                                                            radioEndpointParams = playlist.radioEndpoint?.params,
                                                        )
                                                    val songItems = fetchedSongs.map { song ->
                                                        com.ruben.spotitube.db.entities.PlaylistSongItem(
                                                            id = song.id,
                                                            title = song.title,
                                                            artistName = song.artists.joinToString(", ") { it.name }.ifEmpty { null },
                                                            thumbnailUrl = song.thumbnailUrl,
                                                            duration = song.duration,
                                                            albumId = song.album?.id,
                                                            albumName = song.album?.title,
                                                            explicit = song.explicit
                                                        )
                                                    }
                                                    database.insertSavedPlaylist(
                                                        SavedPlaylistEntity(
                                                            id = playlistEntity.id,
                                                            name = playlistEntity.name,
                                                            browseId = playlistEntity.browseId,
                                                            isEditable = playlistEntity.isEditable,
                                                            thumbnailUrl = playlistEntity.thumbnailUrl,
                                                            createdAt = playlistEntity.createdAt ?: java.time.LocalDateTime.now(),
                                                            songs = songItems
                                                        )
                                                    )
                                                    update(playlistEntity)
                                                }

                                                if (newValue) {
                                                    withContext(Dispatchers.Main) {
                                                        snackbarHostState?.showSnackbar(context.getString(R.string.playlist_synced))
                                                            ?: Toast.makeText(context, context.getString(R.string.playlist_synced), Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            } else {
                                                val existing = currentDbPlaylist.playlist
                                                database.query {
                                                    update(existing.copy(isAutoSync = newValue))
                                                }

                                                if (newValue) {
                                                    syncUtils.syncAutoSyncPlaylists()
                                                    withContext(Dispatchers.Main) {
                                                        snackbarHostState?.showSnackbar(context.getString(R.string.playlist_synced))
                                                            ?: Toast.makeText(context, context.getString(R.string.playlist_synced), Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                            withContext(Dispatchers.Main) {
                                                val errorMsg = context.getString(R.string.import_failed) + ": ${e.message ?: "Unknown error"}"
                                                snackbarHostState?.showSnackbar(errorMsg)
                                                    ?: Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            MenuSurfaceSection(modifier = Modifier.padding(vertical = 6.dp)) {
                when (downloadState) {
                    Download.STATE_COMPLETED -> {
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = stringResource(R.string.remove_download),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.offline),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                            modifier = Modifier.clickable {
                                showRemoveDownloadDialog = true
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }

                    Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> {
                        ListItem(
                            headlineContent = { Text(text = stringResource(R.string.downloading)) },
                            leadingContent = {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                )
                            },
                            modifier = Modifier.clickable {
                                showRemoveDownloadDialog = true
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }

                    else -> {
                        ListItem(
                            headlineContent = { Text(text = stringResource(R.string.action_download)) },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.download),
                                    contentDescription = null,
                                )
                            },
                            modifier = Modifier.clickable {
                                coroutineScope.launch {
                                    songs
                                        .ifEmpty {
                                            withContext(Dispatchers.IO) {
                                                YouTube.playlist(playlist.id).completed().getOrNull()?.songs.orEmpty()
                                            }
                                        }.forEach { song ->
                                            val downloadRequest = com.ruben.spotitube.models.DownloadedSongMetadata.buildDownloadRequest(song)
                                            DownloadService.sendAddDownload(
                                                context,
                                                ExoDownloadService::class.java,
                                                downloadRequest,
                                                false,
                                            )
                                        }
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            MenuSurfaceSection(modifier = Modifier.padding(vertical = 6.dp)) {
                Column {
                    ListItem(
                        headlineContent = { Text(text = shareText) },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.share),
                                contentDescription = null,
                            )
                        },
                        modifier = Modifier.clickable {
                            val intent =
                                Intent().apply {
                                    action = Intent.ACTION_SEND
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, playlist.shareLink)
                                }
                            context.startActivity(Intent.createChooser(intent, null))
                            onDismiss()
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )

                    if (canSelect) {
                        HorizontalDivider(
                            modifier = dividerModifier,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )

                        ListItem(
                            headlineContent = { Text(text = selectText) },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.select_all),
                                    contentDescription = null,
                                )
                            },
                            modifier = Modifier.clickable {
                                onDismiss()
                                selectAction()
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            val (speedDialIds, onSpeedDialIdsChange) = rememberPreference(SpeedDialSongIdsKey, "")
            val speedDialList = remember(speedDialIds) {
                speedDialIds.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            }
            val playlistKey = "playlist:${playlist.id}"
            val isInSpeedDial = remember(speedDialList, playlistKey) { playlistKey in speedDialList }

            MenuSurfaceSection(modifier = Modifier.padding(vertical = 6.dp)) {
                ListItem(
                    headlineContent = {
                        Text(
                            text = stringResource(
                                if (isInSpeedDial) R.string.remove_from_speed_dial
                                else R.string.pin_to_speed_dial,
                            ),
                        )
                    },
                    leadingContent = {
                        Icon(
                            painter = painterResource(if (isInSpeedDial) R.drawable.bookmark_filled else R.drawable.bookmark),
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.clickable {
                        val updatedIds = if (isInSpeedDial) {
                            speedDialList.filterNot { it == playlistKey }
                        } else {
                            (speedDialList + playlistKey).distinct().take(24)
                        }
                        onSpeedDialIdsChange(updatedIds.joinToString(","))
                        onDismiss()
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }
}
