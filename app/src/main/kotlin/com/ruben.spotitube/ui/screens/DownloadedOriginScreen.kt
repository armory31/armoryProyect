package com.ruben.spotitube.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.ruben.spotitube.ArmoryGradients
import com.ruben.spotitube.ArmoryTopBar
import com.ruben.spotitube.ui.LocalPlayerAwareWindowInsets
import com.ruben.spotitube.ui.LocalPlayerConnection
import com.ruben.spotitube.R
import com.ruben.spotitube.extensions.toMediaItem
import com.ruben.spotitube.extensions.togglePlayPause
import com.ruben.spotitube.playback.queues.ListQueue
import com.ruben.spotitube.ui.component.EmptyPlaceholder
import com.ruben.spotitube.ui.component.LocalMenuState
import com.ruben.spotitube.ui.component.SongListItem
import com.ruben.spotitube.ui.menu.SongMenu
import com.ruben.spotitube.viewmodels.DownloadedOriginViewModel

@Composable
fun DownloadedOriginScreen(
    navController: NavController,
    viewModel: DownloadedOriginViewModel = hiltViewModel(),
) {
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val info by viewModel.info.collectAsStateWithLifecycle()
    val playerConnection = LocalPlayerConnection.current
    val isPlaying by playerConnection?.isPlaying?.collectAsStateWithLifecycle(initialValue = false)
        ?: remember { mutableStateOf(false) }
    val mediaMetadata by playerConnection?.mediaMetadata?.collectAsStateWithLifecycle(initialValue = null)
        ?: remember { mutableStateOf(null) }
    val menuState = LocalMenuState.current

    val title = info?.originTitle ?: ""

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        if (songs.isEmpty()) {
            EmptyPlaceholder(
                icon = R.drawable.music_note,
                text = stringResource(R.string.playlist_is_empty),
            )
        } else {
            LazyColumn(
                contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
            ) {
                // Header
                item(key = "header") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AsyncImage(
                            model = info?.originThumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(180.dp)
                                .clip(RoundedCornerShape(16.dp)),
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = pluralStringResource(R.plurals.n_song, songs.size, songs.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Action buttons row (Play and Shuffle)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Button(
                                onClick = {
                                    playerConnection?.playQueue(
                                        ListQueue(
                                            title = title,
                                            items = songs.map { it.toMediaItem() },
                                        )
                                    )
                                },
                                shape = RoundedCornerShape(24.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Transparent,
                                    contentColor = Color.Black
                                ),
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .background(brush = ArmoryGradients.MetallicCopper, shape = RoundedCornerShape(24.dp)),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.play),
                                    contentDescription = stringResource(R.string.play),
                                    tint = Color.Black,
                                    modifier = Modifier.size(24.dp),
                                )
                            }

                            Button(
                                onClick = {
                                    playerConnection?.playQueue(
                                        ListQueue(
                                            title = title,
                                            items = songs.shuffled().map { it.toMediaItem() },
                                        )
                                    )
                                },
                                shape = RoundedCornerShape(24.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Transparent,
                                    contentColor = Color.Black
                                ),
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .background(brush = ArmoryGradients.MetallicSilver, shape = RoundedCornerShape(24.dp)),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.shuffle),
                                    contentDescription = stringResource(R.string.shuffle),
                                    tint = Color.Black,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // Songs list
                itemsIndexed(
                    items = songs,
                    key = { _, song -> song.id },
                ) { index, song ->
                    val isActive = song.id == mediaMetadata?.id
                    SongListItem(
                        song = song,
                        isActive = isActive,
                        isPlaying = isPlaying,
                        showDownloadIcon = true,
                        trailingContent = {
                            IconButton(
                                onClick = {
                                    menuState.show {
                                        SongMenu(
                                            originalSong = song,
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
                        modifier = Modifier.clickable {
                            if (isActive) {
                                playerConnection?.player?.togglePlayPause()
                            } else {
                                playerConnection?.playQueue(
                                    ListQueue(
                                        title = title,
                                        items = songs.map { it.toMediaItem() },
                                        startIndex = index,
                                    )
                                )
                            }
                        },
                    )
                }
            }
        }

        ArmoryTopBar(
            title = {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            onBack = navController::navigateUp,
        )
    }
}
