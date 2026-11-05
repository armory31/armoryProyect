/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ruben.spotitube.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.ruben.spotitube.LocalDatabase
import com.ruben.spotitube.LocalPlayerConnection
import com.ruben.spotitube.R
import com.ruben.spotitube.extensions.togglePlayPause
import com.ruben.spotitube.innertube.YouTube.SearchFilter.Companion.FILTER_ALBUM
import com.ruben.spotitube.innertube.YouTube.SearchFilter.Companion.FILTER_ARTIST
import com.ruben.spotitube.innertube.YouTube.SearchFilter.Companion.FILTER_COMMUNITY_PLAYLIST
import com.ruben.spotitube.innertube.YouTube.SearchFilter.Companion.FILTER_SONG
import com.ruben.spotitube.innertube.YouTube.SearchFilter.Companion.FILTER_VIDEO
import com.ruben.spotitube.innertube.models.AlbumItem
import com.ruben.spotitube.innertube.models.ArtistItem
import com.ruben.spotitube.innertube.models.PlaylistItem
import com.ruben.spotitube.innertube.models.SongItem
import com.ruben.spotitube.models.toMediaMetadata
import com.ruben.spotitube.playback.queues.YouTubeQueue
import com.ruben.spotitube.ui.component.ChipsRow
import com.ruben.spotitube.ui.component.LocalMenuState
import com.ruben.spotitube.ui.component.YouTubeListItem
import com.ruben.spotitube.ui.menu.YouTubeAlbumMenu
import com.ruben.spotitube.ui.menu.YouTubeArtistMenu
import com.ruben.spotitube.ui.menu.YouTubePlaylistMenu
import com.ruben.spotitube.ui.menu.YouTubeSongMenu
import com.ruben.spotitube.viewmodels.OnlineSearchSuggestionViewModel
import com.ruben.spotitube.viewmodels.OnlineSearchViewModel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

