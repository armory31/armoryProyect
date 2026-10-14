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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ruben.spotitube.LocalDatabase
import com.ruben.spotitube.LocalSyncManager
import com.ruben.spotitube.R
import com.ruben.spotitube.db.MusicDatabase
import com.ruben.spotitube.db.entities.SavedPlaylistEntity
import com.ruben.spotitube.innertube.YouTube
import com.ruben.spotitube.innertube.models.SongItem
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
    var processedCount by remember { mutableIntStateOf(0) }
    var totalCount by remember { mutableIntStateOf(0) }
    var selectedCsvUri by remember { mutableStateOf<Uri?>(null) }
    var textState by remember { mutableStateOf(TextFieldValue(initialTextFieldValue ?: "")) }

    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        selectedCsvUri = uri
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
        textState = TextFieldValue(fileName)
    }

    DefaultDialog(
        onDismiss = { if (!isImportingCsv) onDismiss() },
        icon = { Icon(painter = painterResource(R.drawable.playlist_add), contentDescription = null) },
        title = {
            Text(
                text = when {
                    isImportingCsv -> "Importando Playlist CSV"
                    selectedCsvUri != null -> "Importar Playlist CSV"
                    else -> stringResource(R.string.create_playlist)
                }
            )
        },
        buttons = {
            if (!isImportingCsv) {
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(android.R.string.cancel))
                }
                TextButton(
                    enabled = textState.text.trim().isNotEmpty(),
                    onClick = {
                        val playlistName = textState.text.trim()
                        val uri = selectedCsvUri
                        if (uri != null) {
                            isImportingCsv = true
                            coroutineScope.launch(Dispatchers.IO) {
                                try {
                                    val playlistId = importCsvPlaylistAndFetchMetadata(
                                        context = context,
                                        uri = uri,
                                        database = database,
                                        viewModel = backupRestoreViewModel,
                                        customName = playlistName,
                                        onProgress = { p, t ->
                                            processedCount = p
                                            totalCount = t
                                        }
                                    )
                                    withContext(Dispatchers.Main) {
                                        isImportingCsv = false
                                        if (playlistId != null) {
                                            Toast.makeText(context, "Playlist importada correctamente", Toast.LENGTH_SHORT).show()
                                            onDismiss()
                                        } else {
                                            Toast.makeText(context, "No se encontraron canciones en el CSV", Toast.LENGTH_SHORT).show()
                                            onDismiss()
                                        }
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    withContext(Dispatchers.Main) {
                                        isImportingCsv = false
                                        Toast.makeText(context, "Error al importar: ${e.message}", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    }
                                }
                            }
                        } else {
                            coroutineScope.launch(Dispatchers.IO) {
                                val browseId: String? = null
                                syncManager.createPlaylist(playlistName, browseId)
                            }
                            onDismiss()
                        }
                    }
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            if (isImportingCsv) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    if (totalCount > 0) {
                        val percent = ((processedCount.toFloat() / totalCount.toFloat()) * 100).toInt()
                        Text(
                            text = "Procesando $processedCount de $totalCount canciones ($percent%)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Text(
                            text = "Iniciando importación...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                TextField(
                    value = textState,
                    onValueChange = { textState = it },
                    placeholder = { Text(text = stringResource(R.string.playlist_name)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { csvLauncher.launch("*/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(painter = painterResource(R.drawable.add), contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = if (selectedCsvUri != null) "CSV Seleccionado (Cambiar)" else "Cargar desde CSV (.csv)")
                }
            }
        }
    }
}

suspend fun importCsvPlaylistAndFetchMetadata(
    context: Context,
    uri: Uri,
    database: MusicDatabase,
    viewModel: BackupRestoreViewModel,
    customName: String = "Playlist CSV",
    onProgress: ((processed: Int, total: Int) -> Unit)? = null
): String? {
    val songs = viewModel.importPlaylistFromCsv(context, uri)
    if (songs.isEmpty()) return null

    val total = songs.size
    onProgress?.invoke(0, total)

    val playlistId = SavedPlaylistEntity.generatePlaylistId()
    database.insertSavedPlaylist(
        SavedPlaylistEntity(
            id = playlistId,
            name = customName.ifBlank { "Playlist CSV" },
            isEditable = true
        )
    )

    songs.forEachIndexed { index, songItem ->
        var songId = songItem.song.id
        val title = songItem.song.title
        val artistName = songItem.artists.firstOrNull()?.name.orEmpty()

        if (songId.isBlank()) {
            val query = "$title $artistName".trim()
            if (query.isNotEmpty()) {
                val searchResult = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()?.items?.filterIsInstance<SongItem>()?.firstOrNull()
                if (searchResult != null) {
                    songId = searchResult.id
                } else {
                    songId = "CSV_" + RandomStringUtils.insecure().nextAlphanumeric(8)
                }
            }
        }

        if (songId.isNotBlank()) {
            if (database.checkInPlaylist(playlistId, songId) == 0) {
                database.addSongsToPlaylist(playlistId, listOf(songId))
            }
        }
        onProgress?.invoke(index + 1, total)
    }

    return playlistId
}
