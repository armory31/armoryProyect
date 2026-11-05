/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ruben.spotitube.ui.screens.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.ruben.spotitube.R
import com.ruben.spotitube.constants.ArtistFilter
import com.ruben.spotitube.constants.ArtistFilterKey
import com.ruben.spotitube.constants.ArtistSortDescendingKey
import com.ruben.spotitube.constants.ArtistSortType
import com.ruben.spotitube.constants.ArtistSortTypeKey
import com.ruben.spotitube.constants.ArtistViewTypeKey
import com.ruben.spotitube.constants.CONTENT_TYPE_HEADER
import com.ruben.spotitube.constants.GridItemSize
import com.ruben.spotitube.constants.GridItemsSizeKey
import com.ruben.spotitube.constants.GridThumbnailHeight
import com.ruben.spotitube.constants.LibraryViewType
import com.ruben.spotitube.ui.component.EmptyPlaceholder
import com.ruben.spotitube.ui.component.LibraryArtistGridItem
import com.ruben.spotitube.ui.component.LibraryArtistListItem
import com.ruben.spotitube.ui.component.LocalMenuState
import com.ruben.spotitube.ui.component.SortHeader
import com.ruben.spotitube.utils.rememberEnumPreference
import com.ruben.spotitube.utils.rememberPreference
import com.ruben.spotitube.viewmodels.LibraryArtistsViewModel

@Composable
fun LibraryArtistsScreen(
    navController: NavController,
    onDeselect: () -> Unit,
    viewModel: LibraryArtistsViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    var viewType by rememberEnumPreference(ArtistViewTypeKey, LibraryViewType.GRID)

    var filter by rememberEnumPreference(ArtistFilterKey, ArtistFilter.SAVED)
    val (sortType, onSortTypeChange) = rememberEnumPreference(
        ArtistSortTypeKey,
        ArtistSortType.CREATE_DATE
    )
    val (sortDescending, onSortDescendingChange) = rememberPreference(ArtistSortDescendingKey, true)
    val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)

    val filterContent = @Composable {
        LibraryFilterChips(
            filters = listOf(
                ArtistFilter.SAVED to stringResource(R.string.filter_liked),
                ArtistFilter.LIBRARY to stringResource(R.string.filter_library)
            ),
            selectedFilter = filter,
            onFilterSelected = { filter = it },
            modifier = Modifier.padding(vertical = 4.dp)
        )
    }

    val artists by viewModel.allArtists.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    val lazyGridState = rememberLazyGridState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop = backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsStateWithLifecycle()

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
        LibrarySortHeaderCard {
            SortHeader(
                sortType = sortType,
                sortDescending = sortDescending,
                onSortTypeChange = onSortTypeChange,
                onSortDescendingChange = onSortDescendingChange,
                sortTypeText = { sortType ->
                    when (sortType) {
                        ArtistSortType.CREATE_DATE -> R.string.sort_by_create_date
                        ArtistSortType.NAME -> R.string.sort_by_name
                        ArtistSortType.SONG_COUNT -> R.string.sort_by_song_count
                        ArtistSortType.PLAY_TIME -> R.string.sort_by_play_time
                    }
                },
            )
            Spacer(Modifier.weight(1f))
            artists.let { artistsList ->
                Text(
                    text = pluralStringResource(R.plurals.n_artist, artistsList.size, artistsList.size).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = ArmoryColors.Silver3,
                    letterSpacing = 1.sp
                )
            }
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

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        when (viewType) {
            LibraryViewType.LIST -> LazyColumn(
                state = lazyListState,
                contentPadding = PaddingValues(bottom = 120.dp),
            ) {
                item(key = "filter", contentType = CONTENT_TYPE_HEADER) { filterContent() }
                item(key = "header", contentType = CONTENT_TYPE_HEADER) { headerContent() }
                artists.let { artistsList ->
                    if (artistsList.isEmpty()) {
                        item {
                            EmptyPlaceholder(
                                icon = R.drawable.artist,
                                text = stringResource(R.string.library_artist_empty),
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                    items(
                        items = artistsList.distinctBy { it.id },
                        key = { it.id },
                        contentType = { "artist_list_item" }
                    ) { artist ->
                        LibraryArtistListItem(
                            navController = navController,
                            menuState = menuState,
                            coroutineScope = coroutineScope,
                            modifier = Modifier.animateItem(),
                            artist = artist
                        )
                    }
                }
            }
            LibraryViewType.GRID -> LazyVerticalGrid(
                state = lazyGridState,
                columns = GridCells.Adaptive(minSize = GridThumbnailHeight + if (gridItemSize == GridItemSize.BIG) 24.dp else (-24).dp),
                contentPadding = PaddingValues(bottom = 120.dp),
            ) {
                item(key = "filter", span = { GridItemSpan(maxLineSpan) }, contentType = CONTENT_TYPE_HEADER) { filterContent() }
                item(key = "header", span = { GridItemSpan(maxLineSpan) }, contentType = CONTENT_TYPE_HEADER) { headerContent() }
                artists.let { artistsList ->
                    if (artistsList.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            EmptyPlaceholder(
                                icon = R.drawable.artist,
                                text = stringResource(R.string.library_artist_empty),
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                    items(
                        items = artistsList.distinctBy { it.id },
                        key = { it.id },
                        contentType = { "artist_grid_item" }
                    ) { artist ->
                        LibraryArtistGridItem(
                            navController = navController,
                            menuState = menuState,
                            coroutineScope = coroutineScope,
                            modifier = Modifier.animateItem(),
                            artist = artist
                        )
                    }
                }
            }
        }
    }
}
