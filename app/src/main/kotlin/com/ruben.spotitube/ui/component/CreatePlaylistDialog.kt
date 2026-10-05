/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ruben.spotitube.ui.component

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ruben.spotitube.LocalDatabase
import com.ruben.spotitube.LocalSyncManager
import com.ruben.spotitube.R
import com.ruben.spotitube.db.MusicDatabase
import com.ruben.spotitube.db.entities.PlaylistEntity
import com.ruben.spotitube.db.entities.SavedPlaylistEntity
import com.ruben.spotitube.db.entities.SongEntity
import com.ruben.spotitube.innertube.YouTube
import com.ruben.spotitube.innertube.models.SongItem
import com.ruben.spotitube.models.toMediaMetadata
import com.ruben.spotitube.viewmodels.BackupRestoreViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.lang3.RandomStringUtils

@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    initialTextFieldValue: String? = null,
) {
    val syncManager = LocalSyncManager.current
    val database = LocalDatabase.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val backupRestoreViewModel: BackupRestoreViewModel = hiltViewModel()

    var isImportingCsv by remember { mutableStateOf(false) }

    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isImportingCsv = true
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val playlistId = importCsvPlaylistAndFetchMetadata(context, uri, database, backupRestoreViewModel)
                withContext(Dispatchers.Main) {
                    isImportingCsv = false
                    if (playlistId != null) {
                        Toast.makeText(context, "Playlist CSV importada correctamente", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    } else {
                        Toast.makeText(context, "No se encontraron canciones en el archivo CSV", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    isImportingCsv = false
                    Toast.makeText(context, "Error al importar CSV: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    TextFieldDialog(
        icon = { Icon(painter = painterResource(R.drawable.playlist_add), contentDescription = null) },
        title = { Text(text = stringResource(R.string.create_playlist)) },
        placeholder = { Text(text = stringResource(R.string.playlist_name)) },
        isInputValid = { it.trim().isNotEmpty() },
        initialTextFieldValue = TextFieldValue(initialTextFieldValue ?: ""),
        onDismiss = onDismiss,
        onDone = { playlistName ->
            coroutineScope.launch(Dispatchers.IO) {
                val browseId: String? = null
                syncManager.createPlaylist(playlistName, browseId)
            }
        },
        extraContent = {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                enabled = !isImportingCsv,
                onClick = { csvLauncher.launch("*/*") }
            ) {
                Icon(painter = painterResource(R.drawable.add), contentDescription = null)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = if (isImportingCsv) "Importando CSV..." else "Importar desde CSV (.csv)")
            }
        }
    )
}

suspend fun importCsvPlaylistAndFetchMetadata(
    context: Context,
    uri: Uri,
    database: MusicDatabase,
    viewModel: BackupRestoreViewModel
): String? {
    val songs = viewModel.importPlaylistFromCsv(context, uri)
    if (songs.isEmpty()) return null

    var fileName = "Playlist CSV"
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && nameIndex >= 0) {
            val displayName = cursor.getString(nameIndex)
            if (!displayName.isNullOrBlank()) {
                fileName = displayName.removeSuffix(".csv").removeSuffix(".CSV")
            }
        }
    }

    val playlistId = PlaylistEntity.generatePlaylistId()
    val playlist = PlaylistEntity(
        id = playlistId,
        name = fileName,
        isEditable = true,
        isLocal = true
    )
    database.query { insert(playlist) }
    database.insertSavedPlaylist(
        SavedPlaylistEntity(
            id = playlist.id,
            name = playlist.name,
            isEditable = true
        )
    )

    songs.forEach { songItem ->
        var songId = songItem.song.id
        val title = songItem.song.title
        val artistName = songItem.artists.firstOrNull()?.name.orEmpty()

        if (songId.isBlank()) {
            val query = "$title $artistName".trim()
            if (query.isNotEmpty()) {
                val searchResult = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()?.items?.filterIsInstance<SongItem>()?.firstOrNull()
                if (searchResult != null) {
                    songId = searchResult.id
                    val mediaMetadata = searchResult.toMediaMetadata()
                    database.query { insert(mediaMetadata) }
                } else {
                    songId = "CSV_" + RandomStringUtils.insecure().nextAlphanumeric(8)
                    database.query { insert(SongEntity(id = songId, title = title)) }
                }
            }
        } else {
            val existing = database.getSongById(songId)?.song
            if (existing == null || existing.duration <= 0) {
                val refetched = YouTube.queue(listOf(songId)).getOrNull()?.firstOrNull()?.toMediaMetadata()
                if (refetched != null) {
                    database.query { insert(refetched) }
                }
            }
        }

        if (songId.isNotBlank() && database.checkInPlaylist(playlistId, songId) == 0) {
            database.addSongsToPlaylist(playlistId, listOf(songId))
        }
    }

    return playlistId
}
