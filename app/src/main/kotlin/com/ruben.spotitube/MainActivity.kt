/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ruben.spotitube

import android.Manifest
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.navigation.NavHostController
import com.mikepenz.markdown.m3.Markdown
import com.ruben.spotitube.constants.AppLanguageKey
import com.ruben.spotitube.constants.DisableScreenshotKey
import com.ruben.spotitube.constants.LastNotifiedVersionKey
import com.ruben.spotitube.constants.SYSTEM_DEFAULT
import com.ruben.spotitube.constants.StopMusicOnTaskClearKey
import com.ruben.spotitube.db.MusicDatabase
import com.ruben.spotitube.playback.DownloadUtil
import com.ruben.spotitube.playback.MusicService
import com.ruben.spotitube.playback.MusicService.MusicBinder
import com.ruben.spotitube.playback.PlayerConnection
import com.ruben.spotitube.playback.queues.ListQueue
import com.ruben.spotitube.ui.MainScreen
import com.ruben.spotitube.ui.component.BottomSheetPageState
import com.ruben.spotitube.ui.component.MenuState
import com.ruben.spotitube.utils.DeepLinkHandler
import com.ruben.spotitube.utils.PreferenceStore
import com.ruben.spotitube.utils.SessionManager
import com.ruben.spotitube.utils.SyncUtils
import com.ruben.spotitube.utils.UpdateNotificationManager
import com.ruben.spotitube.utils.Updater
import com.ruben.spotitube.utils.dataStore
import com.ruben.spotitube.utils.get
import com.ruben.spotitube.utils.rememberPreference
import com.ruben.spotitube.utils.reportException
import com.ruben.spotitube.utils.setAppLocale
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    private val deepLinkHandler by lazy {
        DeepLinkHandler(
            scope = lifecycleScope,
            onPlaySong = { mediaItem ->
                pendingDeepLinkSong = PendingDeepLinkSong(mediaItem = mediaItem)
                playPendingDeepLinkSongIfReady()
            },
            startMusicService = { startMusicServiceSafely() },
        )
    }

    private var expandPlayerBottomSheet: (() -> Unit)? = null
    private var shouldExpandPlayerForDeepLink = false

    private fun playPendingDeepLinkSongIfReady() {
        val pending = pendingDeepLinkSong ?: return
        val connection = playerConnection ?: return
        pendingDeepLinkSong = null
        connection.playQueue(ListQueue(items = listOf(pending.mediaItem)))
        val expandPlayer = expandPlayerBottomSheet
        if (expandPlayer == null) {
            shouldExpandPlayerForDeepLink = true
        } else {
            expandPlayer()
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
        pendingIntent = intent
        if (::navController.isInitialized) {
            deepLinkHandler.handle(intent, navController)
            pendingIntent = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val mainActivityStartMs = com.ruben.spotitube.utils.PerfTiming.beginSection("MainActivity.toFirstFrame")
        val splashScreen = installSplashScreen()
        startMusicServiceSafely()
        super.onCreate(savedInstanceState)
        window.decorView.post {
            com.ruben.spotitube.utils.PerfTiming.endSection("MainActivity.toFirstFrame", mainActivityStartMs)
        }

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
            val downloadProgress by downloadUtil.progress.collectAsStateWithLifecycle()
            var showDownloadPopup by remember { mutableStateOf(false) }

            LaunchedEffect(downloadProgress.isActive, downloadProgress.total) {
                if (downloadProgress.isActive) {
                    showDownloadPopup = true
                } else if (downloadProgress.total > 0) {
                    delay(2500.milliseconds)
                    showDownloadPopup = false
                }
            }

            val notificationPermissionLauncher =
                rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                    if (isGranted) {
                        playerConnection?.service?.refreshPlaybackNotification()
                    }
                }

            var isUiReady by remember { mutableStateOf(false) }

            val lastNotifiedVersion by rememberPreference(LastNotifiedVersionKey, defaultValue = "")

            LaunchedEffect(lastNotifiedVersion) {
                if (lastNotifiedVersion.isNotBlank() && Updater.isNewerVersion(lastNotifiedVersion, BuildConfig.VERSION_NAME)) {
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
                if (Updater.isNewerVersion(latestVersionName, BuildConfig.VERSION_NAME)) {
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
                MainScreen(
                    database = database,
                    downloadUtil = downloadUtil,
                    syncUtils = syncUtils,
                    libraryStateHolder = libraryStateHolder,
                    syncManager = syncManager,
                    playerConnection = playerConnection,
                    latestVersionName = latestVersionName,
                    downloadProgress = downloadProgress,
                    showDownloadPopup = showDownloadPopup,
                    isUiReady = isUiReady,
                    bottomSheetPageState = bottomSheetPageState,
                    menuState = menuState,
                    onHomeVisible = onHomeVisible,
                    onNavControllerCreated = { this@MainActivity.navController = it },
                    onHandleDeepLinkIntent = { controller ->
                        if (pendingIntent != null) {
                            deepLinkHandler.handle(pendingIntent!!, controller)
                            pendingIntent = null
                        } else {
                            deepLinkHandler.handle(intent, controller)
                        }
                    },
                    onSetExpandPlayerBottomSheet = { expandPlayerBottomSheet = it },
                    consumeShouldExpandPlayerForDeepLink = {
                        if (shouldExpandPlayerForDeepLink) {
                            shouldExpandPlayerForDeepLink = false
                            true
                        } else {
                            false
                        }
                    },
                    window = window,
                    intent = intent,
                )
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

