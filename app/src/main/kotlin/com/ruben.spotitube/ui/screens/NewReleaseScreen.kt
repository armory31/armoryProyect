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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ruben.spotitube.ArmoryIcon
import com.ruben.spotitube.ArmoryTopBar
import com.ruben.spotitube.LocalPlayerAwareWindowInsets
import com.ruben.spotitube.LocalPlayerConnection
import com.ruben.spotitube.R
import com.ruben.spotitube.SectionHeader
import com.ruben.spotitube.constants.getGroupsSummaryText
import com.ruben.spotitube.innertube.models.AlbumItem
import com.ruben.spotitube.ui.component.LocalMenuState
import com.ruben.spotitube.ui.component.RegionGroupsEditorDialog
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
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current
    var showGroupsDialog by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    if (showGroupsDialog) {
        RegionGroupsEditorDialog(
            groups = groups,
            onConfirm = { newGroups ->
                viewModel.setGroups(newGroups)
                showGroupsDialog = false
            },
            onDismiss = { showGroupsDialog = false }
        )
    }

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = innerPadding.calculateTopPadding(),
                    start = innerPadding.calculateStartPadding(layoutDirection),
                    end = innerPadding.calculateEndPadding(layoutDirection)
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val summaryText = remember(groups, context) {
                    getGroupsSummaryText(context, groups)
                }
                FilterChip(
                    selected = true,
                    onClick = { showGroupsDialog = true },
                    label = {
                        Text(
                            text = summaryText,
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.language),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    trailingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.expand_more),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }

            AnimatedContent(
                targetState = uiState,
                transitionSpec = {
                    fadeIn(tween(300)) togetherWith fadeOut(tween(150))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                label = "NewReleaseContent",
            ) { state ->
                when (state) {
                    NewReleaseUiState.Loading -> {
                        NewReleaseLoadingState(
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    is NewReleaseUiState.Error -> {
                        NewReleaseErrorState(
                            message = state.message,
                            onRetry = viewModel::retry,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    NewReleaseUiState.Empty -> {
                        NewReleaseEmptyState(
                            onRefresh = viewModel::retry,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    is NewReleaseUiState.Success -> {
                        NewReleaseGroupsContent(
                            content = state.content,
                            activeAlbumId = mediaMetadata?.album?.id,
                            isPlaying = isPlaying,
                            coroutineScope = coroutineScope,
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NewReleaseGroupsContent(
    content: NewReleaseContent,
    activeAlbumId: String?,
    isPlaying: Boolean,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onAlbumClick: (AlbumItem) -> Unit,
    onAlbumLongClick: (AlbumItem) -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val playerAwarePadding = LocalPlayerAwareWindowInsets.current
        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
        .asPaddingValues()

    val contentPadding = PaddingValues(
        start = playerAwarePadding.calculateStartPadding(layoutDirection),
        top = playerAwarePadding.calculateTopPadding(),
        end = playerAwarePadding.calculateEndPadding(layoutDirection),
        bottom = playerAwarePadding.calculateBottomPadding() + 16.dp
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding
    ) {
        items(
            items = content.sections,
            key = { it.group.id }
        ) { section ->
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                SectionHeader(
                    text = section.group.name,
                    modifier = Modifier.padding(top = 12.dp)
                )

                val categories = remember(section) {
                    buildList {
                        if (section.albums.isNotEmpty()) {
                            add(R.string.albums to section.albums)
                        }
                        if (section.singles.isNotEmpty()) {
                            add(R.string.singles to section.singles)
                        }
                        if (section.eps.isNotEmpty()) {
                            add(R.string.ep to section.eps)
                        }
                    }
                }

                categories.forEach { (titleRes, items) ->
                    Text(
                        text = stringResource(titleRes),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp)
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        items(
                            items = items.distinctBy { it.id },
                            key = { it.id }
                        ) { album ->
                            YouTubeGridItem(
                                item = album,
                                isActive = activeAlbumId == album.id,
                                isPlaying = isPlaying,
                                fillMaxWidth = false,
                                coroutineScope = coroutineScope,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .combinedClickable(
                                        onClick = { onAlbumClick(album) },
                                        onLongClick = { onAlbumLongClick(album) },
                                    ),
                            )
                        }
                    }
                }
            }
        }
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
