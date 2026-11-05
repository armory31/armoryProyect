/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ruben.spotitube.ui.menu

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.ruben.spotitube.LocalDatabase
import com.ruben.spotitube.R
import com.ruben.spotitube.db.entities.SavedPlaylistEntity
import com.ruben.spotitube.ui.component.DefaultDialog
import com.ruben.spotitube.ui.component.TextFieldDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun ImportPlaylistDialog(
    isVisible: Boolean,
    onGetSong: suspend () -> List<String>,
    playlistTitle: String,
    browseId: String? = null,
    snackbarHostState: SnackbarHostState? = null,
    onDismiss: () -> Unit,
) {
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var currentPlaylistName by remember(playlistTitle) { mutableStateOf(playlistTitle) }
    var songIds by remember { mutableStateOf<List<String>?>(null) }
    var isImporting by remember { mutableStateOf(false) }
    var processedCount by remember { mutableStateOf(0) }
    var totalCount by remember { mutableStateOf(0) }
    var showDuplicateDialog by remember { mutableStateOf(false) }
    var existingPlaylistId by remember { mutableStateOf<String?>(null) }
    var isProcessingDuplicate by remember { mutableStateOf(false) }

    fun showMessage(message: String) {
        coroutineScope.launch {
            if (snackbarHostState != null) {
                snackbarHostState.showSnackbar(message)
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun resetState() {
        songIds = null
        isImporting = false
        processedCount = 0
        totalCount = 0
        showDuplicateDialog = false
        existingPlaylistId = null
        isProcessingDuplicate = false
    }

    if (isVisible) {
        TextFieldDialog(
            icon = { Icon(painter = painterResource(R.drawable.add), contentDescription = null) },
            title = { Text(text = stringResource(R.string.import_playlist)) },
            initialTextFieldValue = TextFieldValue(text = playlistTitle),
            autoFocus = false,
            onDismiss = {
                resetState()
                onDismiss()
            },
            extraContent = {
                if (isImporting) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.height(64.dp)
                    ) {
                        CircularProgressIndicator()
                        if (totalCount > 0) {
                            val percent = ((processedCount.toFloat() / totalCount.toFloat()) * 100).toInt()
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = "$processedCount / $totalCount ($percent%)")
                        }
                    }
                }
            },
            onDone = { finalName ->
                currentPlaylistName = finalName
                isImporting = true

                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val ids = onGetSong()
                        songIds = ids
                        totalCount = ids.size
                        processedCount = ids.size

                        if (ids.isEmpty()) {
                            showMessage(context.getString(R.string.import_failed))
                            withContext(Dispatchers.Main) {
                                resetState()
                                onDismiss()
                            }
                            return@launch
                        }

                        if (browseId != null) {
                            val existing = database.playlistByBrowseId(browseId).firstOrNull()
                            if (existing != null) {
                                withContext(Dispatchers.Main) {
                                    existingPlaylistId = existing.playlist.id
                                    isImporting = false
                                    showDuplicateDialog = true
                                }
                                return@launch
                            }
                        }

                        ids.forEachIndexed { index, _ ->
                            processedCount = index + 1
                        }

                        val playlistId = SavedPlaylistEntity.generatePlaylistId()
                        val savedPlaylist = SavedPlaylistEntity(
                            id = playlistId,
                            name = finalName,
                            browseId = browseId,
                            isEditable = true,
                        )
                        database.insertSavedPlaylist(savedPlaylist)

                        val playlist = database.playlist(playlistId).firstOrNull()
                        if (playlist != null) {
                            val resolvedMap = com.ruben.spotitube.utils.HistoryMetadataLoader.resolve(ids, timeoutMs = 5000L)
                            val items = ids.mapNotNull { id ->
                                resolvedMap[id]?.let { songItem ->
                                    com.ruben.spotitube.db.entities.PlaylistSongItem(
                                        id = songItem.id,
                                        title = songItem.title,
                                        artistName = songItem.artists.joinToString(", ") { it.name }.ifEmpty { null },
                                        thumbnailUrl = songItem.thumbnail,
                                        duration = songItem.duration ?: -1,
                                        albumId = songItem.album?.id,
                                        albumName = songItem.album?.name,
                                        explicit = songItem.explicit
                                    )
                                }
                            }
                            if (items.isNotEmpty()) {
                                database.addSongsToPlaylist(playlistId, items)
                            }
                        }

                        showMessage(context.getString(R.string.playlist_synced))
                        withContext(Dispatchers.Main) {
                            resetState()
                            onDismiss()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        showMessage(context.getString(R.string.import_failed) + ": ${e.message ?: "Unknown error"}")
                        withContext(Dispatchers.Main) {
                            resetState()
                            onDismiss()
                        }
                    }
                }
            }
        )
    }

    if (showDuplicateDialog && existingPlaylistId != null) {
        DefaultDialog(
            onDismiss = {
                if (!isProcessingDuplicate) {
                    resetState()
                }
            },
            title = { Text(text = stringResource(R.string.import_playlist)) },
            content = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = stringResource(R.string.already_in_playlist))
                    if (isProcessingDuplicate) {
                        Spacer(modifier = Modifier.height(16.dp))
                        CircularProgressIndicator()
                    }
                }
            },
            buttons = {
                TextButton(
                    enabled = !isProcessingDuplicate,
                    onClick = {
                        resetState()
                        onDismiss()
                    }
                ) { Text(text = stringResource(android.R.string.cancel)) }

                TextButton(
                    enabled = !isProcessingDuplicate,
                    onClick = {
                        isProcessingDuplicate = true
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                val ids = songIds ?: onGetSong()
                                if (ids.isEmpty()) {
                                    showMessage(context.getString(R.string.import_failed))
                                    withContext(Dispatchers.Main) {
                                        resetState()
                                        onDismiss()
                                    }
                                    return@launch
                                }

                                val playlist = database.playlist(existingPlaylistId!!).firstOrNull()
                                if (playlist != null) {
                                    val existingSongIds = database.playlistSongs(playlist.playlist.id).firstOrNull()
                                        ?.map { it.song.id }?.toSet() ?: emptySet()
                                    val newSongIds = ids.filterNot { it in existingSongIds }

                                    if (newSongIds.isEmpty()) {
                                        showMessage(context.getString(R.string.playlist_synced))
                                    } else {
                                        val resolvedMap = com.ruben.spotitube.utils.HistoryMetadataLoader.resolve(newSongIds, timeoutMs = 5000L)
                                        val items = newSongIds.mapNotNull { id ->
                                            resolvedMap[id]?.let { songItem ->
                                                com.ruben.spotitube.db.entities.PlaylistSongItem(
                                                    id = songItem.id,
                                                    title = songItem.title,
                                                    artistName = songItem.artists.joinToString(", ") { it.name }.ifEmpty { null },
                                                    thumbnailUrl = songItem.thumbnail,
                                                    duration = songItem.duration ?: -1,
                                                    albumId = songItem.album?.id,
                                                    albumName = songItem.album?.name,
                                                    explicit = songItem.explicit
                                                )
                                            }
                                        }
                                        if (items.isNotEmpty()) {
                                            database.addSongsToPlaylist(playlist.playlist.id, items)
                                        }
                                        showMessage(context.getString(R.string.playlist_synced))
                                    }
                                } else {
                                    showMessage(context.getString(R.string.import_failed))
                                }

                                withContext(Dispatchers.Main) {
                                    resetState()
                                    onDismiss()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                showMessage(context.getString(R.string.import_failed) + ": ${e.message ?: "Unknown error"}")
                                withContext(Dispatchers.Main) {
                                    resetState()
                                    onDismiss()
                                }
                            }
                        }
                    }
                ) { Text(text = stringResource(R.string.update_button)) }

                TextButton(
                    enabled = !isProcessingDuplicate,
                    onClick = {
                        isProcessingDuplicate = true
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                val ids = songIds ?: onGetSong()
                                if (ids.isEmpty()) {
                                    showMessage(context.getString(R.string.import_failed))
                                    withContext(Dispatchers.Main) {
                                        resetState()
                                        onDismiss()
                                    }
                                    return@launch
                                }

                                val playlistId = SavedPlaylistEntity.generatePlaylistId()
                                val savedPlaylist = SavedPlaylistEntity(
                                    id = playlistId,
                                    name = currentPlaylistName,
                                    browseId = null
                                )
                                database.insertSavedPlaylist(savedPlaylist)

                                val playlist = database.playlist(playlistId).firstOrNull()
                                if (playlist != null) {
                                    val resolvedMap = com.ruben.spotitube.utils.HistoryMetadataLoader.resolve(ids, timeoutMs = 5000L)
                                    val items = ids.mapNotNull { id ->
                                        resolvedMap[id]?.let { songItem ->
                                            com.ruben.spotitube.db.entities.PlaylistSongItem(
                                                id = songItem.id,
                                                title = songItem.title,
                                                artistName = songItem.artists.joinToString(", ") { it.name }.ifEmpty { null },
                                                thumbnailUrl = songItem.thumbnail,
                                                duration = songItem.duration ?: -1,
                                                albumId = songItem.album?.id,
                                                albumName = songItem.album?.name,
                                                explicit = songItem.explicit
                                            )
                                        }
                                    }
                                    if (items.isNotEmpty()) {
                                        database.addSongsToPlaylist(playlistId, items)
                                    }
                                    showMessage(context.getString(R.string.playlist_synced))
                                } else {
                                    showMessage(context.getString(R.string.import_failed))
                                }

                                withContext(Dispatchers.Main) {
                                    resetState()
                                    onDismiss()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                showMessage(context.getString(R.string.import_failed) + ": ${e.message ?: "Unknown error"}")
                                withContext(Dispatchers.Main) {
                                    resetState()
                                    onDismiss()
                                }
                            }
                        }
                    }
                ) { Text(text = stringResource(R.string.import_playlist)) }
            }
        )
    }
}
