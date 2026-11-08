package com.ruben.spotitube.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.ruben.spotitube.ArmoryTopBar
import com.ruben.spotitube.LocalDatabase
import com.ruben.spotitube.LocalPlayerAwareWindowInsets
import com.ruben.spotitube.R
import com.ruben.spotitube.db.entities.DownloadOriginType
import com.ruben.spotitube.ui.component.EmptyPlaceholder

@Composable
fun DownloadedOriginsScreen(navController: NavController, type: String) {
    val rows by LocalDatabase.current.downloadedOriginRows(type)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        if (rows.isEmpty()) {
            EmptyPlaceholder(
                icon = R.drawable.music_note,
                text = stringResource(R.string.playlist_is_empty),
            )
        } else {
            LazyColumn(
                contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
            ) {
                item(key = "top-spacer") {
                    Spacer(modifier = Modifier.height(56.dp))
                }
                items(
                    items = rows,
                    key = { it.originId },
                ) { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                navController.navigate(
                                    "downloaded_origin/$type/${Uri.encode(row.originId)}"
                                )
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = row.originThumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp)),
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 12.dp),
                        ) {
                            Text(
                                text = row.originTitle.takeUnless { it.isNullOrEmpty() } ?: "Sin título",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = pluralStringResource(
                                    R.plurals.n_song,
                                    row.songCount,
                                    row.songCount,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }

        ArmoryTopBar(
            title = if (type == DownloadOriginType.ALBUM) {
                "Álbumes descargados"
            } else {
                "Playlists descargadas"
            },
            onBack = navController::navigateUp,
        )
    }
}
