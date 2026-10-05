/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
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
import com.ruben.spotitube.db.entities.PlaylistEntity
import com.ruben.spotitube.models.toMediaMetadata
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
                    CircularProgressIndicator()
                }
            },
            onDone = { finalName ->
                currentPlaylistName = finalName
                isImporting = true

                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val ids = onGetSong()
                        songIds = ids

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

                        ids.forEach { sId ->
                            val existingSong = database.getSongById(sId)?.song
                            if (existingSong == null || existingSong.duration <= 0) {
                                val refetched = com.ruben.spotitube.innertube.YouTube.queue(listOf(sId)).getOrNull()?.firstOrNull()?.let {
                                    it.toMediaMetadata()
                                }
                                if (refetched != null) {
                                    database.query { insert(refetched) }
                                }
                            }
                        }

                        val newPlaylist = PlaylistEntity(
                            name = finalName,
                            browseId = browseId,
                            isEditable = true,
                            isLocal = true,
                        )
                        database.query { insert(newPlaylist) }
                        database.insertSavedPlaylist(
                            com.ruben.spotitube.db.entities.SavedPlaylistEntity(
                                id = newPlaylist.id,
                                name = newPlaylist.name,
                                browseId = newPlaylist.browseId,
                                isEditable = true,
                                thumbnailUrl = newPlaylist.thumbnailUrl,
                            ),
                        )

                        val playlist = database.playlist(newPlaylist.id).firstOrNull()
                        if (playlist != null) {
                            database.addSongsToPlaylist(playlist.playlist.id, ids)
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
                                        database.addSongsToPlaylist(playlist.playlist.id, newSongIds)
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

                                val newPlaylist = PlaylistEntity(
                                    name = currentPlaylistName,
                                    browseId = null
                                )
                                database.query { insert(newPlaylist) }

                                val playlist = database.playlist(newPlaylist.id).firstOrNull()
                                if (playlist != null) {
                                    database.addSongsToPlaylist(playlist.playlist.id, ids)
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
