/*
 * OpenTune Project - Music Player (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ruben.spotitube.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.ruben.spotitube.ui.LocalPlayerAwareWindowInsets
import com.ruben.spotitube.ui.LocalPlayerConnection
import com.ruben.spotitube.ArmoryColors
import com.ruben.spotitube.ArmoryHeader
import com.ruben.spotitube.rememberAvatarUrl
import com.ruben.spotitube.ui.component.LocalMenuState
import com.ruben.spotitube.ui.component.NavigationTitle
import com.ruben.spotitube.viewmodels.HomeViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    navController: NavController,
    onVisible: () -> Unit = {},
    viewModel: HomeViewModel = run {
        val context = androidx.compose.ui.platform.LocalContext.current
        val owner = remember(context) {
            var ctx = context
            while (ctx is android.content.ContextWrapper) {
                if (ctx is androidx.activity.ComponentActivity) return@remember ctx
                ctx = ctx.baseContext
            }
            null
        }
        if (owner != null) hiltViewModel(viewModelStoreOwner = owner) else hiltViewModel()
    },
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
    val homePage by viewModel.homePage.collectAsStateWithLifecycle()
    val allItemsMetadata by viewModel.allItemsMetadata.collectAsStateWithLifecycle()

    val isInitialLoadComplete by viewModel.isInitialLoadComplete.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val pullRefreshState = rememberPullToRefreshState()

    val moodAndGenresViewModel: com.ruben.spotitube.viewmodels.MoodAndGenresViewModel = run {
        val context = androidx.compose.ui.platform.LocalContext.current
        val owner = remember(context) {
            var ctx = context
            while (ctx is android.content.ContextWrapper) {
                if (ctx is androidx.activity.ComponentActivity) return@remember ctx
                ctx = ctx.baseContext
            }
            null
        }
        if (owner != null) hiltViewModel(viewModelStoreOwner = owner) else hiltViewModel()
    }

    LaunchedEffect(isInitialLoadComplete) {
        if (isInitialLoadComplete) {
            moodAndGenresViewModel.load()
        }
    }

    val hasLoggedHomeVisible = remember { androidx.compose.runtime.mutableStateOf(false) }

    LaunchedEffect(isInitialLoadComplete, quickPicks) {
        if ((isInitialLoadComplete || !quickPicks.isNullOrEmpty()) && !hasLoggedHomeVisible.value) {
            hasLoggedHomeVisible.value = true
            com.ruben.spotitube.utils.PerfTiming.endSection("Home.contentVisible", com.ruben.spotitube.utils.PerfTiming.appStartTimeMs)
            com.ruben.spotitube.utils.PerfTiming.onTabComposedWithData(Screens.Home.route, hasData = !quickPicks.isNullOrEmpty())
            onVisible()
        }
    }

    val accountImageUrl by viewModel.accountImageUrl.collectAsStateWithLifecycle()
    val savedAvatarUrl = rememberAvatarUrl()
    val url = savedAvatarUrl ?: accountImageUrl.takeIf { !it.isNullOrEmpty() }

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

            Box(
                modifier = Modifier
                    .weight(1f)
                    .pullToRefresh(
                        state = pullRefreshState,
                        isRefreshing = isRefreshing,
                        onRefresh = viewModel::refresh
                    )
            ) {
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



                    // 6. Lo nuevo de
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
