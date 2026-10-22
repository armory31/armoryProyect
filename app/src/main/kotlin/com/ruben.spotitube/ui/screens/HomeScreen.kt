/*
 * OpenTune Project - Music Player (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ruben.spotitube.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.ruben.spotitube.LocalPlayerAwareWindowInsets
import com.ruben.spotitube.LocalPlayerConnection
import com.ruben.spotitube.ArmoryColors
import com.ruben.spotitube.ArmoryGradients
import com.ruben.spotitube.ArmoryHeader
import com.ruben.spotitube.rememberAvatarUrl
import com.ruben.spotitube.constants.InnerTubeCookieKey
import com.ruben.spotitube.innertube.utils.parseCookieString
import com.ruben.spotitube.ui.component.LocalMenuState
import com.ruben.spotitube.ui.component.NavigationTitle
import com.ruben.spotitube.ui.utils.SnapLayoutInfoProvider
import com.ruben.spotitube.utils.rememberPreference
import com.ruben.spotitube.viewmodels.HomeViewModel

@OptIn(ExperimentalFoundationApi::class)
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
    val playerConnection = LocalPlayerConnection.current ?: return
    val haptic = LocalHapticFeedback.current

    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val quickPicks by viewModel.quickPicks.collectAsStateWithLifecycle()
    val speedDialItems by viewModel.speedDialItems.collectAsStateWithLifecycle()
    val forgottenFavorites by viewModel.forgottenFavorites.collectAsStateWithLifecycle()
    val forgottenFavoritesDaysRemaining by viewModel.forgottenFavoritesDaysRemaining.collectAsStateWithLifecycle()
    val keepListening by viewModel.keepListening.collectAsStateWithLifecycle()
    val homePage by viewModel.homePage.collectAsStateWithLifecycle()
    val allItemsMetadata by viewModel.allItemsMetadata.collectAsStateWithLifecycle()

    val isInitialLoadComplete by viewModel.isInitialLoadComplete.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val pullRefreshState = rememberPullToRefreshState()

    LaunchedEffect(isInitialLoadComplete) {
        if (isInitialLoadComplete) {
            onVisible()
        }
    }

    val forgottenFavoritesLazyGridState = rememberLazyGridState()

    val accountImageUrl by viewModel.accountImageUrl.collectAsStateWithLifecycle()
    val innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn = remember(innerTubeCookie) {
        "SAPISID" in parseCookieString(innerTubeCookie)
    }
    val savedAvatarUrl = rememberAvatarUrl()
    val url = savedAvatarUrl ?: if (isLoggedIn && !accountImageUrl.isNullOrEmpty()) accountImageUrl else null

    val scope = rememberCoroutineScope()
    val lazylistState = rememberLazyListState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop =
        backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsStateWithLifecycle()

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

    LaunchedEffect(forgottenFavorites) {
        forgottenFavoritesLazyGridState.scrollToItem(0)
    }

    // CONTENEDOR PLANO ABSOLUTO PARA ARMORY
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ArmoryColors.AbsoluteBlack)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ArmoryHeader(
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
                    item(
                        key = "quick_picks",
                        contentType = "section"
                    ) {
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
                    item(
                        key = "speed_dial",
                        contentType = "section"
                    ) {
                        NavigationTitle(
                            title = "Marcado rápido",
                            modifier = Modifier.padding(top = 24.dp)
                        )
                        if (speedDialItems.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                    .border(BorderStroke(1.5.dp, ArmoryGradients.MetallicCopper), RoundedCornerShape(16.dp))
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No hay elementos marcados. Usa el menú de cualquier canción, álbum o playlist para añadirlo a Marcado rápido.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            SpeedDialSection(
                                speedDialItems = speedDialItems,
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
                    item(
                        key = "keep_listening",
                        contentType = "section"
                    ) {
                        NavigationTitle(
                            title = "Sigue escuchando",
                            modifier = Modifier.padding(top = 24.dp)
                        )
                        if (keepListening.isNullOrEmpty()) {
                             HomeLoadingShimmer()
                        } else {
                            KeepListeningSection(
                                keepListening = keepListening!!.take(4),
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
                    item(
                        key = "forgotten_favorites",
                        contentType = "section"
                    ) {
                        NavigationTitle(
                            title = "Tus favoritos del pasado",
                            modifier = Modifier.padding(top = 24.dp)
                        )
                        if (forgottenFavorites.isNullOrEmpty()) {
                            val remaining = forgottenFavoritesDaysRemaining ?: 30
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                    .border(BorderStroke(1.5.dp, ArmoryGradients.MetallicCopper), RoundedCornerShape(16.dp))
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "⏳ Desbloqueando en $remaining días",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = ArmoryColors.Gold2,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = "Se requieren 30 días de historial para calcular tus favoritos del pasado.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
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
                    similarRecommendationsContainer(
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
