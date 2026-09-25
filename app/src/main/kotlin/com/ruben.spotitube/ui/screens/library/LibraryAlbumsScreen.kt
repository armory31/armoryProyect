/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ruben.spotitube.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.ruben.spotitube.ArmoryColors
import com.ruben.spotitube.LocalPlayerConnection
import com.ruben.spotitube.R
import com.ruben.spotitube.constants.AlbumFilter
import com.ruben.spotitube.constants.AlbumFilterKey
import com.ruben.spotitube.constants.AlbumSortDescendingKey
import com.ruben.spotitube.constants.AlbumSortType
import com.ruben.spotitube.constants.AlbumSortTypeKey
import com.ruben.spotitube.constants.AlbumViewTypeKey
import com.ruben.spotitube.constants.CONTENT_TYPE_ALBUM
import com.ruben.spotitube.constants.CONTENT_TYPE_HEADER
import com.ruben.spotitube.constants.GridItemSize
import com.ruben.spotitube.constants.GridItemsSizeKey
import com.ruben.spotitube.constants.GridThumbnailHeight
import com.ruben.spotitube.constants.HideExplicitKey
import com.ruben.spotitube.constants.LibraryViewType
import com.ruben.spotitube.constants.YtmSyncKey
import com.ruben.spotitube.ui.component.ChipsRow
import com.ruben.spotitube.ui.component.EmptyPlaceholder
import com.ruben.spotitube.ui.component.LibraryAlbumGridItem
import com.ruben.spotitube.ui.component.LibraryAlbumListItem
import com.ruben.spotitube.ui.component.LocalMenuState
import com.ruben.spotitube.ui.component.SortHeader
import com.ruben.spotitube.utils.rememberEnumPreference
import com.ruben.spotitube.utils.rememberPreference
import com.ruben.spotitube.viewmodels.LibraryAlbumsViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LibraryAlbumsScreen(
    navController: NavController,
    onDeselect: () -> Unit,
    viewModel: LibraryAlbumsViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    var viewType by rememberEnumPreference(AlbumViewTypeKey, LibraryViewType.GRID)
    var filter by rememberEnumPreference(AlbumFilterKey, AlbumFilter.LIKED)
    val (sortType, onSortTypeChange) = rememberEnumPreference(
        AlbumSortTypeKey,
        AlbumSortType.CREATE_DATE
    )
    val (sortDescending, onSortDescendingChange) = rememberPreference(AlbumSortDescendingKey, true)
    val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)

    val (ytmSync) = rememberPreference(YtmSyncKey, true)
    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)

    val filterContent = @Composable {
        Row(modifier = Modifier.padding(vertical = 8.dp)) {
            Spacer(Modifier.width(12.dp))
            FilterChip(
                label = { Text(stringResource(R.string.albums)) },
                selected = true,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = ArmoryColors.Gold5.copy(alpha = 0.3f),
                    labelColor = ArmoryColors.Gold2,
                    selectedContainerColor = ArmoryColors.Gold5.copy(alpha = 0.5f),
                    selectedLabelColor = ArmoryColors.Gold1
                ),
                onClick = onDeselect,
                shape = RoundedCornerShape(16.dp),
                leadingIcon = {
                    Icon(painter = painterResource(R.drawable.close), contentDescription = "", tint = ArmoryColors.Gold2)
                },
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = true,
                    borderColor = ArmoryColors.Gold4.copy(alpha = 0.4f),
                    selectedBorderColor = ArmoryColors.Gold3.copy(alpha = 0.6f)
                )
            )
            ChipsRow(
                chips = listOf(
                    AlbumFilter.LIKED to stringResource(R.string.filter_liked),
                    AlbumFilter.LIBRARY to stringResource(R.string.filter_library),
                    AlbumFilter.DOWNLOADED to stringResource(R.string.filter_downloaded),
                    AlbumFilter.DOWNLOADED_FULL to stringResource(R.string.filter_downloaded_full)
                ),
                currentValue = filter,
                onValueUpdate = { filter = it },
                modifier = Modifier.weight(1f),
            )
        }
    }

    val albums by viewModel.allAlbums.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    val lazyGridState = rememberLazyGridState()
    val pullRefreshState = rememberPullToRefreshState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop = backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsState()

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            when (viewType) {
                LibraryViewType.LIST -> lazyListState.animateScrollToItem(0)
                LibraryViewType.GRID -> lazyGridState.animateScrollToItem(0)
            }
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    val headerContent = @Composable {
        Card(
            colors = CardDefaults.cardColors(containerColor = ArmoryColors.Silver5.copy(alpha = 0.5f)),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .border(1.dp, ArmoryColors.Silver4.copy(alpha = 0.3f), MaterialTheme.shapes.large),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                SortHeader(
                    sortType = sortType,
                    sortDescending = sortDescending,
                    onSortTypeChange = onSortTypeChange,
                    onSortDescendingChange = onSortDescendingChange,
                    sortTypeText = { sortType ->
                        when (sortType) {
                            AlbumSortType.CREATE_DATE -> R.string.sort_by_create_date
                            AlbumSortType.NAME -> R.string.sort_by_name
                            AlbumSortType.ARTIST -> R.string.sort_by_artist
                            AlbumSortType.YEAR -> R.string.sort_by_year
                            AlbumSortType.SONG_COUNT -> R.string.sort_by_song_count
                            AlbumSortType.LENGTH -> R.string.sort_by_length
                            AlbumSortType.PLAY_TIME -> R.string.sort_by_play_time
                        }
                    },
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = pluralStringResource(R.plurals.n_album, albums.size, albums.size).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = ArmoryColors.Silver3,
                    letterSpacing = 1.sp
                )
                IconButton(
                    onClick = { viewType = viewType.toggle() },
                    modifier = Modifier.padding(start = 6.dp),
                ) {
                    Icon(
                        painter = painterResource(
                            if (viewType == LibraryViewType.LIST) R.drawable.list else R.drawable.grid_view
                        ),
                        contentDescription = null,
                        tint = ArmoryColors.Gold3
                    )
                }
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().pullToRefresh(
            state = pullRefreshState,
            isRefreshing = isRefreshing,
            onRefresh = { if (ytmSync) viewModel.refresh(filter) }
        ),
    ) {
        when (viewType) {
            LibraryViewType.LIST -> LazyColumn(
                state = lazyListState,
                contentPadding = PaddingValues(bottom = 120.dp),
            ) {
                item(key = "filter", contentType = CONTENT_TYPE_HEADER) { filterContent() }
                item(key = "header", contentType = CONTENT_TYPE_HEADER) { headerContent() }
                val filteredAlbums = if (hideExplicit) albums.filter { !it.album.explicit } else albums
                if (filteredAlbums.isEmpty()) {
                    item {
                        EmptyPlaceholder(
                            icon = R.drawable.album,
                            text = stringResource(R.string.library_album_empty),
                            modifier = Modifier.animateItem()
                        )
                    }
                }
                items(items = filteredAlbums.distinctBy { it.id }, key = { it.id }, contentType = { CONTENT_TYPE_ALBUM }) { album ->
                    LibraryAlbumListItem(
                        navController = navController,
                        menuState = menuState,
                        album = album,
                        isActive = album.id == mediaMetadata?.album?.id,
                        isPlaying = isPlaying,
                        modifier = Modifier.animateItem()
                    )
                }
            }
            LibraryViewType.GRID -> LazyVerticalGrid(
                state = lazyGridState,
                columns = GridCells.Adaptive(minSize = GridThumbnailHeight + if (gridItemSize == GridItemSize.BIG) 24.dp else (-24).dp),
                contentPadding = PaddingValues(bottom = 120.dp),
            ) {
                item(key = "filter", span = { GridItemSpan(maxLineSpan) }, contentType = CONTENT_TYPE_HEADER) { filterContent() }
                item(key = "header", span = { GridItemSpan(maxLineSpan) }, contentType = CONTENT_TYPE_HEADER) { headerContent() }
                val filteredAlbums = if (hideExplicit) albums.filter { !it.album.explicit } else albums
                if (filteredAlbums.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyPlaceholder(
                            icon = R.drawable.album,
                            text = stringResource(R.string.library_album_empty),
                            modifier = Modifier.animateItem()
                        )
                    }
                }
                items(items = filteredAlbums.distinctBy { it.id }, key = { it.id }, contentType = { CONTENT_TYPE_ALBUM }) { album ->
                    LibraryAlbumGridItem(
                        navController = navController,
                        menuState = menuState,
                        coroutineScope = coroutineScope,
                        album = album,
                        isActive = album.id == mediaMetadata?.album?.id,
                        isPlaying = isPlaying,
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
        PullToRefreshDefaults.Indicator(
            isRefreshing = isRefreshing,
            state = pullRefreshState,
            containerColor = ArmoryColors.Silver5,
            color = ArmoryColors.Gold3,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
        )
    }
}
