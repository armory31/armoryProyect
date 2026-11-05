/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ruben.spotitube.ui.menu

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ruben.spotitube.LocalDatabase
import com.ruben.spotitube.LocalLibraryStateHolder
import com.ruben.spotitube.LocalPlayerConnection
import com.ruben.spotitube.LocalSyncManager
import com.ruben.spotitube.R
import com.ruben.spotitube.constants.ArtistSongSortType
import com.ruben.spotitube.db.entities.Artist
import com.ruben.spotitube.extensions.toMediaItem
import com.ruben.spotitube.playback.queues.ListQueue
import com.ruben.spotitube.ui.component.ArtistListItem
import com.ruben.spotitube.ui.component.NewAction
import com.ruben.spotitube.ui.component.NewActionGrid
import com.ruben.spotitube.ui.component.primaryShareAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ArtistMenu(
    originalArtist: Artist,
    coroutineScope: CoroutineScope,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val artistState = database.artist(originalArtist.id).collectAsStateWithLifecycle(initialValue = originalArtist)
    val artist = artistState.value ?: originalArtist

    val libraryStateHolder = LocalLibraryStateHolder.current
    val syncManager = LocalSyncManager.current
    val isSaved by libraryStateHolder.isSavedArtist(artist.id).collectAsStateWithLifecycle(initialValue = false)

    ArtistListItem(
        artist = artist,
        badges = {},
        trailingContent = {},
    )

    HorizontalDivider()

    Spacer(modifier = Modifier.height(12.dp))

    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

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
            // Enhanced Action Grid using NewMenuComponents
            NewActionGrid(
                actions = buildList {
                    if (artist.songCount > 0) {
                        add(
                            NewAction(
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.play),
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                text = stringResource(R.string.play),
                                onClick = {
                                    coroutineScope.launch {
                                        val songs = withContext(Dispatchers.IO) {
                                            database
                                                .artistSongs(artist.id, ArtistSongSortType.CREATE_DATE, true)
                                                .first()
                                                .map { it.toMediaItem() }
                                        }
                                        playerConnection.playQueue(
                                            ListQueue(
                                                title = artist.artist.name,
                                                items = songs,
                                            ),
                                        )
                                    }
                                    onDismiss()
                                }
                            )
                        )

                        add(
                            NewAction(
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.shuffle),
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                text = stringResource(R.string.shuffle),
                                onClick = {
                                    coroutineScope.launch {
                                        val songs = withContext(Dispatchers.IO) {
                                            database
                                                .artistSongs(artist.id, ArtistSongSortType.CREATE_DATE, true)
                                                .first()
                                                .map { it.toMediaItem() }
                                                .shuffled()
                                        }
                                        playerConnection.playQueue(
                                            ListQueue(
                                                title = artist.artist.name,
                                                items = songs,
                                            ),
                                        )
                                    }
                                    onDismiss()
                                }
                            )
                        )
                    }

                    add(
                        primaryShareAction(
                            text = stringResource(R.string.share),
                            onClick = {
                                onDismiss()
                                com.ruben.spotitube.utils.shareItem(context, "artist", artist.id, artist.artist.name, imageUrl = artist.artist.thumbnailUrl)
                            },
                            backgroundColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        )
                    )
                },
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp)
            )
        }

        // Subscribe/Subscribed button
        item {
            ListItem(
                headlineContent = {
                    Text(text = if (isSaved) stringResource(R.string.subscribed) else stringResource(R.string.subscribe))
                },
                leadingContent = {
                    Icon(
                        painter = painterResource(if (isSaved) R.drawable.subscribed else R.drawable.subscribe),
                        contentDescription = null,
                    )
                },
                modifier = Modifier.clickable {
                    coroutineScope.launch {
                        syncManager.toggleArtistSaved(artist.artist)
                    }
                }
            )
        }
    }
}