@Composable
fun OnlineSearchScreen(
    query: String,
    onQueryChange: (TextFieldValue) -> Unit,
    navController: NavController,
    onSearch: (String) -> Unit,
    onDismiss: () -> Unit,
    viewModel: OnlineSearchSuggestionViewModel = hiltViewModel(),
    onlineSearchViewModel: OnlineSearchViewModel = hiltViewModel(),
) {
    val database = LocalDatabase.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val coroutineScope = rememberCoroutineScope()
    val viewState by viewModel.viewState.collectAsStateWithLifecycle()

    val searchFilter by onlineSearchViewModel.filter.collectAsStateWithLifecycle()
    val searchSummary = onlineSearchViewModel.summaryPage

    val itemsPage by remember(searchFilter) {
        derivedStateOf {
            searchFilter?.value?.let {
                onlineSearchViewModel.viewStateMap[it]
            }
        }
    }

    val lazyListState = rememberLazyListState()

    LaunchedEffect(Unit) {
        snapshotFlow { lazyListState.firstVisibleItemScrollOffset }
            .drop(1)
            .collect {
                keyboardController?.hide()
            }
    }

    LaunchedEffect(query) {
        viewModel.query.value = query
        if (query.isNotBlank()) {
            onlineSearchViewModel.performSearch(query)
        }
    }

    LaunchedEffect(lazyListState) {
        snapshotFlow {
            lazyListState.layoutInfo.visibleItemsInfo.any { it.key == "loading" }
        }.collect { shouldLoadMore ->
            if (shouldLoadMore && query.isNotBlank()) {
                onlineSearchViewModel.loadMore()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Barra de Filtros por Categoría cuando hay búsqueda activa
        if (query.isNotBlank()) {
            ChipsRow(
                chips = listOf(
                    null to stringResource(R.string.filter_all),
                    FILTER_SONG to stringResource(R.string.filter_songs),
                    FILTER_VIDEO to stringResource(R.string.filter_videos),
                    FILTER_ALBUM to stringResource(R.string.filter_albums),
                    FILTER_ARTIST to stringResource(R.string.filter_artists),
                    FILTER_COMMUNITY_PLAYLIST to stringResource(R.string.filter_community_playlists),
                ),
                currentValue = searchFilter,
                onValueUpdate = { newFilter ->
                    if (onlineSearchViewModel.filter.value != newFilter) {
                        onlineSearchViewModel.filter.value = newFilter
                    }
                    coroutineScope.launch {
                        lazyListState.animateScrollToItem(0)
                    }
                },
                icons = mapOf(
                    null to R.drawable.search,
                    FILTER_SONG to R.drawable.music_note,
                    FILTER_VIDEO to R.drawable.slow_motion_video,
                    FILTER_ALBUM to R.drawable.album,
                    FILTER_ARTIST to R.drawable.person,
                    FILTER_COMMUNITY_PLAYLIST to R.drawable.queue_music,
                ),
            )
        }

        LazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp),
            modifier = Modifier.fillMaxSize().imePadding()
        ) {
            if (query.isBlank()) {
                items(viewState.history, key = { "history_${it.query}" }) { history ->
                    SuggestionItem(
                        query = history.query,
                        online = false,
                        onClick = {
                            onSearch(history.query)
                            onDismiss()
                        },
                        onDelete = {
                            database.query {
                                delete(history)
                            }
                        },
                        onFillTextField = {
                            onQueryChange(TextFieldValue(history.query, TextRange(history.query.length)))
                        },
                        modifier = Modifier.animateItem()
                    )
                }

                items(viewState.suggestions, key = { "suggestion_$it" }) { q ->
                    SuggestionItem(
                        query = q,
                        online = true,
                        onClick = {
                            onSearch(q)
                            onDismiss()
                        },
                        onFillTextField = {
                            onQueryChange(TextFieldValue(q, TextRange(q.length)))
                        },
                        modifier = Modifier.animateItem()
                    )
                }
            } else {
                // MODO BÚSQUEDA ACTIVADO CON O SIN FILTRO
                if (searchFilter == null) {
                    val summaries = searchSummary?.summaries.orEmpty()
                    summaries.forEachIndexed { index, summary ->
                        if (index > 0) {
                            item(key = "divider_$index") {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                )
                            }
                        }

                        item(key = "section_header_${summary.title}_$index") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(16.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = summary.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        items(summary.items.distinctBy { it.id }, key = { "sum_${it.id}_$index" }) { item ->
                            YouTubeListItem(
                                item = item,
                                isActive = when (item) {
                                    is SongItem -> mediaMetadata?.id == item.id
                                    is AlbumItem -> mediaMetadata?.album?.id == item.id
                                    else -> false
                                },
                                isPlaying = isPlaying,
                                trailingContent = {
                                    IconButton(
                                        onClick = {
                                            menuState.show {
                                                when (item) {
                                                    is SongItem -> YouTubeSongMenu(song = item, navController = navController, onDismiss = { menuState.dismiss(); onDismiss() })
                                                    is AlbumItem -> YouTubeAlbumMenu(albumItem = item, navController = navController, onDismiss = { menuState.dismiss(); onDismiss() })
                                                    is ArtistItem -> YouTubeArtistMenu(artist = item, onDismiss = { menuState.dismiss(); onDismiss() })
                                                    is PlaylistItem -> YouTubePlaylistMenu(playlist = item, coroutineScope = scope, onDismiss = { menuState.dismiss(); onDismiss() })
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(painter = painterResource(R.drawable.more_vert), contentDescription = null)
                                    }
                                },
                                modifier = Modifier.clickable {
                                    when (item) {
                                        is SongItem -> {
                                            if (item.id == mediaMetadata?.id) playerConnection.player.togglePlayPause()
                                            else { playerConnection.playQueue(YouTubeQueue.radio(item.toMediaMetadata())); onDismiss() }
                                        }
                                        is AlbumItem -> { navController.navigate("album/${item.id}"); onDismiss() }
                                        is ArtistItem -> { navController.navigate("artist/${item.id}"); onDismiss() }
                                        is PlaylistItem -> { navController.navigate("online_playlist/${item.id}"); onDismiss() }
                                    }
                                }
                            )
                        }
                    }
                } else {
                    // MODO FILTRADO (Canciones, Álbumes, Artistas, Listas, Videos)
                    val filterItems = itemsPage?.items.orEmpty()
                    items(filterItems.distinctBy { it.id }, key = { "filt_${it.id}" }) { item ->
                        YouTubeListItem(
                            item = item,
                            isActive = when (item) {
                                is SongItem -> mediaMetadata?.id == item.id
                                is AlbumItem -> mediaMetadata?.album?.id == item.id
                                else -> false
                            },
                            isPlaying = isPlaying,
                            trailingContent = {
                                IconButton(
                                    onClick = {
                                        menuState.show {
                                            when (item) {
                                                is SongItem -> YouTubeSongMenu(song = item, navController = navController, onDismiss = { menuState.dismiss(); onDismiss() })
                                                is AlbumItem -> YouTubeAlbumMenu(albumItem = item, navController = navController, onDismiss = { menuState.dismiss(); onDismiss() })
                                                is ArtistItem -> YouTubeArtistMenu(artist = item, onDismiss = { menuState.dismiss(); onDismiss() })
                                                is PlaylistItem -> YouTubePlaylistMenu(playlist = item, coroutineScope = scope, onDismiss = { menuState.dismiss(); onDismiss() })
                                            }
                                        }
                                    }
                                ) {
                                    Icon(painter = painterResource(R.drawable.more_vert), contentDescription = null)
                                }
                            },
                            modifier = Modifier.clickable {
                                when (item) {
                                    is SongItem -> {
                                        if (item.id == mediaMetadata?.id) playerConnection.player.togglePlayPause()
                                        else { playerConnection.playQueue(YouTubeQueue.radio(item.toMediaMetadata())); onDismiss() }
                                    }
                                    is AlbumItem -> { navController.navigate("album/${item.id}"); onDismiss() }
                                    is ArtistItem -> { navController.navigate("artist/${item.id}"); onDismiss() }
                                    is PlaylistItem -> { navController.navigate("online_playlist/${item.id}"); onDismiss() }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SuggestionItem(
    modifier: Modifier = Modifier,
    query: String,
    online: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit = {},
    onFillTextField: () -> Unit
) {
    val iconContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    val iconTint = MaterialTheme.colorScheme.primary

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .background(
                    color = iconContainerColor,
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
            Icon(
                painterResource(if (online) R.drawable.search else R.drawable.history),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(Modifier.width(14.dp))

        Text(
            text = query,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        if (!online) {
            IconButton(onClick = onDelete) {
                Icon(
                    painter = painterResource(R.drawable.close),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        IconButton(onClick = onFillTextField) {
            Icon(
                painter = painterResource(R.drawable.arrow_top_left),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
