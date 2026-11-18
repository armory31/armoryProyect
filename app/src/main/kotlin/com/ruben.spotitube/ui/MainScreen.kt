package com.ruben.spotitube.ui

import android.content.Intent
import android.view.Window
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Scaffold
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ruben.spotitube.ArmoryColors
import com.ruben.spotitube.ArmoryNavigationBar
import com.ruben.spotitube.MainActivity
import com.ruben.spotitube.R
import com.ruben.spotitube.constants.AppBarHeight
import com.ruben.spotitube.constants.DefaultOpenTabKey
import com.ruben.spotitube.constants.HasPressedStarKey
import com.ruben.spotitube.constants.LaunchCountKey
import com.ruben.spotitube.constants.MiniPlayerLastAnchorKey
import com.ruben.spotitube.constants.NavigationTab
import com.ruben.spotitube.constants.RemindAfterKey
import com.ruben.spotitube.db.MusicDatabase
import com.ruben.spotitube.playback.DownloadProgress
import com.ruben.spotitube.playback.DownloadUtil
import com.ruben.spotitube.playback.PlayerConnection
import com.ruben.spotitube.sync.LibraryStateHolder
import com.ruben.spotitube.sync.SyncManager
import com.ruben.spotitube.ui.component.BottomSheetMenu
import com.ruben.spotitube.ui.component.BottomSheetPageState
import com.ruben.spotitube.ui.component.COLLAPSED_ANCHOR
import com.ruben.spotitube.ui.component.DISMISSED_ANCHOR
import com.ruben.spotitube.ui.component.DownloadProgressPopup
import com.ruben.spotitube.ui.component.EXPANDED_ANCHOR
import com.ruben.spotitube.ui.component.LocalBottomSheetPageState
import com.ruben.spotitube.ui.component.LocalMenuState
import com.ruben.spotitube.ui.component.MenuState
import com.ruben.spotitube.ui.component.rememberBottomSheetState
import com.ruben.spotitube.ui.component.shimmer.ShimmerTheme
import com.ruben.spotitube.ui.player.BottomSheetPlayer
import com.ruben.spotitube.ui.screens.Screens
import com.ruben.spotitube.ui.screens.navigationBuilder
import com.ruben.spotitube.ui.utils.appBarScrollBehavior
import com.ruben.spotitube.ui.utils.resetHeightOffset
import com.ruben.spotitube.ui.utils.tabSwipeNavigation
import com.ruben.spotitube.utils.SyncUtils
import com.ruben.spotitube.utils.dataStore
import com.ruben.spotitube.utils.get
import com.ruben.spotitube.utils.rememberEnumPreference
import com.ruben.spotitube.utils.rememberPreference
import com.valentinilk.shimmer.LocalShimmerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import kotlin.time.Duration.Companion.milliseconds

private val NavBarHeight = 74.dp

