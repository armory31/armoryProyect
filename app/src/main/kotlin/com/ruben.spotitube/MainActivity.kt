/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ruben.spotitube

import android.Manifest
import com.ruben.spotitube.ArmoryGradients
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Button
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ruben.spotitube.constants.AppBarHeight
import com.ruben.spotitube.constants.AppLanguageKey
import com.ruben.spotitube.constants.DefaultOpenTabKey
import com.ruben.spotitube.constants.DisableScreenshotKey
import com.ruben.spotitube.constants.HasPressedStarKey
import com.ruben.spotitube.constants.LastNotifiedVersionKey
import com.ruben.spotitube.constants.LaunchCountKey
import com.ruben.spotitube.constants.MiniPlayerLastAnchorKey
import com.ruben.spotitube.constants.NavigationTab
import com.ruben.spotitube.constants.RemindAfterKey
import com.ruben.spotitube.constants.SYSTEM_DEFAULT
import com.ruben.spotitube.constants.StopMusicOnTaskClearKey
import com.ruben.spotitube.constants.TogetherDisplayNameKey
import com.ruben.spotitube.db.MusicDatabase
import com.ruben.spotitube.extensions.toMediaItem
import com.ruben.spotitube.innertube.YouTube
import com.ruben.spotitube.playback.DownloadUtil
import com.ruben.spotitube.playback.MusicService
import com.ruben.spotitube.playback.MusicService.MusicBinder
import com.ruben.spotitube.playback.PlayerConnection
import com.ruben.spotitube.playback.queues.ListQueue
import com.ruben.spotitube.ui.component.BottomSheetMenu
import com.ruben.spotitube.ui.component.BottomSheetPageState
import com.ruben.spotitube.ui.component.COLLAPSED_ANCHOR
import com.ruben.spotitube.ui.component.DISMISSED_ANCHOR
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
import com.ruben.spotitube.utils.PreferenceStore
import com.ruben.spotitube.utils.SessionManager
import com.ruben.spotitube.utils.SyncUtils
import com.ruben.spotitube.utils.UpdateNotificationManager
import com.ruben.spotitube.utils.Updater
import com.ruben.spotitube.utils.dataStore
import com.ruben.spotitube.utils.get
import com.ruben.spotitube.utils.rememberEnumPreference
import com.ruben.spotitube.utils.rememberPreference
import com.ruben.spotitube.utils.reportException
import com.ruben.spotitube.utils.setAppLocale
import com.mikepenz.markdown.m3.Markdown
import com.valentinilk.shimmer.LocalShimmerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.util.Locale
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@Suppress("DEPRECATION", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var database: MusicDatabase

    @Inject
    lateinit var downloadUtil: DownloadUtil

    @Inject
    lateinit var syncUtils: SyncUtils

    @Inject
    lateinit var libraryStateHolder: com.ruben.spotitube.sync.LibraryStateHolder

    @Inject
    lateinit var syncManager: com.ruben.spotitube.sync.SyncManager

    @Inject
    lateinit var armoryAuthManager: com.ruben.spotitube.sync.ArmoryAuthManager

    private lateinit var session: SessionManager

    private lateinit var navController: NavHostController
    private var pendingIntent: Intent? = null
    private var pendingDeepLinkSong: PendingDeepLinkSong? = null
    private var pendingTogetherJoinLink: String? = null
    private var latestVersionName: String by mutableStateOf(BuildConfig.VERSION_NAME)

    private var playerConnection by mutableStateOf<PlayerConnection?>(null)
    private var isMusicServiceBound = false
    private var isMainActivityReady = mutableStateOf(false)

    private val serviceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?,
            ) {
                isMusicServiceBound = true
                if (service is MusicBinder) {
                    playerConnection =
                        PlayerConnection(this@MainActivity, service, database, lifecycleScope)
                    playPendingDeepLinkSongIfReady()
                    joinPendingTogetherIfReady()
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                isMusicServiceBound = false
                playerConnection?.dispose()
                playerConnection = null
            }
        }

    private data class PendingDeepLinkSong(
        val mediaItem: MediaItem,
    )

    private fun playPendingDeepLinkSongIfReady() {
        val pending = pendingDeepLinkSong ?: return
        val connection = playerConnection ?: return
        pendingDeepLinkSong = null
        connection.playQueue(ListQueue(items = listOf(pending.mediaItem)))
    }

    private fun joinPendingTogetherIfReady() {
        val pending = pendingTogetherJoinLink ?: return
        val connection = playerConnection ?: return
        pendingTogetherJoinLink = null
        lifecycleScope.launch(Dispatchers.IO) {
            val displayName =
                runCatching { dataStore.data.first()[TogetherDisplayNameKey] }
                    .getOrNull()
                    ?.trim()
                    .orEmpty()
                    .ifBlank { Build.MODEL ?: getString(R.string.app_name) }
            withContext(Dispatchers.Main) {
                connection.service.joinTogether(pending, displayName)
            }
        }
    }


    override fun onStart() {
        super.onStart()
        val intent = Intent(this@MainActivity, MusicService::class.java)
        isMusicServiceBound = bindService(intent, serviceConnection, BIND_AUTO_CREATE)
        playPendingDeepLinkSongIfReady()
    }

    private fun safeUnbindMusicService() {
        if (!isMusicServiceBound) return
        try {
            unbindService(serviceConnection)
        } catch (_: IllegalArgumentException) {
        } catch (e: Exception) {
            reportException(e)
        } finally {
            isMusicServiceBound = false
        }
    }

    override fun onStop() {
        safeUnbindMusicService()
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()

        val shouldStopOnTaskClear =
            isFinishing && dataStore.get(StopMusicOnTaskClearKey, false)

        if (shouldStopOnTaskClear) {
            safeUnbindMusicService()
            stopService(Intent(this, MusicService::class.java))
            playerConnection = null
        }
    }





    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (::navController.isInitialized) {
            handleDeepLinkIntent(intent, navController)
        } else {
            pendingIntent = intent
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        startMusicServiceSafely()
        super.onCreate(savedInstanceState)
        
        var isLoginChecked by mutableStateOf(false)
        var isRedirecting by mutableStateOf(false)

        splashScreen.setKeepOnScreenCondition {
            !isRedirecting && (!App.instance.isInitialized.value || !isLoginChecked || !isMainActivityReady.value)
        }

        session = SessionManager(this)
        
        lifecycleScope.launch {
            // Se confía en la sesión local para un inicio instantáneo. 
            // El backup detectará si el token de Firebase ha caducado en segundo plano.
            val hasSession = session.isLoggedIn()
            if (!hasSession) {
                isRedirecting = true
                session.logout()
                armoryAuthManager.clearAccountInfo()
                startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                finish()
            } else {
                isLoginChecked = true
            }
        }

        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_LTR
        window.decorView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            val initialLocale = PreferenceStore.get(AppLanguageKey)
                ?.takeUnless { it == SYSTEM_DEFAULT }
                ?.let { Locale.forLanguageTag(it) }
                ?: Locale.getDefault()
            setAppLocale(this, initialLocale)

            lifecycleScope.launch(Dispatchers.IO) {
                runCatching {
                    dataStore.data.first()[AppLanguageKey]
                }.onSuccess { lang ->
                    val targetLocale = lang
                        ?.takeUnless { it == SYSTEM_DEFAULT }
                        ?.let { Locale.forLanguageTag(it) }
                        ?: Locale.getDefault()
                    if (targetLocale != initialLocale) {
                        withContext(Dispatchers.Main) {
                            setAppLocale(this@MainActivity, targetLocale)
                            recreate()
                        }
                    }
                }
            }
        }

        lifecycleScope.launch(Dispatchers.IO) {
            dataStore.data
                .map { it[DisableScreenshotKey] ?: false }
                .distinctUntilChanged()
                .collectLatest {
                    withContext(Dispatchers.Main) {
                        if (it) {
                            window.setFlags(
                                WindowManager.LayoutParams.FLAG_SECURE,
                                WindowManager.LayoutParams.FLAG_SECURE,
                            )
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        }
                    }
                }
        }

        setContent {
            val notificationPermissionLauncher =
                rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                    if (isGranted) {
                        playerConnection?.service?.refreshPlaybackNotification()
                    }
                }

            var isUiReady by remember { mutableStateOf(false) }

            val lastNotifiedVersion by rememberPreference(LastNotifiedVersionKey, defaultValue = "")

            LaunchedEffect(lastNotifiedVersion) {
                if (lastNotifiedVersion.isNotBlank() && !Updater.isSameVersion(lastNotifiedVersion, BuildConfig.VERSION_NAME)) {
                    latestVersionName = lastNotifiedVersion
                }
            }

            // El logo de carga solo se irá cuando la pantalla confirme que ha cargado los datos iniciales.
            val onHomeVisible = remember {
                {
                    if (!isUiReady) {
                        isUiReady = true
                        isMainActivityReady.value = true
                        
                        lifecycleScope.launch(Dispatchers.IO) {
                            syncManager.schedulePeriodicSync(this@MainActivity)
                            UpdateNotificationManager.schedulePeriodicUpdateCheck(this@MainActivity)
                        }

                        lifecycleScope.launch {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(
                                    this@MainActivity,
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    }
                }
            }

            // Use remembered instances so the same state object is used everywhere
            // (previously retrieving the composition local directly created different
            // instances in different composition scopes which caused the update
            // bottom sheet to not appear and overlay interactions to be blocked).
            val bottomSheetPageState = remember { BottomSheetPageState() }
            val menuState = remember { MenuState() }
            val uriHandler = LocalUriHandler.current
            val releaseNotesState = remember { mutableStateOf<String?>(null) }
            val updateSheetContent: @Composable ColumnScope.() -> Unit = { // receiver: ColumnScope
                Text(
                    text = stringResource(R.string.new_update_available),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(top = 16.dp)
                )

                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {},
                    shape = CircleShape,
                    contentPadding = PaddingValues(
                        horizontal = 5.dp,
                        vertical = 5.dp
                    )
                ) {
                    Text(text = latestVersionName, style = MaterialTheme.typography.labelLarge)
                }

                Spacer(Modifier.height(12.dp))

                Box(modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                ) {
                    val notes = releaseNotesState.value
                    if (!notes.isNullOrBlank()) {
                        Markdown(
                            content = notes,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 8.dp)
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.release_notes_unavailable),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = {
                        try {
                            uriHandler.openUri(Updater.getLatestDownloadUrl())
                        } catch (_: Exception) {}
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(R.string.update_text))
                }
            }

            // fetch release notes and show sheet when a new version is detected
            LaunchedEffect(latestVersionName) {
                if (!Updater.isSameVersion(latestVersionName, BuildConfig.VERSION_NAME)) {
                    Updater.getLatestReleaseNotes().onSuccess {
                        releaseNotesState.value = it
                    }.onFailure {
                        releaseNotesState.value = null
                    }

                    bottomSheetPageState.show(updateSheetContent)
                }
            }

            LaunchedEffect(Unit) {
                setSystemBarAppearance(true)
            }

            ArmoryTheme {
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
                        mutableStateOf(intent?.action == ACTION_SEARCH)
                    }

                    val shouldShowSearchBar =
                        remember(active, navBackStackEntry) {
                            active ||
                                    navigationItems.fastAny { it.first == navBackStackEntry?.destination?.route } ||
                                    navBackStackEntry?.destination?.route?.startsWith("search/") == true
                        }

                    val nowPlaying by (playerConnection?.mediaMetadata ?: MutableStateFlow(null)).collectAsState()

                    val route = navBackStackEntry?.destination?.route
                    val canShowNav = (route == null ||
                            navigationItems.fastAny { it.first == route } ||
                            topLevelScreens.contains(route) ||
                            route.startsWith("settings") ||
                            route.startsWith("history") ||
                            route.startsWith("search")) &&
                            !active

                    val systemNavPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    val navBarHeight = if (canShowNav && !useRail) 88.dp else 0.dp
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
                        val wasOnNonTopLevelScreen = previousRoute != null &&
                                previousRoute !in topLevelScreens &&
                                previousRoute?.startsWith("search/") != true
                        val isReturningToHomeOrLibrary = currentRoute == Screens.Home.route ||
                                currentRoute == Screens.Library.route

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
                        val player =
                            playerConnection?.player ?: return@DisposableEffect onDispose { }
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
                        }
                    }

                    LaunchedEffect(Unit) {
                        if (pendingIntent != null) {
                            handleDeepLinkIntent(pendingIntent!!, navController)
                            pendingIntent = null
                        } else {
                            handleDeepLinkIntent(intent, navController)
                        }
                    }

                    LaunchedEffect(Unit) {
                        delay(3000.milliseconds)

                        withContext(Dispatchers.IO) {
                            val current = dataStore[LaunchCountKey] ?: 0
                            val newCount = current + 1
                            dataStore.edit { prefs ->
                                prefs[LaunchCountKey] = newCount
                            }
                        }

                        val shouldShow = withContext(Dispatchers.IO) {
                            val hasPressed = dataStore[HasPressedStarKey] ?: false
                            val remindAfter = dataStore[RemindAfterKey] ?: 3
                            !hasPressed && (dataStore[LaunchCountKey] ?: 0) >= remindAfter
                        }

                        if (shouldShow) {
                            var waited = 0L
                            val waitStep = 500L
                            val maxWait = 30_000L
                            while (bottomSheetPageState.isVisible && waited < maxWait) {
                                delay(waitStep.milliseconds)
                                waited += waitStep
                            }
                            // star dialog logic omitted as per cleanup
                        }
                    }


                    val playerStatus by remember(playerConnection) {
                        derivedStateOf {
                            val isPlaying = playerConnection?.isPlaying?.value ?: false
                            val playbackState = playerConnection?.playbackState?.value ?: Player.STATE_IDLE
                            when {
                                playbackState == Player.STATE_BUFFERING -> PlayerStatus.LOADING
                                isPlaying -> PlayerStatus.PLAYING
                                else -> PlayerStatus.PAUSED
                            }
                        }
                    }

                    var position by remember { mutableLongStateOf(0L) }
                    LaunchedEffect(playerConnection, playerStatus) {
                        while (playerStatus == PlayerStatus.PLAYING) {
                            position = playerConnection?.player?.currentPosition ?: 0L
                            delay(1000.milliseconds)
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
                                    bottomPadding += 72.dp // Espacio para el MiniPlayer de OpenTune
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
                                    // Se ha eliminado la TopAppBar antigua para dejar solo el diseño Armory
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
                                                    navController.navigate(screenRoute) {
                                                        popUpTo(navController.graph.startDestinationId) {
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
                                // El NavHost ocupa el contenido del Scaffold respetando los paddings de las barras
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(paddingValues)
                                        .then(if (!shouldShowNavigationBar && systemNavPadding > 0.dp) Modifier.navigationBarsPadding() else Modifier)
                                        .then(
                                            if (route in listOf(Screens.Home.route, Screens.Explore.route, Screens.MoodAndGenres.route, Screens.Library.route)) {
                                                Modifier.pointerInput(route) {
                                                    var totalDrag = 0f
                                                    detectHorizontalDragGestures(
                                                        onDragStart = { totalDrag = 0f },
                                                        onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount },
                                                        onDragEnd = {
                                                            val currentIdx = when (route) {
                                                                Screens.Home.route -> 0
                                                                Screens.Explore.route -> 1
                                                                Screens.MoodAndGenres.route -> 2
                                                                Screens.Library.route -> 3
                                                                else -> 0
                                                            }
                                                            if (totalDrag < -120f && currentIdx < 3) {
                                                                val nextRoute = when (currentIdx + 1) {
                                                                    1 -> Screens.Explore.route
                                                                    2 -> Screens.MoodAndGenres.route
                                                                    3 -> Screens.Library.route
                                                                    else -> Screens.Home.route
                                                                }
                                                                navController.navigate(nextRoute) {
                                                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                                                    launchSingleTop = true
                                                                    restoreState = true
                                                                }
                                                            } else if (totalDrag > 120f && currentIdx > 0) {
                                                                val prevRoute = when (currentIdx - 1) {
                                                                    0 -> Screens.Home.route
                                                                    1 -> Screens.Explore.route
                                                                    2 -> Screens.MoodAndGenres.route
                                                                    else -> Screens.Home.route
                                                                }
                                                                navController.navigate(prevRoute) {
                                                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                                                    launchSingleTop = true
                                                                    restoreState = true
                                                                }
                                                            }
                                                        }
                                                    )
                                                }
                                            } else Modifier
                                        )
                                ) {
                                    val tabOpenedFromShortcut = remember {
                                        when (intent?.action) {
                                            ACTION_LIBRARY -> NavigationTab.LIBRARY
                                            ACTION_SEARCH -> NavigationTab.SEARCH
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
                                            .padding(bottom = if (nowPlaying != null && !playerBottomSheetState.isDismissed) 72.dp else 0.dp)
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
                                                fadeIn(tween(250))
                                            } else {
                                                fadeIn(tween(250)) + slideInHorizontally { it / 2 }
                                            }
                                        },
                                        exitTransition = {
                                            if (initialState.destination.route in topLevelScreens && targetState.destination.route in topLevelScreens) {
                                                fadeOut(tween(200))
                                            } else {
                                                fadeOut(tween(200)) + slideOutHorizontally { -it / 2 }
                                            }
                                        },
                                        popEnterTransition = {
                                            if ((initialState.destination.route in topLevelScreens || initialState.destination.route?.startsWith("search/") == true) && targetState.destination.route in topLevelScreens) {
                                                fadeIn(tween(250))
                                            } else {
                                                fadeIn(tween(250)) + slideInHorizontally { -it / 2 }
                                            }
                                        },
                                        popExitTransition = {
                                            if ((initialState.destination.route in topLevelScreens || initialState.destination.route?.startsWith("search/") == true) && targetState.destination.route in topLevelScreens) {
                                                fadeOut(tween(200))
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
                                        navigationBarHeight = if (shouldShowNavigationBar && !useRail) 88.dp else 0.dp
                                    )
                                }
                            }

                            BottomSheetMenu(
                                state = menuState,
                            )

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
        }
    }

    private fun handleDeepLinkIntent(intent: Intent, navController: NavHostController) {
        val uri = intent.data ?: intent.extras?.getString(Intent.EXTRA_TEXT)?.toUri() ?: return
        val coroutineScope = lifecycleScope

        val authority = uri.authority?.lowercase()
        val isSupportedScheme = uri.scheme.equals("armory", ignoreCase = true) || uri.scheme.equals("OpenTune", ignoreCase = true)
        if (isSupportedScheme && authority == "together") {
            pendingTogetherJoinLink = uri.toString()
            startMusicServiceSafely()
            joinPendingTogetherIfReady()
            return
        }

        if (isSupportedScheme && authority == "login") {
            // navController.navigate(buildLoginRoute(uri.getQueryParameter(LOGIN_URL_ARGUMENT)))
            return
        }

        when (val path = uri.pathSegments.firstOrNull()) {
            "playlist" -> uri.getQueryParameter("list")?.let { playlistId ->
                if (playlistId.startsWith("OLAK5uy_")) {
                    coroutineScope.launch {
                        YouTube.albumSongs(playlistId).onSuccess { songs ->
                            songs.firstOrNull()?.album?.id?.let { browseId ->
                                navController.navigate("album/$browseId")
                            }
                        }.onFailure { reportException(it) }
                    }
                } else {
                    navController.navigate("online_playlist/$playlistId")
                }
            }

            "browse" -> uri.lastPathSegment?.let { browseId ->
                navController.navigate("album/$browseId")
            }

            "channel", "c" -> uri.lastPathSegment?.let { artistId ->
                navController.navigate("artist/$artistId")
            }

            else -> {
                val videoId = when {
                    path == "watch" -> uri.getQueryParameter("v")
                    uri.host == "youtu.be" -> uri.pathSegments.firstOrNull()
                    else -> null
                }

                val playlistId = uri.getQueryParameter("list")

                videoId?.let { vid ->
                    coroutineScope.launch {
                        val result = withContext(Dispatchers.IO) {
                            YouTube.queue(listOf(vid), playlistId)
                        }

                        result.onSuccess { queued ->
                            val mediaItem =
                                queued.firstOrNull { it.id == vid }?.toMediaItem()
                                    ?: queued.firstOrNull()?.toMediaItem()
                                    ?: MediaItem
                                        .Builder()
                                        .setMediaId(vid)
                                        .setUri(vid)
                                        .setCustomCacheKey(vid)
                                        .build()
                            pendingDeepLinkSong =
                                PendingDeepLinkSong(
                                    mediaItem = mediaItem,
                                )
                            startMusicServiceSafely()
                            playPendingDeepLinkSongIfReady()
                        }.onFailure {
                            reportException(it)
                        }
                    }
                }
            }
        }
    }

    private fun startMusicServiceSafely() {
        runCatching { startService(Intent(this, MusicService::class.java)) }
            .onFailure { reportException(it) }
    }

    private fun setSystemBarAppearance(isDark: Boolean) {
        WindowCompat.getInsetsController(window, window.decorView.rootView).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }
    }

    companion object {
        const val ACTION_SEARCH = "com.ruben.spotitube.action.SEARCH"
        const val ACTION_LIBRARY = "com.ruben.spotitube.action.LIBRARY"
    }
}

val LocalDatabase = staticCompositionLocalOf<MusicDatabase> { error("No database provided") }
val LocalPlayerConnection =
    staticCompositionLocalOf<PlayerConnection?> { error("No PlayerConnection provided") }
val LocalPlayerAwareWindowInsets =
    compositionLocalOf<WindowInsets> { error("No WindowInsets provided") }
val LocalDownloadUtil = staticCompositionLocalOf<DownloadUtil> { error("No DownloadUtil provided") }
val LocalSyncManager =
    staticCompositionLocalOf<com.ruben.spotitube.sync.SyncManager> { error("No SyncManager provided") }
val LocalLibraryStateHolder =
    staticCompositionLocalOf<com.ruben.spotitube.sync.LibraryStateHolder> { error("No LibraryStateHolder provided") }
val LocalSyncUtils = staticCompositionLocalOf<SyncUtils> { error("No SyncUtils provided") }