/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.ruben.spotitube.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.ruben.spotitube.LocalPlayerAwareWindowInsets
import com.ruben.spotitube.LocalPlayerConnection
import com.ruben.spotitube.ArmoryTopBar
import com.ruben.spotitube.ArmoryIcon
import com.ruben.spotitube.SectionHeader
import com.ruben.spotitube.R
import com.ruben.spotitube.constants.GridThumbnailHeight
import com.ruben.spotitube.ui.component.LocalMenuState
import com.ruben.spotitube.ui.component.YouTubeGridItem
import com.ruben.spotitube.ui.menu.YouTubeAlbumMenu
import com.ruben.spotitube.viewmodels.NewReleaseContent
import com.ruben.spotitube.viewmodels.NewReleaseUiState
import com.ruben.spotitube.viewmodels.NewReleaseViewModel

@Composable
fun NewReleaseScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: NewReleaseViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val content by viewModel.content.collectAsStateWithLifecycle()

    val coroutineScope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentWindowInsets = LocalPlayerAwareWindowInsets.current,
        topBar = {
            ArmoryTopBar(
                title = stringResource(R.string.new_releases),
                onBack = navController::navigateUp,
                scrollBehavior = scrollBehavior
            )
        },
    ) { innerPadding ->
        AnimatedContent(
            targetState = uiState,
            transitionSpec = {
                fadeIn(tween(300)) togetherWith fadeOut(tween(150))
            },
            modifier = Modifier.fillMaxSize(),
            label = "NewReleaseContent",
        ) { state ->
            when (state) {
                NewReleaseUiState.Loading -> {
                    NewReleaseLoadingState(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    )
                }

                is NewReleaseUiState.Error -> {
                    NewReleaseErrorState(
                        message = state.message,
                        onRetry = viewModel::retry,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    )
                }

                NewReleaseUiState.Empty -> {
                    NewReleaseEmptyState(
                        onRefresh = viewModel::retry,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    )
                }

                is NewReleaseUiState.Success -> {
                    NewReleaseCategoriesContent(
                        content = state.content,
                        activeAlbumId = mediaMetadata?.album?.id,
                        isPlaying = isPlaying,
                        coroutineScope = coroutineScope,
                        paddingValues = innerPadding,
                        onAlbumClick = { album ->
                            navController.navigate("album/${album.id}")
                        },
                        onAlbumLongClick = { album ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuState.show {
                                YouTubeAlbumMenu(
                                    albumItem = album,
                                    navController = navController,
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

@Immutable
private enum class NewReleaseCategory(
    val titleRes: Int,
    val contentType: String,
) {
    Albums(
        titleRes = R.string.albums,
        contentType = "new_release_album_grid_item",
    ),
    Singles(
        titleRes = R.string.singles,
        contentType = "new_release_single_grid_item",
    ),
    Ep(
        titleRes = R.string.ep,
        contentType = "new_release_ep_grid_item",
    ),
}

@Immutable
private data class NewReleaseSection(
    val category: NewReleaseCategory,
    val releases: List<com.ruben.spotitube.innertube.models.AlbumItem>,
)

@Composable
private fun NewReleaseCategoriesContent(
    content: NewReleaseContent,
    activeAlbumId: String?,
    isPlaying: Boolean,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    paddingValues: PaddingValues,
    onAlbumClick: (com.ruben.spotitube.innertube.models.AlbumItem) -> Unit,
    onAlbumLongClick: (com.ruben.spotitube.innertube.models.AlbumItem) -> Unit,
) {
    val sections = remember(content) {
        buildList {
            if (content.albums.isNotEmpty()) {
                add(NewReleaseSection(NewReleaseCategory.Albums, content.albums))
            }
            if (content.singles.isNotEmpty()) {
                add(NewReleaseSection(NewReleaseCategory.Singles, content.singles))
            }
            if (content.eps.isNotEmpty()) {
                add(NewReleaseSection(NewReleaseCategory.Ep, content.eps))
            }
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = GridThumbnailHeight + 24.dp),
        contentPadding = paddingValues,
        modifier = Modifier.fillMaxSize(),
    ) {
        sections.forEach { section ->
            item(
                key = "new_release_section_header_${section.category.name}",
                span = { GridItemSpan(maxLineSpan) },
                contentType = "new_release_section_header",
            ) {
                NewReleaseSectionHeader(
                    title = stringResource(section.category.titleRes),
                    count = section.releases.size,
                )
            }

            item(
                key = "new_release_section_${section.category.name}",
                span = { GridItemSpan(maxLineSpan) },
                contentType = "new_release_horizontal_section",
            ) {
                NewReleaseHorizontalSection(
                    releases = section.releases,
                    contentType = section.category.contentType,
                    activeAlbumId = activeAlbumId,
                    isPlaying = isPlaying,
                    coroutineScope = coroutineScope,
                    onAlbumClick = onAlbumClick,
                    onAlbumLongClick = onAlbumLongClick,
                )
            }
        }
    }
}

@Composable
private fun NewReleaseSectionHeader(
    title: String,
    count: Int,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 4.dp),
    ) {
        SectionHeader(text = title)
        Text(
            text = stringResource(R.string.n_releases, count),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun NewReleaseHorizontalSection(
    releases: List<com.ruben.spotitube.innertube.models.AlbumItem>,
    contentType: String,
    activeAlbumId: String?,
    isPlaying: Boolean,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onAlbumClick: (com.ruben.spotitube.innertube.models.AlbumItem) -> Unit,
    onAlbumLongClick: (com.ruben.spotitube.innertube.models.AlbumItem) -> Unit,
) {
    // Siempre usar carrusel, incluso con 1 item
    val carouselState = rememberCarouselState { releases.size }

    HorizontalMultiBrowseCarousel(
        state = carouselState,
        preferredItemWidth = 180.dp,
        itemSpacing = 12.dp,
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(216.dp),
    ) { index ->
        val album = releases[index]
        YouTubeGridItem(
            item = album,
            isActive = activeAlbumId == album.id,
            isPlaying = isPlaying,
            fillMaxWidth = false,  // Importante: false para que respete el ancho del carrusel
            coroutineScope = coroutineScope,
            modifier = Modifier
                .fillMaxSize()  // fillMaxSize para que ocupe todo el espacio del carrusel
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(
                    onClick = { onAlbumClick(album) },
                    onLongClick = { onAlbumLongClick(album) },
                ),
        )
    }
}

@Composable
private fun NewReleaseLoadingState(
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.padding(24.dp),
    ) {
        ElevatedCard(
            shape = MaterialTheme.shapes.extraLarge,
            colors = MaterialTheme.colorScheme.surfaceContainerHigh.let {
                androidx.compose.material3.CardDefaults.elevatedCardColors(containerColor = it)
            },
            elevation = androidx.compose.material3.CardDefaults.elevatedCardElevation(
                defaultElevation = 6.dp
            ),
        ) {
            Column(
                modifier = Modifier.padding(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                CircularWavyProgressIndicator(
                    modifier = Modifier.size(72.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
                Text(
                    text = stringResource(R.string.loading_new_releases),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun NewReleaseErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.padding(24.dp),
    ) {
        ElevatedCard(
            shape = MaterialTheme.shapes.extraLarge,
            colors = MaterialTheme.colorScheme.surfaceContainerHigh.let {
                androidx.compose.material3.CardDefaults.elevatedCardColors(containerColor = it)
            },
            elevation = androidx.compose.material3.CardDefaults.elevatedCardElevation(
                defaultElevation = 6.dp
            ),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp),
        ) {
            Column(
                modifier = Modifier.padding(40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(88.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        ArmoryIcon(
                            painter = painterResource(R.drawable.error),
                            contentDescription = null,
                            modifier = Modifier.size(44.dp),
                            active = true
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.new_releases_error_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                )

                Text(
                    text = stringResource(R.string.new_releases_error_desc),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )

                if (message.isNotBlank()) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                ElevatedButton(
                    onClick = onRetry,
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.retry),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun NewReleaseEmptyState(
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.padding(24.dp),
    ) {
        ElevatedCard(
            shape = MaterialTheme.shapes.extraLarge,
            colors = MaterialTheme.colorScheme.surfaceContainerHigh.let {
                androidx.compose.material3.CardDefaults.elevatedCardColors(containerColor = it)
            },
            elevation = androidx.compose.material3.CardDefaults.elevatedCardElevation(
                defaultElevation = 6.dp
            ),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp),
        ) {
            Column(
                modifier = Modifier.padding(40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(88.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        ArmoryIcon(
                            painter = painterResource(R.drawable.album),
                            contentDescription = null,
                            modifier = Modifier.size(44.dp),
                            active = true
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.no_releases_found),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                )

                Text(
                    text = stringResource(R.string.new_releases_empty_desc),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(16.dp))

                ElevatedButton(
                    onClick = onRefresh,
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.refresh),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}