@Composable
internal fun MainScreen(
    database: MusicDatabase,
    downloadUtil: DownloadUtil,
    syncUtils: SyncUtils,
    libraryStateHolder: LibraryStateHolder,
    syncManager: SyncManager,
    playerConnection: PlayerConnection?,
    latestVersionName: String,
    downloadProgress: DownloadProgress,
    showDownloadPopup: Boolean,
    isUiReady: Boolean,
    bottomSheetPageState: BottomSheetPageState,
    menuState: MenuState,
    onHomeVisible: () -> Unit,
    onNavControllerCreated: (NavHostController) -> Unit,
    onHandleDeepLinkIntent: (NavHostController) -> Unit,
    onSetExpandPlayerBottomSheet: (((() -> Unit)?) -> Unit),
    consumeShouldExpandPlayerForDeepLink: () -> Boolean,
    window: Window,
    intent: Intent? = null,
) {
    val context = LocalContext.current

    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black)
    ) {
        val windowsInsets = WindowInsets.systemBars

        val useRail = currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass != androidx.window.core.layout.WindowWidthSizeClass.COMPACT

        val isKeyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

        val navController = rememberNavController()
        onNavControllerCreated(navController)
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        val isYearInMusicScreen = currentRoute == "year_in_music"

        val navigationItems: List<Triple<String, ImageVector, Int>> = remember {
            listOf(
                Triple(Screens.Home.route, Icons.Default.Home, R.string.home),
                Triple(Screens.Explore.route, Icons.Default.Explore, R.string.explore),
                Triple(Screens.MoodAndGenres.route, Icons.Default.Style, R.string.mood_and_genres),
                Triple(Screens.Library.route, Icons.Default.LibraryMusic, R.string.filter_library)
            )
        }

        val (savedMiniPlayerAnchor, setSavedMiniPlayerAnchor) = rememberPreference(
            MiniPlayerLastAnchorKey,
            defaultValue = COLLAPSED_ANCHOR
        )
        val defaultOpenTab by rememberEnumPreference<NavigationTab>(DefaultOpenTabKey, NavigationTab.HOME)

        val topLevelScreens =
            listOf(
                Screens.Home.route,
                Screens.Explore.route,
                Screens.Search.route,
                Screens.MoodAndGenres.route,
                Screens.Library.route,
                "settings",
            )

        val (_, onQueryChange) =
            rememberSaveable(stateSaver = TextFieldValue.Saver) {
                mutableStateOf(TextFieldValue())
            }

        var active by rememberSaveable {
            mutableStateOf(false)
        }

        val focusManager = LocalFocusManager.current

        val onActiveChange: (Boolean) -> Unit = { newActive ->
            active = newActive
            if (!newActive) {
                focusManager.clearFocus()
                if (navigationItems.fastAny { it.first == navBackStackEntry?.destination?.route }) {
                    onQueryChange(TextFieldValue())
                }
            }
        }

        val searchBarFocusRequester = remember { FocusRequester() }

        var openSearchImmediately: Boolean by remember {
            mutableStateOf(intent?.action == MainActivity.ACTION_SEARCH)
        }

        val shouldShowSearchBar =
            remember(active, navBackStackEntry) {
                active ||
                        navigationItems.fastAny { it.first == navBackStackEntry?.destination?.route } ||
                        navBackStackEntry?.destination?.route?.startsWith("search/") == true
            }

        val nowPlaying by (playerConnection?.mediaMetadata ?: MutableStateFlow(null)).collectAsStateWithLifecycle()

        val route = navBackStackEntry?.destination?.route
        val canShowNav = (route == null ||
                navigationItems.fastAny { it.first == route } ||
                topLevelScreens.contains(route) ||
                route.startsWith("settings") ||
                route.startsWith("history") ||
                route.startsWith("search")) &&
                !active

        val systemNavPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val navBarHeight = if (canShowNav && !useRail) NavBarHeight else 0.dp
        val dynamicCollapsedBound = 76.dp + navBarHeight + systemNavPadding

        val playerBottomSheetState =
            rememberBottomSheetState(
                dismissedBound = 0.dp,
                collapsedBound = dynamicCollapsedBound,
                expandedBound = maxHeight,
            )

        val shouldShowNavigationBar =
            canShowNav &&
                    playerBottomSheetState.progress <= 0f &&
                    !isKeyboardVisible

        val shouldShowMiniPlayer =
            !active &&
                    playerBottomSheetState.progress <= 0f &&
                    route != "settings" &&
                    route != "artist_view_all/{id}/{name}/{type}" &&
                    route?.startsWith("player_full_screen") != true

        val miniPlayerAnchor by remember {
            derivedStateOf {
                when {
                    playerBottomSheetState.isExpanded -> EXPANDED_ANCHOR
                    playerBottomSheetState.isDismissed -> DISMISSED_ANCHOR
                    else -> COLLAPSED_ANCHOR
                }
            }
        }

        var miniPlayerAnchorPersistenceEnabled by remember(playerConnection) {
            mutableStateOf(false)
        }

        LaunchedEffect(miniPlayerAnchor, isYearInMusicScreen, miniPlayerAnchorPersistenceEnabled) {
            if (!isYearInMusicScreen && miniPlayerAnchorPersistenceEnabled) {
                setSavedMiniPlayerAnchor(miniPlayerAnchor)
            }
        }

        var yearInMusicSavedPlayerAnchor by rememberSaveable { mutableIntStateOf(-1) }

        LaunchedEffect(isYearInMusicScreen) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            if (isYearInMusicScreen) {
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.statusBars())
            } else {
                controller.show(WindowInsetsCompat.Type.statusBars())
            }
        }

        LaunchedEffect(isYearInMusicScreen, playerConnection) {
            val player = playerConnection?.player ?: return@LaunchedEffect

            if (isYearInMusicScreen) {
                if (yearInMusicSavedPlayerAnchor == -1) {
                    yearInMusicSavedPlayerAnchor =
                        when {
                            playerBottomSheetState.isExpanded -> EXPANDED_ANCHOR
                            playerBottomSheetState.isCollapsed -> COLLAPSED_ANCHOR
                            playerBottomSheetState.isDismissed -> DISMISSED_ANCHOR
                            else -> COLLAPSED_ANCHOR
                        }
                }

                if (!playerBottomSheetState.isDismissed) {
                    playerBottomSheetState.dismiss()
                }
            } else if (yearInMusicSavedPlayerAnchor != -1) {
                val anchorToRestore = yearInMusicSavedPlayerAnchor
                yearInMusicSavedPlayerAnchor = -1

                if (player.currentMediaItem == null) {
                    playerBottomSheetState.dismiss()
                } else {
                    when (anchorToRestore) {
                        EXPANDED_ANCHOR -> playerBottomSheetState.expandSoft()
                        COLLAPSED_ANCHOR -> playerBottomSheetState.collapseSoft()
                        DISMISSED_ANCHOR -> playerBottomSheetState.dismiss()
                        else -> playerBottomSheetState.collapseSoft()
                    }
                }
            }
        }

        val searchBarScrollBehavior =
            appBarScrollBehavior(
                canScroll = {
                    navBackStackEntry?.destination?.route?.startsWith("search/") == false &&
                            (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
                },
            )
        val topAppBarScrollBehavior =
            appBarScrollBehavior(
                canScroll = {
                    navBackStackEntry?.destination?.route?.startsWith("search/") == false &&
                            (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
                },
            )

        var previousRoute by rememberSaveable { mutableStateOf<String?>(null) }

        LaunchedEffect(navBackStackEntry) {
            val currentRoute = navBackStackEntry?.destination?.route
            val routeChanged = previousRoute != currentRoute
            val wasOnNonTopLevelScreen = previousRoute != null &&
                    previousRoute !in topLevelScreens &&
                    previousRoute?.startsWith("search/") != true
            val isReturningToHomeOrLibrary = currentRoute == Screens.Home.route ||
                    currentRoute == Screens.Library.route

            if (routeChanged && currentRoute?.startsWith("search/") != true) {
                topAppBarScrollBehavior.state.resetHeightOffset()
            }

            if (wasOnNonTopLevelScreen && isReturningToHomeOrLibrary) {
                searchBarScrollBehavior.state.resetHeightOffset()
                topAppBarScrollBehavior.state.resetHeightOffset()
            }

            previousRoute = currentRoute

            if (navBackStackEntry?.destination?.route?.startsWith("search/") == true) {
                val searchQuery =
                    withContext(Dispatchers.IO) {
                        if (navBackStackEntry
                                ?.arguments
                                ?.getString(
                                    "query",
                                )!!
                                .contains(
                                    "%",
                                )
                        ) {
                            navBackStackEntry?.arguments?.getString(
                                "query",
                            )!!
                        } else {
                            URLDecoder.decode(
                                navBackStackEntry?.arguments?.getString("query")!!,
                                "UTF-8"
                            )
                        }
                    }
                onQueryChange(
                    TextFieldValue(
                        searchQuery,
                        TextRange(searchQuery.length)
                    )
                )
            } else if (navigationItems.fastAny { it.first == navBackStackEntry?.destination?.route } || navBackStackEntry?.destination?.route in topLevelScreens) {
                onQueryChange(TextFieldValue())
                if (navBackStackEntry?.destination?.route != Screens.Home.route) {
                    searchBarScrollBehavior.state.resetHeightOffset()
                    topAppBarScrollBehavior.state.resetHeightOffset()
                }
            }
        }
        LaunchedEffect(active) {
            if (active) {
                searchBarScrollBehavior.state.resetHeightOffset()
                topAppBarScrollBehavior.state.resetHeightOffset()
                searchBarFocusRequester.requestFocus()
            }
        }

        var restoredMiniPlayerAnchor by remember(playerConnection) { mutableStateOf(false) }

        LaunchedEffect(playerConnection, savedMiniPlayerAnchor, isYearInMusicScreen) {
            if (restoredMiniPlayerAnchor) return@LaunchedEffect
            val player = playerConnection?.player ?: return@LaunchedEffect
            val connection = playerConnection ?: return@LaunchedEffect
            connection.queueRestoreCompleted.first { it }
            if (player.currentMediaItem == null) {
                if (!playerBottomSheetState.isDismissed) {
                    playerBottomSheetState.dismiss()
                }
            } else {
                if (!isYearInMusicScreen) {
                    when (savedMiniPlayerAnchor) {
                        EXPANDED_ANCHOR, COLLAPSED_ANCHOR -> playerBottomSheetState.collapseSoft()
                        else -> playerBottomSheetState.dismiss()
                    }
                }
            }
            restoredMiniPlayerAnchor = true
            miniPlayerAnchorPersistenceEnabled = true
        }

        DisposableEffect(playerConnection, playerBottomSheetState) {
            val expandAction = { playerBottomSheetState.expandSoft() }
            onSetExpandPlayerBottomSheet(expandAction)
            if (consumeShouldExpandPlayerForDeepLink()) {
                expandAction()
            }
            val player =
                playerConnection?.player ?: return@DisposableEffect onDispose { onSetExpandPlayerBottomSheet(null) }
            val listener =
                object : Player.Listener {
                    override fun onMediaItemTransition(
                        mediaItem: MediaItem?,
                        reason: Int,
                    ) {
                        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED &&
                            mediaItem != null &&
                            playerBottomSheetState.isDismissed &&
                            !isYearInMusicScreen
                        ) {
                            playerBottomSheetState.collapseSoft()
                        }
                    }
                }
            player.addListener(listener)
            onDispose {
                player.removeListener(listener)
                onSetExpandPlayerBottomSheet(null)
            }
        }

        LaunchedEffect(Unit) {
            onHandleDeepLinkIntent(navController)
        }

        LaunchedEffect(Unit) {
            delay(3000.milliseconds)

            withContext(Dispatchers.IO) {
                val current = context.dataStore[LaunchCountKey] ?: 0
                val newCount = current + 1
                context.dataStore.edit { prefs ->
                    prefs[LaunchCountKey] = newCount
                }
            }

            val shouldShow = withContext(Dispatchers.IO) {
                val hasPressed = context.dataStore[HasPressedStarKey] ?: false
                val remindAfter = context.dataStore[RemindAfterKey] ?: 3
                !hasPressed && (context.dataStore[LaunchCountKey] ?: 0) >= remindAfter
            }

            if (shouldShow) {
                var waited = 0L
                val waitStep = 500L
                val maxWait = 30_000L
                while (bottomSheetPageState.isVisible && waited < maxWait) {
                    delay(waitStep.milliseconds)
                    waited += waitStep
                }
            }
        }



        val playerAwareWindowInsets =
            remember(
                useRail,
                shouldShowNavigationBar,
                shouldShowMiniPlayer,
                nowPlaying,
            ) {
                var bottomPadding = 0.dp
                if (!useRail) {
                    if (shouldShowMiniPlayer && nowPlaying != null) {
                        bottomPadding += 72.dp
                    }
                }
                windowsInsets
                    .only((if(useRail) {
                        WindowInsetsSides.Right
                    } else WindowInsetsSides.Horizontal) + WindowInsetsSides.Top)
                    .add(WindowInsets(top = AppBarHeight, bottom = bottomPadding))
            }

        CompositionLocalProvider(
            LocalDatabase provides database,
            LocalContentColor provides Color.White,
            LocalPlayerConnection provides playerConnection,
            LocalPlayerAwareWindowInsets provides playerAwareWindowInsets,
            LocalDownloadUtil provides downloadUtil,
            LocalShimmerTheme provides ShimmerTheme,
            LocalSyncUtils provides syncUtils,
            LocalLibraryStateHolder provides libraryStateHolder,
            LocalSyncManager provides syncManager,
            LocalBottomSheetPageState provides bottomSheetPageState,
            LocalMenuState provides menuState,
        ) {
            Box(modifier = Modifier.fillMaxSize().background(ArmoryColors.AbsoluteBlack)) {
                Scaffold(
                    containerColor = Color.Transparent,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    topBar = {
                    },
                    bottomBar = {
                        if (shouldShowNavigationBar && !useRail) {
                            Box(
                                modifier = Modifier
                                    .navigationBarsPadding()
                                    .padding(horizontal = 12.dp)
                                    .padding(bottom = 12.dp)
                            ) {
                                ArmoryNavigationBar(
                                    selectedTab = when (currentRoute) {
                                        Screens.Home.route -> 0
                                        Screens.Explore.route -> 1
                                        Screens.MoodAndGenres.route -> 2
                                        Screens.Library.route -> 3
                                        else -> 0
                                    },
                                    onTabChange = { idx ->
                                        val screenRoute = when (idx) {
                                            0 -> Screens.Home.route
                                            1 -> Screens.Explore.route
                                            2 -> Screens.MoodAndGenres.route
                                            3 -> Screens.Library.route
                                            else -> Screens.Home.route
                                        }
                                        com.ruben.spotitube.utils.PerfTiming.onTabClicked(screenRoute)
                                        navController.navigate(screenRoute) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (currentRoute !in topLevelScreens &&
                                (currentRoute?.startsWith("artist/") == true ||
                                        currentRoute?.startsWith("album/") == true)
                            ) {
                                Modifier
                            } else {
                                Modifier.nestedScroll(searchBarScrollBehavior.nestedScrollConnection)
                            }
                        )
                ) { paddingValues ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .then(if (!shouldShowNavigationBar && systemNavPadding > 0.dp) Modifier.navigationBarsPadding() else Modifier)
                            .tabSwipeNavigation(route, navController)
                    ) {
                        val tabOpenedFromShortcut = remember {
                            when (intent?.action) {
                                MainActivity.ACTION_LIBRARY -> NavigationTab.LIBRARY
                                MainActivity.ACTION_SEARCH -> NavigationTab.SEARCH
                                else -> null
                            }
                        }

                        NavHost(
                            navController = navController,
                            startDestination = when (tabOpenedFromShortcut ?: defaultOpenTab) {
                                NavigationTab.HOME -> Screens.Home
                                NavigationTab.LIBRARY -> Screens.Library
                                else -> Screens.Home
                            }.route,
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(
                                    if (navigationItems.fastAny { it.first == navBackStackEntry?.destination?.route } ||
                                        navBackStackEntry?.destination?.route?.startsWith("search/") == true
                                    ) {
                                        searchBarScrollBehavior.nestedScrollConnection
                                    } else {
                                        topAppBarScrollBehavior.nestedScrollConnection
                                    }
                                ),
                            enterTransition = {
                                if (initialState.destination.route in topLevelScreens && targetState.destination.route in topLevelScreens) {
                                    fadeIn(tween(50))
                                } else {
                                    fadeIn(tween(250)) + slideInHorizontally { it / 2 }
                                }
                            },
                            exitTransition = {
                                if (initialState.destination.route in topLevelScreens && targetState.destination.route in topLevelScreens) {
                                    fadeOut(tween(50))
                                } else {
                                    fadeOut(tween(200)) + slideOutHorizontally { -it / 2 }
                                }
                            },
                            popEnterTransition = {
                                if (initialState.destination.route in topLevelScreens && targetState.destination.route in topLevelScreens) {
                                    fadeIn(tween(50))
                                } else {
                                    fadeIn(tween(250)) + slideInHorizontally { -it / 2 }
                                }
                            },
                            popExitTransition = {
                                if (initialState.destination.route in topLevelScreens && targetState.destination.route in topLevelScreens) {
                                    fadeOut(tween(50))
                                } else {
                                    fadeOut(tween(200)) + slideOutHorizontally { it / 2 }
                                }
                            }
                        ) {
                            navigationBuilder(
                                navController,
                                topAppBarScrollBehavior,
                                latestVersionName,
                                onHomeVisible = onHomeVisible
                            )
                        }
                    }
                }
            }

            if (isUiReady) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                ) {
                    BottomSheetPlayer(
                        state = playerBottomSheetState,
                        navController = navController,
                        navigationBarHeight = if (shouldShowNavigationBar && !useRail) NavBarHeight else 0.dp
                    )
                }
            }

            BottomSheetMenu(
                state = menuState,
            )

            AnimatedVisibility(
                visible = showDownloadPopup && isUiReady,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 8.dp, start = 16.dp, end = 16.dp)
                    .fillMaxWidth(),
            ) {
                DownloadProgressPopup(downloadProgress, Modifier.fillMaxWidth())
            }

            if (!isUiReady) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.armory_logo),
                        contentDescription = "Logo",
                        modifier = Modifier
                            .height(160.dp)
                            .fillMaxWidth(0.4f)
                    )
                }
            }
        }

        LaunchedEffect(shouldShowSearchBar, openSearchImmediately) {
            if (shouldShowSearchBar && openSearchImmediately) {
                onActiveChange(true)
                try {
                    delay(100.milliseconds)
                    searchBarFocusRequester.requestFocus()
                } catch (_: Exception) {
                }
                openSearchImmediately = false
            }
        }
    }
}
