/*
 * OpenTune Project - Music Player (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ruben.spotitube.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.ruben.spotitube.LocalPlayerAwareWindowInsets
import com.ruben.spotitube.LocalPlayerConnection
import com.ruben.spotitube.OpenTuneColors
import com.ruben.spotitube.OpenTuneHeader
import com.ruben.spotitube.constants.DisableBlurKey
import com.ruben.spotitube.constants.InnerTubeCookieKey
import com.ruben.spotitube.constants.ShowHomeCategoryChipsKey
import com.ruben.spotitube.innertube.utils.parseCookieString
import com.google.firebase.auth.FirebaseAuth
import com.ruben.spotitube.ui.component.LocalBottomSheetPageState
import com.ruben.spotitube.ui.component.LocalMenuState
import com.ruben.spotitube.ui.component.NavigationTitle
import com.ruben.spotitube.ui.utils.SnapLayoutInfoProvider
import com.ruben.spotitube.utils.rememberPreference
import com.ruben.spotitube.viewmodels.HomeViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    onVisible: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) {
        onVisible()
    }
    val menuState = LocalMenuState.current
    val bottomSheetPageState = LocalBottomSheetPageState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val haptic = LocalHapticFeedback.current

    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val quickPicks by viewModel.quickPicks.collectAsState()
    val speedDialSongs by viewModel.speedDialSongs.collectAsState()
    val forgottenFavorites by viewModel.forgottenFavorites.collectAsState()
    val keepListening by viewModel.keepListening.collectAsState()
    val homePage by viewModel.homePage.collectAsState()
    val allItemsMetadata by viewModel.allItemsMetadata.collectAsState()

    val selectedChip by viewModel.selectedChip.collectAsState()

    val isLoading: Boolean by viewModel.isLoading.collectAsState()
    val isInitialLoadComplete by viewModel.isInitialLoadComplete.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()

    LaunchedEffect(isInitialLoadComplete) {
        if (isInitialLoadComplete) {
            onVisible()
        }
    }

    val forgottenFavoritesLazyGridState = rememberLazyGridState()

    val accountName by viewModel.accountName.collectAsState()
    val accountImageUrl by viewModel.accountImageUrl.collectAsState()
    val innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    val (disableBlur) = rememberPreference(DisableBlurKey, true)
    val (showHomeCategoryChips) = rememberPreference(ShowHomeCategoryChipsKey, true)
    val isLoggedIn = remember(innerTubeCookie) {
        "SAPISID" in parseCookieString(innerTubeCookie)
    }
    val currentUser = remember { FirebaseAuth.getInstance().currentUser }
    val url = if (isLoggedIn && !accountImageUrl.isNullOrEmpty()) accountImageUrl else currentUser?.photoUrl?.toString()

    val scope = rememberCoroutineScope()
    val lazylistState = rememberLazyListState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop =
        backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsState()

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            lazylistState.animateScrollToItem(0)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { lazylistState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisibleIndex ->
                val len = lazylistState.layoutInfo.totalItemsCount
                if (lastVisibleIndex != null && lastVisibleIndex >= len - 3) {
                    viewModel.loadMoreYouTubeItems(homePage?.continuation)
                }
            }
    }

    if (selectedChip != null) {
        BackHandler {
            // if a chip is selected, go back to the normal homepage first
            viewModel.toggleChip(selectedChip)
        }
    }

    LaunchedEffect(showHomeCategoryChips, selectedChip) {
        if (!showHomeCategoryChips && selectedChip != null) {
            viewModel.toggleChip(selectedChip)
        }
    }

    LaunchedEffect(forgottenFavorites) {
        forgottenFavoritesLazyGridState.scrollToItem(0)
    }

    // Capture M3 Expressive colors from theme outside drawBehind
    val color1 = MaterialTheme.colorScheme.primary
    val color2 = MaterialTheme.colorScheme.secondary
    val color3 = MaterialTheme.colorScheme.tertiary
    val color4 = MaterialTheme.colorScheme.primaryContainer
    val color5 = MaterialTheme.colorScheme.secondaryContainer
    val surfaceColor = MaterialTheme.colorScheme.surface

    // CONTENEDOR PLANO ABSOLUTO PARA OpenTune
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OpenTuneColors.AbsoluteBlack)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            OpenTuneHeader(
                avatarUrl = url,
                onSettings = { navController.navigate("account") }
            )

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .pullToRefresh(
                        state = pullRefreshState,
                        isRefreshing = isRefreshing,
                        onRefresh = viewModel::refresh
                    )
            ) {
                val horizontalLazyGridItemWidthFactor = if (maxWidth * 0.475f >= 320.dp) 0.475f else 0.9f
                val horizontalLazyGridItemWidth = maxWidth * horizontalLazyGridItemWidthFactor
                
                // Optimizamos el SnapLayoutInfoProvider envolviéndolo en remember con dependencias precisas
                // para evitar cómputos pesados redundantes que jankean el Main Thread en cada frame de scroll.
                val forgottenFavoritesSnapLayoutInfoProvider = remember(forgottenFavoritesLazyGridState, horizontalLazyGridItemWidthFactor) {
                    SnapLayoutInfoProvider(
                        lazyGridState = forgottenFavoritesLazyGridState,
                        positionInLayout = { layoutSize, itemSize ->
                            (layoutSize * horizontalLazyGridItemWidthFactor / 2f - itemSize / 2f)
                        }
                    )
                }

                LazyColumn(
                    state = lazylistState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 90.dp)
                ) {
                    // 1. Selecciones rápidas (Quick Picks)
                    item {
                        NavigationTitle(
                            title = "Selecciones rápidas",
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        if (quickPicks.isNullOrEmpty()) {
                            HomeLoadingShimmer()
                        } else {
                            QuickPicksSection(
                                quickPicks = quickPicks!!,
                                mediaMetadata = mediaMetadata,
                                isPlaying = isPlaying,
                                navController = navController,
                                playerConnection = playerConnection,
                                menuState = menuState,
                                haptic = haptic,
                                metadataMap = allItemsMetadata
                            )
                        }
                    }

                    // 2. Marcado rápido (Speed Dial)
                    item {
                        NavigationTitle(
                            title = "Marcado rápido",
                            modifier = Modifier.padding(top = 24.dp)
                        )
                        if (speedDialSongs.isEmpty()) {
                             HomeLoadingShimmer()
                        } else {
                            SpeedDialSection(
                                speedDialSongs = speedDialSongs,
                                mediaMetadata = mediaMetadata,
                                isPlaying = isPlaying,
                                navController = navController,
                                playerConnection = playerConnection,
                                menuState = menuState,
                                haptic = haptic,
                                metadataMap = allItemsMetadata
                            )
                        }
                    }

                    // 3. Sigue escuchando (Keep Listening)
                    item {
                        NavigationTitle(
                            title = "Sigue escuchando",
                            modifier = Modifier.padding(top = 24.dp)
                        )
                        if (keepListening.isNullOrEmpty()) {
                             HomeLoadingShimmer()
                        } else {
                            KeepListeningSection(
                                keepListening = keepListening!!,
                                mediaMetadata = mediaMetadata,
                                isPlaying = isPlaying,
                                navController = navController,
                                playerConnection = playerConnection,
                                menuState = menuState,
                                haptic = haptic,
                                scope = scope,
                                metadataMap = allItemsMetadata
                            )
                        }
                    }

                    // 4. Tus favoritos del pasado (Forgotten Favorites)
                    item {
                        NavigationTitle(
                            title = "Tus favoritos del pasado",
                            modifier = Modifier.padding(top = 24.dp)
                        )
                        if (forgottenFavorites.isNullOrEmpty()) {
                            HomeLoadingShimmer()
                        } else {
                            ForgottenFavoritesSection(
                                forgottenFavorites = forgottenFavorites!!,
                                mediaMetadata = mediaMetadata,
                                isPlaying = isPlaying,
                                horizontalLazyGridItemWidth = horizontalLazyGridItemWidth,
                                lazyGridState = forgottenFavoritesLazyGridState,
                                snapLayoutInfoProvider = forgottenFavoritesSnapLayoutInfoProvider,
                                navController = navController,
                                playerConnection = playerConnection,
                                menuState = menuState,
                                haptic = haptic,
                                metadataMap = allItemsMetadata
                            )
                        }
                    }

                    // 6. Recomendaciones Similares (Similar to...)
                    SimilarRecommendationsContainer(
                        viewModel = viewModel,
                        mediaMetadata = mediaMetadata,
                        isPlaying = isPlaying,
                        navController = navController,
                        playerConnection = playerConnection,
                        menuState = menuState,
                        haptic = haptic,
                        scope = scope
                    )
                }

                Indicator(
                    isRefreshing = isRefreshing,
                    state = pullRefreshState,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues()),
                )
            }
        }
    }
}
