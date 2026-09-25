/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ruben.spotitube.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import com.ruben.spotitube.LocalSyncManager
import com.ruben.spotitube.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    initialTextFieldValue: String? = null,
) {
    val syncManager = LocalSyncManager.current
    val coroutineScope = rememberCoroutineScope()

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
        extraContent = {}
    )
}
