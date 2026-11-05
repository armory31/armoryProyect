/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ruben.spotitube.ui.screens
 
 import androidx.compose.foundation.combinedClickable
 import androidx.compose.foundation.layout.asPaddingValues
 import androidx.compose.foundation.lazy.grid.GridCells
 import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
 import androidx.compose.foundation.lazy.grid.items
 import com.ruben.spotitube.ArmoryTopBar
 import androidx.compose.material3.TopAppBarScrollBehavior
 import androidx.compose.runtime.Composable
 import androidx.lifecycle.compose.collectAsStateWithLifecycle
 import androidx.compose.runtime.getValue
 import androidx.compose.runtime.rememberCoroutineScope
 import androidx.compose.ui.Modifier
 import androidx.compose.ui.unit.dp
 import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
 import androidx.navigation.NavController
 import com.ruben.spotitube.LocalPlayerAwareWindowInsets
 import com.ruben.spotitube.LocalPlayerConnection
 import com.ruben.spotitube.constants.GridThumbnailHeight
 import com.ruben.spotitube.innertube.models.AlbumItem
 import com.ruben.spotitube.innertube.models.ArtistItem
 import com.ruben.spotitube.innertube.models.PlaylistItem
 import com.ruben.spotitube.ui.component.LocalMenuState
 import com.ruben.spotitube.ui.component.YouTubeGridItem
 import com.ruben.spotitube.ui.component.shimmer.GridItemPlaceHolder
 import com.ruben.spotitube.ui.component.shimmer.ShimmerHost
 import com.ruben.spotitube.ui.menu.YouTubeAlbumMenu
 import com.ruben.spotitube.ui.menu.YouTubeArtistMenu
 import com.ruben.spotitube.ui.menu.YouTubePlaylistMenu
 import com.ruben.spotitube.viewmodels.BrowseViewModel

@Composable
 fun BrowseScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    browseId: String?,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
     val menuState = LocalMenuState.current
     val playerConnection = LocalPlayerConnection.current ?: return
     val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
     val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
 
     val title by viewModel.title.collectAsStateWithLifecycle()
     val items by viewModel.items.collectAsStateWithLifecycle()
 
     val coroutineScope = rememberCoroutineScope()
 
     LazyVerticalGrid(
         columns = GridCells.Adaptive(minSize = GridThumbnailHeight + 24.dp),
         contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
     ) {
         items?.let { items ->
             items(
                 items = items.distinctBy { it.id },
                 key = { it.id }
             ) { item ->
                 YouTubeGridItem(
                     item = item,
                     isPlaying = isPlaying,
                     fillMaxWidth = true,
                     coroutineScope = coroutineScope,
                     modifier = Modifier
                         .combinedClickable(
                             onClick = {
                                 when (item) {
                                     is AlbumItem -> navController.navigate("album/${item.id}")
                                     is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
                                     is ArtistItem -> navController.navigate("artist/${item.id}")
                                     else -> {
                                         // Do nothing
                                     }
                                 }
                             },
                             onLongClick = {
                                 menuState.show {
                                     when (item) {
                                         is AlbumItem ->
                                             YouTubeAlbumMenu(
                                                 albumItem = item,
                                                 navController = navController,
                                                 onDismiss = menuState::dismiss
                                             )
 
                                         is PlaylistItem -> {
                                             YouTubePlaylistMenu(
                                                 playlist = item,
                                                 coroutineScope = coroutineScope,
                                                 onDismiss = menuState::dismiss
                                             )
                                         }
 
                                         is ArtistItem -> {
                                             YouTubeArtistMenu(
                                                 artist = item,
                                                 onDismiss = menuState::dismiss
                                             )
                                         }
 
                                         else -> {
                                             // Do nothing
                                         }
                                     }
                                 }
                             }
                         )
                 )
             }
 
             if (items.isEmpty()) {
                 items(8) {
                     ShimmerHost {
                         GridItemPlaceHolder(fillMaxWidth = true)
                     }
                 }
             }
         }
     }
 
     ArmoryTopBar(
         title = title.orEmpty(),
         onBack = navController::navigateUp,
         scrollBehavior = scrollBehavior
     )
 }
