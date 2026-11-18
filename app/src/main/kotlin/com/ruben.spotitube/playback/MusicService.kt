/*
 * ARMORY Project - Music Player (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



@file:Suppress("DEPRECATION")

package com.ruben.spotitube.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.os.Binder
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.datastore.preferences.core.edit
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.EVENT_POSITION_DISCONTINUITY
import androidx.media3.common.Player.EVENT_TIMELINE_CHANGED
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.STATE_IDLE
import androidx.media3.common.Timeline
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.PlaybackStats
import androidx.media3.exoplayer.analytics.PlaybackStatsListener
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import com.ruben.spotitube.constants.MediaSessionConstants.CommandToggleSaved
import com.ruben.spotitube.constants.MediaSessionConstants.CommandToggleStartRadio
import com.ruben.spotitube.constants.MediaSessionConstants.CommandToggleRepeatMode
import com.ruben.spotitube.constants.MediaSessionConstants.CommandToggleShuffle
import com.ruben.spotitube.MainActivity
import com.ruben.spotitube.R
import com.ruben.spotitube.constants.AudioCrossfadeDurationKey
import com.ruben.spotitube.constants.AudioNormalizationKey
import com.ruben.spotitube.constants.AudioOffload
import com.ruben.spotitube.constants.AudioQualityKey
import com.ruben.spotitube.constants.AutoDownloadOnSavedKey
import com.ruben.spotitube.constants.AutoLoadMoreKey
import com.ruben.spotitube.constants.AutoStartOnBluetoothKey
import com.ruben.spotitube.constants.HideVideoKey
import com.ruben.spotitube.constants.HistoryDuration
import com.ruben.spotitube.constants.MaxSongCacheSizeKey
import com.ruben.spotitube.constants.PauseListenHistoryKey
import com.ruben.spotitube.constants.PauseOnDeviceMuteKey
import com.ruben.spotitube.constants.PermanentShuffleKey
import com.ruben.spotitube.constants.PersistentQueueKey
import com.ruben.spotitube.constants.PlayerStreamClient
import com.ruben.spotitube.constants.PlayerStreamClientKey
import com.ruben.spotitube.constants.PlayerVolumeKey
import com.ruben.spotitube.constants.RepeatModeKey
import com.ruben.spotitube.constants.ShowLyricsKey
import com.ruben.spotitube.constants.SkipSilenceKey
import com.ruben.spotitube.constants.SmartTrimmerKey
import com.ruben.spotitube.db.MusicDatabase
import com.ruben.spotitube.db.entities.LyricsEntity
import com.ruben.spotitube.di.DownloadCache
import com.ruben.spotitube.di.PlayerCache
import com.ruben.spotitube.extensions.SilentHandler
import com.ruben.spotitube.extensions.collect
import com.ruben.spotitube.extensions.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import com.ruben.spotitube.extensions.currentMetadata
import com.ruben.spotitube.extensions.directorySizeBytes
import com.ruben.spotitube.extensions.mediaItems
import com.ruben.spotitube.extensions.metadata
import com.ruben.spotitube.extensions.setOffloadEnabled
import com.ruben.spotitube.extensions.toMediaItem
import com.ruben.spotitube.extensions.toPersistQueue
import com.ruben.spotitube.extensions.toQueue
import com.ruben.spotitube.innertube.models.WatchEndpoint
import com.ruben.spotitube.lyrics.LyricsHelper
import com.ruben.spotitube.lyrics.LyricsPreloadManager
import com.ruben.spotitube.models.ContinueListeningEntry
import com.ruben.spotitube.models.ContinueListeningStore
import com.ruben.spotitube.models.MediaMetadata
import com.ruben.spotitube.models.PersistPlayerState
import com.ruben.spotitube.models.PersistQueue
import com.ruben.spotitube.playback.queues.EmptyQueue
import com.ruben.spotitube.playback.queues.ListQueue
import com.ruben.spotitube.playback.queues.LocalAlbumRadio
import com.ruben.spotitube.playback.queues.Queue
import com.ruben.spotitube.playback.queues.YouTubeQueue
import com.ruben.spotitube.playback.queues.filterVideo
import com.ruben.spotitube.sync.LibraryStateHolder
import com.ruben.spotitube.sync.SyncManager
import com.ruben.spotitube.utils.NetworkConnectivityObserver
import com.ruben.spotitube.utils.SyncUtils
import com.ruben.spotitube.utils.dataStore
import com.ruben.spotitube.utils.enumPreference
import com.ruben.spotitube.utils.get
import com.ruben.spotitube.utils.reportException
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.math.min
import kotlin.math.pow
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@AndroidEntryPoint
class MusicService :
    MediaLibraryService(),
    Player.Listener,
    PlaybackStatsListener.Callback {
    @Inject
    lateinit var database: MusicDatabase

    @Inject
    lateinit var lyricsHelper: LyricsHelper

    @Inject
    lateinit var syncUtils: SyncUtils

    @Inject
    lateinit var syncManager: SyncManager

    @Inject
    lateinit var libraryStateHolder: LibraryStateHolder

    @Inject
    lateinit var mediaLibrarySessionCallback: MediaLibrarySessionCallback

    private val audioFocusController by lazy {
        AudioFocusController(
            context = this,
            player = { player },
            audioFocusVolumeFactor = audioFocusVolumeFactor,
        )
    }

    private val deviceStateController by lazy {
        DeviceStateController(
            context = this,
            player = { player },
            scope = { scope },
            isTogetherGuest = { isTogetherGuestSession() },
        )
    }

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Timber.e(throwable, "Unhandled exception in MusicService coroutine scope")
    }
    private var scopeJob = SupervisorJob()
    private var scope = CoroutineScope(Dispatchers.Main + scopeJob + coroutineExceptionHandler)
    private var ioScope = CoroutineScope(Dispatchers.IO + scopeJob + coroutineExceptionHandler)
    private val binder = MusicBinder()
    private var hasBoundClients = false
    private var idleStopJob: Job? = null

    private lateinit var connectivityManager: ConnectivityManager
    lateinit var connectivityObserver: NetworkConnectivityObserver
    val waitingForNetworkConnection = MutableStateFlow(false)
    private val isNetworkConnected = MutableStateFlow(false)
    private var isCurrentSongSaved: Boolean = false

    private val streamDataSourceProvider by lazy {
        StreamDataSourceProvider(
            context = this,
            database = database,
            player = { player },
            scope = { scope },
            playerCache = { playerCache },
            downloadCache = { downloadCache },
            connectivityManager = { connectivityManager },
            audioQuality = { audioQuality },
            preferredStreamClient = { preferredStreamClient },
        )
    }

    private val audioQuality by enumPreference(
        this,
        AudioQualityKey,
        com.ruben.spotitube.constants.AudioQuality.AUTO
    )
    private val preferredStreamClient by enumPreference(
        this,
        PlayerStreamClientKey,
        PlayerStreamClient.ANDROID_VR
    )

    private val streamErrorHandler by lazy {
        StreamErrorHandler(
            player = { player },
            scope = { scope },
            isNetworkConnected = { isNetworkConnected.value },
            downloadCache = { downloadCache },
            playerCache = { playerCache },
            preferredStreamClient = { preferredStreamClient },
            onWaitForNetwork = { waitOnNetworkError() },
            onSkipOnError = { skipOnError() },
            onStopOnError = { stopOnError() },
        )
    }

    private var currentQueue: Queue = EmptyQueue
    var queueTitle: String? = null
    private val persistentStateStore by lazy { PersistentStateStore(filesDir) }
    @Volatile
    private var suppressAutoPlayback = false

    val currentMediaMetadata = MutableStateFlow<com.ruben.spotitube.models.MediaMetadata?>(null)
    val queueRestoreCompleted = MutableStateFlow(false)
    private val currentFormat =
        currentMediaMetadata.flatMapLatest { mediaMetadata ->
            database.format(mediaMetadata?.id)
        }.flowOn(Dispatchers.IO)

    private val normalizeFactor = MutableStateFlow(1f)
    var playerVolume = MutableStateFlow(1f)
    private val audioFocusVolumeFactor = MutableStateFlow(1f)
    private val playbackFadeFactor = MutableStateFlow(1f)
    private val crossfadeDurationMs = MutableStateFlow(0)
    private val audioNormalizationEnabled = MutableStateFlow(true)
    private var crossfadeAudio: CrossfadeAudio? = null
    private var lyricsPreloadManager: LyricsPreloadManager? = null



    lateinit var sleepTimer: SleepTimer

    @Inject
    @PlayerCache
    lateinit var playerCache: Cache

    @Inject
    @DownloadCache
    lateinit var downloadCache: Cache

    lateinit var player: ExoPlayer
    private var mediaSession: MediaLibrarySession? = null

    private val audioEffectsController by lazy {
        AudioEffectsController(
            context = this,
            player = { player },
            scope = { scope },
            ioScope = { ioScope },
        )
    }

    val eqCapabilities: StateFlow<EqCapabilities?>
        get() = audioEffectsController.eqCapabilities

    private val automixController by lazy {
        AutomixController(
            context = this,
            player = { player },
            scope = { scope },
            isSuppressed = { suppressAutoPlayback },
            addToQueue = { items -> addToQueue(items) },
            playNext = { items -> playNext(items) },
        )
    }

    val automixItems: StateFlow<List<MediaItem>>
        get() = automixController.automixItems

    val automixLoading: StateFlow<Boolean>
        get() = automixController.automixLoading

    val automixError: StateFlow<String?>
        get() = automixController.automixError

    val autoAddedMediaIds: MutableSet<String>
        get() = automixController.autoAddedMediaIds

    private var consecutivePlaybackErr = 0

    val maxSafeGainFactor = 1.414f // +3 dB
    @Volatile
    private var hasCalledStartForeground = false

    private val musicTogetherCoordinator by lazy {
        MusicTogetherCoordinator(
            context = this,
            player = { player },
            scope = { scope },
            ioScope = { ioScope },
            ensureScopesActive = { ensureScopesActive() },
            playNext = { items -> playNext(items) },
            addToQueue = { items -> addToQueue(items) },
            onRemoteQueueReplaced = { queue ->
                suppressAutoPlayback = false
                currentQueue = queue
                queueTitle = null
            },
        )
    }

    val togetherSessionState: StateFlow<com.ruben.spotitube.together.TogetherSessionState>
        get() = musicTogetherCoordinator.togetherSessionState

    val togetherAvatars: StateFlow<Map<String, String>>
        get() = musicTogetherCoordinator.togetherAvatars


    private fun ensureStartedAsForeground() {

        val notification =
            try {
                val contentIntent =
                    PendingIntent.getActivity(
                        this,
                        0,
                        Intent(this, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )

                val builder = NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.armory_logo)
                    .setContentTitle(getString(R.string.music_player))
                    .setContentText(getString(R.string.app_name))
                    .setContentIntent(contentIntent)
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)

                mediaSession?.let { builder.setStyle(MediaStyleNotificationHelper.MediaStyle(it)) }

                builder.build()
            } catch (e: Exception) {
                reportException(e)
                NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.armory_logo)
                    .setContentTitle("ARMORY")
                    .build()
            }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            hasCalledStartForeground = true
        } catch (e: Exception) {
            reportException(e)
        }
    }

    private fun promoteToStartedService() {
        runCatching { startService(Intent(this, MusicService::class.java)) }
            .onFailure { reportException(it) }
    }

    private fun cancelIdleStop() {
        idleStopJob?.cancel()
        idleStopJob = null
    }

    private fun stopForegroundAndSelf() {
        cancelIdleStop()
        runCatching {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        hasCalledStartForeground = false
        stopSelf()
    }

    private fun scheduleStopIfIdle() {
        if (hasBoundClients) return
        val state = player.playbackState
        val keepAlive =
            player.isPlaying ||
                (player.playWhenReady && (state == Player.STATE_BUFFERING || state == Player.STATE_READY))
        if (keepAlive) {
            cancelIdleStop()
            return
        }
        val togetherIdle = musicTogetherCoordinator.togetherSessionState.value is com.ruben.spotitube.together.TogetherSessionState.Idle
        if (!togetherIdle) {
            cancelIdleStop()
            return
        }

        val delayMs =
            when (state) {
                Player.STATE_READY -> 5 * 60_000L
                Player.STATE_ENDED, STATE_IDLE -> 30_000L
                else -> 60_000L
            }

        cancelIdleStop()
        idleStopJob =
            scope.launch {
                delay(delayMs.milliseconds)
                if (hasBoundClients) return@launch
                val currentState = player.playbackState
                val shouldKeep =
                    player.isPlaying ||
                        (player.playWhenReady && (currentState == Player.STATE_BUFFERING || currentState == Player.STATE_READY))
                if (shouldKeep) return@launch
                if (togetherSessionState.value !is com.ruben.spotitube.together.TogetherSessionState.Idle) return@launch
                stopForegroundAndSelf()
            }
    }

    override fun onCreate() {
        super.onCreate()
        ensureScopesActive()
        ContinueListeningStore.init(filesDir)

        try {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.music_player),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        } catch (e: Exception) {
            reportException(e)
        }

        player =
            ExoPlayer
                .Builder(this)
                .setMediaSourceFactory(streamDataSourceProvider.createMediaSourceFactory())
                .setRenderersFactory(streamDataSourceProvider.createRenderersFactory())
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    false,
                ).setSeekBackIncrementMs(5000)
                .setSeekForwardIncrementMs(5000)
                .setDeviceVolumeControlEnabled(true)
                .build()
                .apply {
                    addListener(this@MusicService)
                    sleepTimer = SleepTimer(scope, this)
                    addListener(sleepTimer)
                    addAnalyticsListener(PlaybackStatsListener(false, this@MusicService))
                    setOffloadEnabled(false)
                }

        audioFocusController.setupAudioFocusRequest()

        mediaLibrarySessionCallback.apply {
            toggleSongSaved = { toggleSongSaved() }
            toggleStartRadio = { toggleStartRadio() }
            toggleLibrary = { toggleSongSaved() }
            onCustomCommandHandled = {
                updateNotification()
            }
        }
        mediaSession =
            MediaLibrarySession
                .Builder(this, player, mediaLibrarySessionCallback)
                .setSessionActivity(
                    PendingIntent.getActivity(
                        this,
                        0,
                        Intent(this, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE,
                    ),
                ).build()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider(
                this,
                { NOTIFICATION_ID },
                CHANNEL_ID,
                R.string.music_player
            ).apply {
                setSmallIcon(R.drawable.armory_logo)
            }
        )
        
        updateNotification()
        player.repeatMode = REPEAT_MODE_OFF

        scope.launch {
            currentMediaMetadata
                .flatMapLatest { metadata ->
                    if (metadata == null) kotlinx.coroutines.flow.flowOf(false)
                    else libraryStateHolder.isSavedSong(metadata.id)
                }
                .distinctUntilChanged()
                .collectLatest(scope) { isSaved ->
                    isCurrentSongSaved = isSaved
                    updateNotification()
                }
        }

        scope.launch(Dispatchers.IO) {
            val prefs = dataStore.data.first()
            val repeatMode = prefs[RepeatModeKey] ?: REPEAT_MODE_OFF
            val volume = (prefs[PlayerVolumeKey] ?: 1f).coerceIn(0f, 1f)
            val offload = prefs[AudioOffload] ?: false
            withContext(Dispatchers.Main) {
                player.repeatMode = repeatMode
                playerVolume.value = volume
                updateAudioOffload(offload)
            }
        }

        connectivityManager = getSystemService()!!
        connectivityObserver = NetworkConnectivityObserver(this)

        scope.launch {
            connectivityObserver.networkStatus.collect { isConnected ->
                isNetworkConnected.value = isConnected
                if (isConnected && waitingForNetworkConnection.value) {
                    waitingForNetworkConnection.value = false
                    if (player.currentMediaItem != null && player.playWhenReady &&
                        player.playbackState == STATE_IDLE
                    ) {
                        player.prepare()
                        player.play()
                    }
                }
            }
        }

        combine(playerVolume, normalizeFactor, audioFocusVolumeFactor, playbackFadeFactor) { playerVolume, normalizeFactor, audioFocusVolumeFactor, playbackFadeFactor ->
            playerVolume * normalizeFactor * audioFocusVolumeFactor * playbackFadeFactor
        }.collectLatest(scope) { finalVolume ->
            player.volume = finalVolume
        }

        playerVolume.debounce(1000.milliseconds).collect(ioScope) { volume ->
            dataStore.edit { settings ->
                settings[PlayerVolumeKey] = volume
            }
        }

        combine(
            currentMediaMetadata.distinctUntilChangedBy { it?.id },
            dataStore.data.map { it[ShowLyricsKey] ?: false }.distinctUntilChanged(),
        ) { mediaMetadata, showLyrics ->
            mediaMetadata to showLyrics
        }.collectLatest(ioScope) { (mediaMetadata, showLyrics) ->
            if (showLyrics && mediaMetadata != null && database.lyrics(mediaMetadata.id)
                    .first() == null
            ) {
                val lyrics = lyricsHelper.getLyrics(mediaMetadata)
                database.query {
                    upsert(
                        LyricsEntity(
                            id = mediaMetadata.id,
                            lyrics = lyrics,
                        ),
                    )
                }
            }
        }

        dataStore.data
            .map { it[SkipSilenceKey] ?: false }
            .distinctUntilChanged()
            .collectLatest(scope) {
                player.skipSilenceEnabled = it
            }

        dataStore.data
            .map { it[PauseOnDeviceMuteKey] ?: false }
            .distinctUntilChanged()
            .collectLatest(scope) { enabled ->
                deviceStateController.setPauseOnDeviceMute(enabled)
            }

        dataStore.data
            .map { it[AutoStartOnBluetoothKey] ?: false }
            .distinctUntilChanged()
            .collectLatest(scope) { enabled ->
                deviceStateController.setAutoStartOnBluetooth(enabled)
            }

        dataStore.data
            .map { it[AudioOffload] ?: false }
            .distinctUntilChanged()
            .collectLatest(scope) { enabled ->
                updateAudioOffload(enabled)
                if (enabled) {
                    val skipSilenceEnabled = dataStore.get(SkipSilenceKey, false)
                    if (skipSilenceEnabled) {
                        dataStore.edit { it[SkipSilenceKey] = false }
                        player.skipSilenceEnabled = false
                    }
                    val crossfadeSeconds = dataStore.get(AudioCrossfadeDurationKey, 0)
                    if (crossfadeSeconds != 0) {
                        dataStore.edit { it[AudioCrossfadeDurationKey] = 0 }
                    }
                }
            }
        
        dataStore.data
            .map { (it[AudioCrossfadeDurationKey] ?: 0) * 1000 }
            .distinctUntilChanged()
            .collectLatest(scope) {
                crossfadeDurationMs.value = it
            }

        crossfadeAudio =
            CrossfadeAudio(
                player = player,
                database = database,
                crossfadeDurationMs = crossfadeDurationMs,
                playbackFadeFactor = playbackFadeFactor,
                playerVolume = playerVolume,
                audioFocusVolumeFactor = audioFocusVolumeFactor,
                audioNormalizationEnabled = audioNormalizationEnabled,
                maxSafeGainFactor = maxSafeGainFactor,
                overlapPlayerFactory = {
                    ExoPlayer
                        .Builder(this)
                        .setMediaSourceFactory(streamDataSourceProvider.createMediaSourceFactory())
                        .setRenderersFactory(streamDataSourceProvider.createRenderersFactory())
                        .setHandleAudioBecomingNoisy(false)
                        .setWakeMode(C.WAKE_MODE_NETWORK)
                        .setAudioAttributes(
                            AudioAttributes
                                .Builder()
                                .setUsage(C.USAGE_MEDIA)
                                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                                .build(),
                            false,
                        ).setSeekBackIncrementMs(5000)
                        .setSeekForwardIncrementMs(5000)
                        .build()
                },
                onCrossfadeStart = { mediaItem ->
                    val metadata = mediaItem.metadata
                    currentMediaMetadata.value = metadata
                }
            ).also { it.start(scope) }

        // Initialize lyrics pre-load manager asíncronamente para no bloquear el inicio del servicio
        scope.launch {
            lyricsPreloadManager = LyricsPreloadManager(
                context = this@MusicService,
                database = database,
                networkConnectivity = connectivityObserver,
            )
        }

        dataStore.data
            .map(audioEffectsController::readEqSettingsFromPrefs)
            .distinctUntilChanged()
            .collectLatest(scope) { settings ->
                audioEffectsController.onSettingsChanged(settings)
            }

        combine(
            currentFormat,
            dataStore.data
                .map { it[AudioNormalizationKey] ?: true }
                .distinctUntilChanged(),
        ) { format, normalizeAudio ->
            format to normalizeAudio
        }.collectLatest(scope) { (format, normalizeAudio) ->
            audioNormalizationEnabled.value = normalizeAudio
            Timber.tag("AudioNormalization").d("Audio normalization enabled: $normalizeAudio")
            Timber.tag("AudioNormalization").d("Format loudnessDb: ${format?.loudnessDb}, perceptualLoudnessDb: ${format?.perceptualLoudnessDb}")
            
            normalizeFactor.value =
                if (normalizeAudio) {
                    // Usamos loudnessDb o perceptualLoudnessDb de YouTube si existen.
                    // Si ambos son nulos (por ejemplo, pistas locales o sin metadatos de ganancia remotos),
                    // aplicamos un valor de 0.0dB (factor 1.0f) para evitar aumentos de volumen indeseados.
                    val loudness = format?.loudnessDb ?: format?.perceptualLoudnessDb ?: 0.0
                    
                    val loudnessDb = loudness.toFloat()
                    var factor = 10f.pow(-loudnessDb / 20)
                    
                    Timber.tag("AudioNormalization").d("Calculated raw normalization factor: $factor (from loudness: $loudnessDb)")
                    
                    if (factor > 1f) {
                        factor = min(factor, maxSafeGainFactor)
                        Timber.tag("AudioNormalization").d("Factor capped at maxSafeGainFactor: $factor")
                    }
                    
                    Timber.tag("AudioNormalization").i("Applying normalization factor: $factor")
                    factor
                } else {
                    Timber.tag("AudioNormalization").d("Normalization disabled - using factor 1.0")
                    1f
                }
        }


        dataStore.data
            .map { prefs ->
                (prefs[SmartTrimmerKey] ?: false) to (prefs[MaxSongCacheSizeKey] ?: 1024)
            }
            .debounce(300.milliseconds)
            .distinctUntilChanged()
            .collectLatest(ioScope) { (enabled, maxSongCacheSizeMb) ->
                if (!enabled) return@collectLatest
                if (maxSongCacheSizeMb <= 0) return@collectLatest
                val bytesPerMb = 1024L * 1024L
                val safeSizeMb = maxSongCacheSizeMb.toLong().coerceAtMost(Long.MAX_VALUE / bytesPerMb)
                val limitBytes = safeSizeMb * bytesPerMb
                trimPlayerCacheToBytes(limitBytes)
            }

        scope.launch(Dispatchers.IO) {
            if (dataStore.get(PersistentQueueKey, true)) {
                persistentStateStore.readPersistentObject<PersistQueue>(PERSISTENT_QUEUE_FILE)
                    ?.let { persistedQueue ->
                    restorePersistentQueue(persistedQueue)
                }
                persistentStateStore.readPersistentObject<PersistQueue>(PERSISTENT_AUTOMIX_FILE)
                    ?.let { persistedAutomix ->
                    val items = persistedAutomix.items.map { it.toMediaItem() }
                    withContext(Dispatchers.Main) {
                        automixController.automixItems.value = items
                        automixController.automixSeedMediaId = player.currentMetadata?.id?.trim()?.takeIf { it.isNotBlank() }
                    }
                }
                
                persistentStateStore.readPersistentObject<PersistPlayerState>(PERSISTENT_PLAYER_STATE_FILE)
                    ?.let { playerState ->
                    delay(1000.milliseconds)
                    withContext(Dispatchers.Main) {
                        player.repeatMode = playerState.repeatMode
                        player.shuffleModeEnabled = playerState.shuffleModeEnabled
                        playerVolume.value = playerState.volume
                        
                        if (player.mediaItemCount > 0) {
                            val index =
                                if (playerState.currentMediaItemIndex in 0 until player.mediaItemCount) {
                                    playerState.currentMediaItemIndex
                                } else {
                                    player.currentMediaItemIndex.coerceIn(0, player.mediaItemCount - 1)
                                }
                            player.seekTo(index, playerState.currentPosition)
                        }
                        
                        currentMediaMetadata.value = player.currentMetadata
                        refreshPlaybackNotification()
                    }
                }
            }
            withContext(Dispatchers.Main) {
                queueRestoreCompleted.value = true
            }
        }

        // Save queue periodically to prevent queue loss from crash or force kill
        // Save queue periodically to prevent queue loss from crash or force kill
        scope.launch {
            while (isActive) {
                val interval = if (player.isPlaying) 10.seconds else 30.seconds
                delay(interval)
                val shouldSave = withContext(Dispatchers.IO) { dataStore.get(PersistentQueueKey, true) }
                if (shouldSave) {
                    saveQueueToDisk()
                }
            }
        }
    }

    private fun ensureScopesActive() {
        if (!scopeJob.isActive) {
            scopeJob = SupervisorJob()
        }
        if (!scope.isActive) {
            scope = CoroutineScope(Dispatchers.Main + scopeJob + coroutineExceptionHandler)
        }
        if (!ioScope.isActive) {
            ioScope = CoroutineScope(Dispatchers.IO + scopeJob + coroutineExceptionHandler)
        }
    }

    private suspend fun restorePersistentQueue(persistedQueue: PersistQueue) {
        val restoredQueue = persistedQueue.toQueue()
        val hideVideo = dataStore.get(HideVideoKey, false)
        val initialStatus =
            restoredQueue
                .getInitialStatus()
                .filterVideo(hideVideo)

        withContext(Dispatchers.Main) {
            currentQueue = restoredQueue
            queueTitle = initialStatus.title

            val items = initialStatus.items
            if (items.isEmpty()) {
                return@withContext
            }

            val fullIndex = initialStatus.mediaItemIndex.coerceIn(0, items.lastIndex)
            val windowStart = (fullIndex - 20).coerceAtLeast(0)
            val windowEnd = (fullIndex + 50).coerceAtMost(items.size)

            val initialChunk = items.subList(windowStart, windowEnd)
            val relativeIndex = (fullIndex - windowStart).coerceIn(0, initialChunk.lastIndex)

            player.setMediaItems(
                initialChunk,
                relativeIndex,
                initialStatus.position,
            )
            player.prepare()
            player.playWhenReady = false
            currentMediaMetadata.value = player.currentMetadata
            refreshPlaybackNotification()

            if (items.size > initialChunk.size) {
                scope.launch(SilentHandler) {
                    delay(2000.milliseconds)
                    if (!isActive || player.mediaItemCount == 0) return@launch
                    if (windowStart > 0) {
                        player.addMediaItems(0, items.subList(0, windowStart))
                    }
                    if (windowEnd < items.size) {
                        player.addMediaItems(items.subList(windowEnd, items.size))
                    }
                }
            }
        }
    }


    fun hasAudioFocusForPlayback(): Boolean {
        return audioFocusController.hasAudioFocusForPlayback()
    }

    private fun isTogetherGuestSession(): Boolean {
        val joined = togetherSessionState.value as? com.ruben.spotitube.together.TogetherSessionState.Joined
        return joined?.role is com.ruben.spotitube.together.TogetherRole.Guest
    }

    private fun waitOnNetworkError() {
        waitingForNetworkConnection.value = true
    }

    private fun skipOnError() {
        /**
         * Auto skip to the next media item on error.
         *
         * To prevent a "runaway diesel engine" scenario, force the user to take action after
         * too many errors come up too quickly. Pause to show player "stopped" state
         */
        consecutivePlaybackErr += 2
        val nextWindowIndex = player.nextMediaItemIndex

        if (consecutivePlaybackErr <= MAX_CONSECUTIVE_ERR && nextWindowIndex != C.INDEX_UNSET) {
            player.seekTo(nextWindowIndex, C.TIME_UNSET)
            player.prepare()
            player.play()
            return
        }

        player.pause()
        consecutivePlaybackErr = 0
    }

    private fun stopOnError() {
        player.pause()
    }

    private fun updateNotification() {
        try {
            val mediaMetadata = currentMediaMetadata.value
            val isSaved = isCurrentSongSaved
            val customLayout = listOf(
                CommandButton
                    .Builder()
                    .setDisplayName(
                        getString(
                            if (isSaved) {
                                R.string.action_remove_saved
                            } else {
                                R.string.action_saved
                            },
                        ),
                    )
                    .setIconResId(if (isSaved) R.drawable.favorite else R.drawable.favorite_border)
                    .setSessionCommand(CommandToggleSaved)
                    .setEnabled(mediaMetadata != null)
                    .build(),
                CommandButton
                    .Builder()
                    .setDisplayName(
                        getString(
                            when (player.repeatMode) {
                                REPEAT_MODE_OFF -> R.string.repeat_mode_off
                                Player.REPEAT_MODE_ONE -> R.string.repeat_mode_one
                                Player.REPEAT_MODE_ALL -> R.string.repeat_mode_all
                                else -> R.string.repeat_mode_off
                            },
                        ),
                    ).setIconResId(
                        when (player.repeatMode) {
                            REPEAT_MODE_OFF -> R.drawable.repeat
                            Player.REPEAT_MODE_ONE -> R.drawable.repeat_one_on
                            Player.REPEAT_MODE_ALL -> R.drawable.repeat_on
                            else -> R.drawable.repeat
                        },
                    ).setSessionCommand(CommandToggleRepeatMode)
                    .build(),
                CommandButton
                    .Builder()
                    .setDisplayName(getString(if (player.shuffleModeEnabled) R.string.action_shuffle_off else R.string.action_shuffle_on))
                    .setIconResId(if (player.shuffleModeEnabled) R.drawable.shuffle_on else R.drawable.shuffle)
                    .setSessionCommand(CommandToggleShuffle)
                    .build(),
                CommandButton.Builder()
                    .setDisplayName(getString(R.string.start_radio))
                    .setIconResId(R.drawable.radio)
                    .setSessionCommand(CommandToggleStartRadio)
                    .setEnabled(mediaMetadata != null)
                    .build(),
            )
            mediaSession?.setCustomLayout(customLayout)
        } catch (e: Exception) {
            reportException(e)
        }
    }

    fun refreshPlaybackNotification() {
        updateNotification()
        mediaSession?.let { session ->
            runCatching { super.onUpdateNotification(session, hasCalledStartForeground) }
                .onFailure { reportException(it) }
        }
    }



    fun playQueue(
        queue: Queue,
        playWhenReady: Boolean = true,
    ) {
        val preload = queue.preloadItem
        val joined = musicTogetherCoordinator.togetherSessionState.value as? com.ruben.spotitube.together.TogetherSessionState.Joined
        if (!musicTogetherCoordinator.isTogetherApplyingRemote && joined?.role is com.ruben.spotitube.together.TogetherRole.Guest) {
            if (!joined.roomState.settings.allowGuestsToControlPlayback) {
                musicTogetherCoordinator.showTogetherNotice(getString(R.string.not_allowed), key = "GUEST_PLAYQUEUE_DISABLED")
                return
            }
            ensureScopesActive()
            scope.launch(SilentHandler) {
                val initialStatus =
                    withContext(Dispatchers.IO) {
                        queue.getInitialStatus()
                            .filterVideo(dataStore.get(HideVideoKey, false))
                    }

                val targetItem =
                    initialStatus.items.getOrNull(initialStatus.mediaItemIndex)
                        ?: preload?.toMediaItem()

                val meta = targetItem?.metadata
                val trackId =
                    meta?.id?.trim().orEmpty().ifBlank {
                        targetItem?.mediaId?.trim().orEmpty()
                    }
                if (trackId.isBlank()) {
                    musicTogetherCoordinator.showTogetherNotice(getString(R.string.not_allowed), key = "GUEST_PLAYQUEUE_NO_TRACK")
                    return@launch
                }

                val track =
                    com.ruben.spotitube.together.TogetherTrack(
                        id = trackId,
                        title = meta?.title ?: trackId,
                        artists = meta?.artists?.map { it.name }.orEmpty(),
                        durationSec = meta?.duration ?: -1,
                        thumbnailUrl = meta?.thumbnailUrl,
                    )

                val ops =
                    com.ruben.spotitube.together.TogetherGuestPlaybackPlanner.planPlayTrackNow(
                        roomState = joined.roomState,
                        track = track,
                        positionMs = initialStatus.position,
                        playWhenReady = playWhenReady,
                    )

                if (ops.isEmpty()) {
                    musicTogetherCoordinator.showTogetherNotice(getString(R.string.not_allowed), key = "GUEST_PLAYQUEUE_BLOCKED")
                    return@launch
                }

                musicTogetherCoordinator.showTogetherNotice(getString(R.string.together_requesting_song_change), key = "GUEST_PLAYQUEUE_REQUEST")
                ops.forEach { op ->
                    when (op) {
                        is com.ruben.spotitube.together.TogetherGuestOp.Control -> musicTogetherCoordinator.requestTogetherControl(op.action)
                        is com.ruben.spotitube.together.TogetherGuestOp.AddTrack -> musicTogetherCoordinator.requestTogetherAddTrack(op.track, op.mode)
                    }
                }
            }
            return
        }
        if (playWhenReady) {
            cancelIdleStop()
            promoteToStartedService()
            ensureStartedAsForeground()
        }
        ensureScopesActive()
        suppressAutoPlayback = false
        currentQueue = queue
        queueTitle = null
        val permanentShuffle = dataStore.get(PermanentShuffleKey, false)
        if (!permanentShuffle) {
            player.shuffleModeEnabled = false
        }
        
        automixController.clearAutomix()
        if (preload != null) {
            player.setMediaItem(preload.toMediaItem())
            player.prepare()
            player.playWhenReady = playWhenReady
            refreshPlaybackNotification()
        }
        scope.launch(SilentHandler) {
            val initialStatus =
                withContext(Dispatchers.IO) {
                    queue.getInitialStatus().filterVideo(dataStore.get(HideVideoKey, false))
                }
            if (initialStatus.title != null) {
                queueTitle = initialStatus.title
            }
            if (initialStatus.items.isEmpty()) return@launch
            if (preload != null) {
                player.addMediaItems(
                    0,
                    initialStatus.items.subList(0, initialStatus.mediaItemIndex)
                )
                player.addMediaItems(
                    initialStatus.items.subList(
                        initialStatus.mediaItemIndex + 1,
                        initialStatus.items.size
                    )
                )
                if (player.shuffleModeEnabled) {
                    applyCurrentFirstShuffleOrder()
                }
            } else {
                val items = initialStatus.items
                val index = initialStatus.mediaItemIndex
                
                // Chunk Loading: Only load a window around the current item initially
                // to prevent blocking the Main Thread for seconds with large queues.
                val windowStart = (index - 20).coerceAtLeast(0)
                val windowEnd = (index + 50).coerceAtMost(items.size)
                
                val initialChunk = items.subList(windowStart, windowEnd)
                val relativeIndex = index - windowStart
                
                player.setMediaItems(
                    initialChunk,
                    if (relativeIndex > 0) relativeIndex else 0,
                    initialStatus.position,
                )
                player.prepare()
                player.playWhenReady = playWhenReady
                if (player.shuffleModeEnabled) {
                    applyCurrentFirstShuffleOrder()
                }
                refreshPlaybackNotification()
                
                // Defer loading the rest of the queue
                if (items.size > initialChunk.size) {
                    scope.launch(SilentHandler) {
                        try {
                            delay(2000.milliseconds) // Allow UI to settle
                            if (!isActive) return@launch
                            
                            // Add preceding items
                            if (windowStart > 0) {
                                val startChunk = items.subList(0, windowStart)
                                player.addMediaItems(0, startChunk)
                            }
                            
                            // Add succeeding items
                            if (windowEnd < items.size) {
                                val endChunk = items.subList(windowEnd, items.size)
                                player.addMediaItems(endChunk)
                            }

                            if (player.shuffleModeEnabled) {
                                applyCurrentFirstShuffleOrder()
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to load deferred queue items")
                        }
                    }
                }
            }
        }
    }

    private fun applyCurrentFirstShuffleOrder() {
        val count = player.mediaItemCount
        if (count <= 1) return
        val currentIndex = player.currentMediaItemIndex.coerceIn(0, count - 1)
        val shuffledIndices = IntArray(count) { it }
        shuffledIndices.shuffle()
        val currentPos = shuffledIndices.indexOf(currentIndex)
        if (currentPos >= 0) {
            shuffledIndices[currentPos] = shuffledIndices[0]
        }
        shuffledIndices[0] = currentIndex
        player.shuffleOrder = DefaultShuffleOrder(shuffledIndices, System.currentTimeMillis())
    }

    fun startRadioSeamlessly() {
        val joined = musicTogetherCoordinator.togetherSessionState.value as? com.ruben.spotitube.together.TogetherSessionState.Joined
        if (!musicTogetherCoordinator.isTogetherApplyingRemote && joined?.role is com.ruben.spotitube.together.TogetherRole.Guest) {
            if (!joined.roomState.settings.allowGuestsToControlPlayback) {
                musicTogetherCoordinator.showTogetherNotice(getString(R.string.not_allowed), key = "GUEST_RADIO_DISABLED")
                return
            }
            musicTogetherCoordinator.showTogetherNotice(getString(R.string.not_allowed), key = "GUEST_RADIO_UNSUPPORTED")
            return
        }
        suppressAutoPlayback = false
        val currentMediaMetadata = player.currentMetadata ?: return

        val currentIndex = player.currentMediaItemIndex
        val currentMediaId = currentMediaMetadata.id

        scope.launch(SilentHandler) {
            val radioQueue = YouTubeQueue(
                endpoint = WatchEndpoint(videoId = currentMediaId)
            )
            val initialStatus = withContext(Dispatchers.IO) {
                radioQueue.getInitialStatus().filterVideo(dataStore.get(HideVideoKey, false))
            }

            if (initialStatus.title != null) {
                queueTitle = initialStatus.title
            }

            val radioItems = initialStatus.items.filter { item ->
                item.mediaId != currentMediaId
            }
            
            if (radioItems.isNotEmpty()) {
                val itemCount = player.mediaItemCount
                
                if (itemCount > currentIndex + 1) {
                    player.removeMediaItems(currentIndex + 1, itemCount)
                }
                
                player.addMediaItems(currentIndex + 1, radioItems)
            }

            currentQueue = radioQueue
        }
    }

    fun getAutomixAlbum(albumId: String) {
        automixController.getAutomixAlbum(albumId)
    }

    fun getAutomix(playlistId: String) {
        automixController.getAutomix(playlistId)
    }

    fun addToQueueAutomix(
        item: MediaItem,
        position: Int,
    ) {
        automixController.addToQueueAutomix(item, position)
    }

    fun playNextAutomix(
        item: MediaItem,
        position: Int,
    ) {
        automixController.playNextAutomix(item, position)
    }

    fun clearAutomix() {
        automixController.clearAutomix()
    }

    fun onInfiniteQueueDisabled() {
        automixController.onInfiniteQueueDisabled()
    }

    fun onInfiniteQueueEnabled() {
        automixController.onInfiniteQueueEnabled()
    }

    fun stopAndClearPlayback() {
        suppressAutoPlayback = true
        clearAutomix()
        currentQueue = EmptyQueue
        queueTitle = null
        streamErrorHandler.clearStreamRefreshGuards()
        waitingForNetworkConnection.value = false
        currentMediaMetadata.value = null
        player.playWhenReady = false
        player.stop()
        player.clearMediaItems()
        audioFocusController.abandonAudioFocus()
        audioEffectsController.closeAudioEffectSession()
        consecutivePlaybackErr = 0
    }

    fun playNext(items: List<MediaItem>) {
        val joined = musicTogetherCoordinator.togetherSessionState.value as? com.ruben.spotitube.together.TogetherSessionState.Joined
        if (joined?.role is com.ruben.spotitube.together.TogetherRole.Guest) {
            if (!joined.roomState.settings.allowGuestsToAddTracks) {
                return
            }
            val tracks =
                items.mapNotNull { it.metadata }.map { meta ->
                    com.ruben.spotitube.together.TogetherTrack(
                        id = meta.id,
                        title = meta.title,
                        artists = meta.artists.map { it.name },
                        durationSec = meta.duration,
                        thumbnailUrl = meta.thumbnailUrl,
                    )
                }
            tracks.asReversed().forEach { track ->
                musicTogetherCoordinator.requestTogetherAddTrack(track, com.ruben.spotitube.together.AddTrackMode.PLAY_NEXT)
            }
            return
        }
        suppressAutoPlayback = false
        player.addMediaItems(
            if (player.mediaItemCount == 0) 0 else player.currentMediaItemIndex + 1,
            items
        )
        player.prepare()
    }

    fun addToQueue(items: List<MediaItem>) {
        val joined = musicTogetherCoordinator.togetherSessionState.value as? com.ruben.spotitube.together.TogetherSessionState.Joined
        if (joined?.role is com.ruben.spotitube.together.TogetherRole.Guest) {
            if (!joined.roomState.settings.allowGuestsToAddTracks) {
                return
            }
            val tracks =
                items.mapNotNull { it.metadata }.map { meta ->
                    com.ruben.spotitube.together.TogetherTrack(
                        id = meta.id,
                        title = meta.title,
                        artists = meta.artists.map { it.name },
                        durationSec = meta.duration,
                        thumbnailUrl = meta.thumbnailUrl,
                    )
                }
            tracks.forEach { track ->
                musicTogetherCoordinator.requestTogetherAddTrack(track, com.ruben.spotitube.together.AddTrackMode.ADD_TO_QUEUE)
            }
            return
        }
        suppressAutoPlayback = false
        player.addMediaItems(items)
        player.prepare()
    }

    fun startTogetherOnlineHost(
        displayName: String,
        settings: com.ruben.spotitube.together.TogetherRoomSettings,
        avatarUri: String = "",
    ) {
        musicTogetherCoordinator.startTogetherOnlineHost(displayName, settings, avatarUri)
    }

    fun joinTogetherOnline(
        code: String,
        displayName: String,
        avatarUri: String = "",
    ) {
        musicTogetherCoordinator.joinTogetherOnline(code, displayName, avatarUri)
    }

    fun leaveTogether() {
        musicTogetherCoordinator.leaveTogether()
    }

    fun updateTogetherSettings(settings: com.ruben.spotitube.together.TogetherRoomSettings) {
        musicTogetherCoordinator.updateTogetherSettings(settings)
    }

    fun approveTogetherParticipant(participantId: String, approved: Boolean) {
        musicTogetherCoordinator.approveTogetherParticipant(participantId, approved)
    }

    fun kickTogetherParticipant(participantId: String, reason: String? = null) {
        musicTogetherCoordinator.kickTogetherParticipant(participantId, reason)
    }

    fun banTogetherParticipant(participantId: String, reason: String? = null) {
        musicTogetherCoordinator.banTogetherParticipant(participantId, reason)
    }

    fun requestTogetherControl(action: com.ruben.spotitube.together.ControlAction) {
        musicTogetherCoordinator.requestTogetherControl(action)
    }

    fun requestTogetherAddTrack(
        track: com.ruben.spotitube.together.TogetherTrack,
        mode: com.ruben.spotitube.together.AddTrackMode,
    ) {
        musicTogetherCoordinator.requestTogetherAddTrack(track, mode)
    }

    fun toggleSongSaved() {
        scope.launch(Dispatchers.IO) {
            val metadata = currentMediaMetadata.value ?: return@launch
            val song = metadata.toSavedSongEntity()
            val wasSaved = libraryStateHolder.isSavedSong(song.id).first()
            syncManager.toggleSongSaved(song)

            val isNowSaved = !wasSaved
            if (dataStore.get(AutoDownloadOnSavedKey, false) && isNowSaved) {
                val downloadRequest = com.ruben.spotitube.models.DownloadedSongMetadata.buildDownloadRequest(song)
                androidx.media3.exoplayer.offline.DownloadService.sendAddDownload(
                    this@MusicService,
                    ExoDownloadService::class.java,
                    downloadRequest,
                    false
                )
            }
        }
    }

    fun toggleStartRadio() {
        startRadioSeamlessly()
    }

    fun applyEqFlatPreset() {
        audioEffectsController.applyEqFlatPreset()
    }

    fun applySystemEqPreset(presetIndex: Int) {
        audioEffectsController.applySystemEqPreset(presetIndex)
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
    super.onMediaItemTransition(mediaItem, reason)

    streamErrorHandler.clearStreamRefreshGuards(
        mediaItem?.mediaId
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: player.currentMediaItem?.mediaId
    )

    crossfadeAudio?.onMediaItemTransition(mediaItem, reason)

    // Preload lyrics for upcoming songs in queue
    val currentIndex = player.currentMediaItemIndex
    // Convert media items to MediaMetadata for lyrics preloading
    val queue = player.mediaItems.mapNotNull { it.metadata }
    if (queue.isNotEmpty()) {
        lyricsPreloadManager?.onSongChanged(currentIndex, queue)
    }

    val joined = musicTogetherCoordinator.togetherSessionState.value as? com.ruben.spotitube.together.TogetherSessionState.Joined
    if (joined?.role is com.ruben.spotitube.together.TogetherRole.Guest &&
        reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
    ) {
        if (!joined.roomState.settings.allowGuestsToControlPlayback) {
            scope.launch(SilentHandler) { musicTogetherCoordinator.applyRemoteRoomState(joined.roomState) }
            return
        }
        val now = android.os.SystemClock.elapsedRealtime()
        val index = player.currentMediaItemIndex.coerceAtLeast(0)
        val isEcho =
            musicTogetherCoordinator.isTogetherApplyingRemote ||
                (now < musicTogetherCoordinator.togetherSuppressEchoUntilElapsedMs && musicTogetherCoordinator.togetherLastRemoteAppliedIndex == index)
        if (!isEcho) {
            val trackId = (mediaItem?.metadata ?: player.currentMetadata)?.id?.trim().orEmpty()
            musicTogetherCoordinator.requestTogetherControl(
                if (trackId.isBlank()) {
                    com.ruben.spotitube.together.ControlAction.SeekToIndex(
                        index = index,
                        positionMs = player.currentPosition.coerceAtLeast(0L),
                    )
                } else {
                    com.ruben.spotitube.together.ControlAction.SeekToTrack(
                        trackId = trackId,
                        positionMs = player.currentPosition.coerceAtLeast(0L),
                    )
                },
            )
        }
    }

    val timelineEmpty = player.currentTimeline.isEmpty || player.mediaItemCount == 0 || player.currentMediaItem == null
    val currentMetadata = if (timelineEmpty) null else (mediaItem?.metadata ?: player.currentMetadata)
    currentMediaMetadata.value = currentMetadata

    if (!timelineEmpty &&
        dataStore.get(AutoLoadMoreKey, true) &&
        reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT &&
        player.repeatMode == REPEAT_MODE_OFF
    ) {
        val isNearEndWithoutPaging =
            player.mediaItemCount - player.currentMediaItemIndex <= 3 && !currentQueue.hasNextPage()

        if (!isNearEndWithoutPaging) {
            val force =
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK ||
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED

            val currentId = (mediaItem?.metadata ?: player.currentMetadata)?.id?.trim().orEmpty()
            if (force || (currentId.isNotBlank() && automixController.automixSeedMediaId != currentId)) {
                automixController.refreshAutomixForCurrentMedia(force = force)
            }
        }
    }

    // Autoload more from queue if available
    if (!suppressAutoPlayback &&
        !timelineEmpty &&
        dataStore.get(AutoLoadMoreKey, true) &&
        reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT &&
        player.mediaItemCount - player.currentMediaItemIndex <= 5 &&
        currentQueue.hasNextPage() &&
        player.repeatMode == REPEAT_MODE_OFF
    ) {
        scope.launch(SilentHandler) {
            val mediaItems =
                currentQueue.nextPage().filterVideo(dataStore.get(HideVideoKey, false))
            if (player.playbackState != STATE_IDLE) {
                player.addMediaItems(mediaItems.drop(1))
            }
        }
    }
    
    if (!suppressAutoPlayback &&
        !timelineEmpty &&
        dataStore.get(AutoLoadMoreKey, true) &&
        reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT &&
        player.repeatMode == REPEAT_MODE_OFF &&
        player.mediaItemCount - player.currentMediaItemIndex <= 3 &&
        !currentQueue.hasNextPage()
    ) {
        scope.launch(SilentHandler) {
            if (suppressAutoPlayback || player.mediaItemCount == 0) return@launch
            val queueIds = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }.toSet()
            val currentMediaMetadata = player.currentMetadata
            val currentMediaId = currentMediaMetadata?.id?.trim().orEmpty()
            val existingSeed = automixController.automixSeedMediaId?.trim().orEmpty()
            val existingAutomix =
                if (currentMediaId.isNotBlank() && existingSeed == currentMediaId) {
                    automixController.automixItems.value
                } else {
                    if (automixController.automixItems.value.isNotEmpty()) {
                        automixController.clearAutomix()
                    }
                    emptyList()
                }
            if (existingAutomix.isNotEmpty()) {
                if (player.playbackState == STATE_IDLE) return@launch
                val filteredAutomix = existingAutomix.filter { it.mediaId !in queueIds }
                if (filteredAutomix.isNotEmpty()) {
                    player.addMediaItems(filteredAutomix)
                    filteredAutomix.forEach { automixController.autoAddedMediaIds.add(it.mediaId) }
                }
                automixController.clearAutomix()
            } else {
                if (currentMediaMetadata != null) {
                    automixController.refreshAutomixForCurrentMedia(force = true)
                }
            }
        }
    }

    scope.launch {
        val shouldSave = withContext(Dispatchers.IO) { dataStore.get(PersistentQueueKey, true) }
        if (shouldSave) {
            saveQueueToDisk()
        }
    }
    recordContinueListening()
}

    override fun onPlaybackStateChanged(@Player.State playbackState: Int) {
    super.onPlaybackStateChanged(playbackState)

    val activeMediaId = player.currentMediaItem?.mediaId
    streamErrorHandler.clearStreamRefreshGuards(activeMediaId)
    streamErrorHandler.onPlaybackReady(activeMediaId)

    scope.launch {
        val shouldSave = withContext(Dispatchers.IO) { dataStore.get(PersistentQueueKey, true) }
        if (shouldSave) {
            saveQueueToDisk()
        }
    }

    if (playbackState == STATE_IDLE || playbackState == Player.STATE_ENDED) {
        crossfadeAudio?.stop(resetMainFade = true)
    }
    
    // Auto-start recommendations when playback ends
    if (!suppressAutoPlayback &&
        playbackState == Player.STATE_ENDED &&
        dataStore.get(AutoLoadMoreKey, true) &&
        player.repeatMode == REPEAT_MODE_OFF &&
        player.currentMediaItem != null
    ) {
        // TODO: Restore recommendation logic here if needed
    }
}

    override fun onEvents(player: Player, events: Player.Events) {
        if (events.contains(Player.EVENT_MEDIA_METADATA_CHANGED)) {
            if (crossfadeAudio?.isCrossfading() != true) {
                currentMediaMetadata.value = player.currentMetadata
            }
        }
    val joined = musicTogetherCoordinator.togetherSessionState.value as? com.ruben.spotitube.together.TogetherSessionState.Joined
    if (joined?.role is com.ruben.spotitube.together.TogetherRole.Guest &&
        events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED)
    ) {
        if (!joined.roomState.settings.allowGuestsToControlPlayback) {
            scope.launch(SilentHandler) { musicTogetherCoordinator.applyRemoteRoomState(joined.roomState) }
        } else {
            val now = android.os.SystemClock.elapsedRealtime()
            val playWhenReady = this.player.playWhenReady
            val isEcho =
                musicTogetherCoordinator.isTogetherApplyingRemote ||
                    (now < musicTogetherCoordinator.togetherSuppressEchoUntilElapsedMs &&
                        musicTogetherCoordinator.togetherLastRemoteAppliedPlayWhenReady != null &&
                        musicTogetherCoordinator.togetherLastRemoteAppliedPlayWhenReady == playWhenReady)
            if (!isEcho) {
                val action =
                    if (playWhenReady) {
                        com.ruben.spotitube.together.ControlAction.Play
                    } else {
                        com.ruben.spotitube.together.ControlAction.Pause
                    }
                musicTogetherCoordinator.requestTogetherControl(action)
            }
        }
    }
    if (events.contains(Player.EVENT_DEVICE_VOLUME_CHANGED)) {
        deviceStateController.handleDeviceMuteStateChanged()
    }
    if (events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED) && deviceStateController.isDeviceMutedNow() && this.player.playWhenReady) {
        deviceStateController.handleDeviceMuteStateChanged()
    }
    if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) &&
        (this.player.playbackState == STATE_IDLE || this.player.playbackState == Player.STATE_ENDED)
    ) {
        deviceStateController.onPlaybackIdleOrEnded()
    }
    if (events.contains(Player.EVENT_AUDIO_SESSION_ID)) {
        audioEffectsController.onAudioSessionIdChanged(this.player.audioSessionId)
    }
    if (events.containsAny(
            Player.EVENT_PLAYBACK_STATE_CHANGED,
            Player.EVENT_PLAY_WHEN_READY_CHANGED
        )
    ) {
        val playbackState = player.playbackState
        val keepAudioEffectSessionOpen =
            playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_READY
        if (player.playWhenReady && keepAudioEffectSessionOpen) {
            audioFocusController.requestAudioFocus()
        }
        if (keepAudioEffectSessionOpen) {
            audioEffectsController.openAudioEffectSession()
        } else {
            audioEffectsController.closeAudioEffectSession()
        }
        if (player.playWhenReady && keepAudioEffectSessionOpen) {
            cancelIdleStop()
            promoteToStartedService()
            ensureStartedAsForeground()
        } else {
            scheduleStopIfIdle()
        }
    }

       if (events.containsAny(EVENT_TIMELINE_CHANGED, EVENT_POSITION_DISCONTINUITY)) {
            if (crossfadeAudio?.isCrossfading() != true) {
                currentMediaMetadata.value = player.currentMetadata
            }
            recordContinueListening()
        }

        // Also handle immediate update for play state and media item transition events explicitly
        if (events.containsAny(Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_PLAYBACK_STATE_CHANGED)) {
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                currentMediaMetadata.value = player.currentMetadata
            }
            refreshPlaybackNotification()
        }
  }


    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        refreshPlaybackNotification()
        val joined = musicTogetherCoordinator.togetherSessionState.value as? com.ruben.spotitube.together.TogetherSessionState.Joined
        if (joined?.role is com.ruben.spotitube.together.TogetherRole.Guest) {
            if (!musicTogetherCoordinator.isTogetherApplyingRemote) {
                if (!joined.roomState.settings.allowGuestsToControlPlayback) {
                    scope.launch(SilentHandler) { musicTogetherCoordinator.applyRemoteRoomState(joined.roomState) }
                    return
                }
                musicTogetherCoordinator.requestTogetherControl(
                    com.ruben.spotitube.together.ControlAction.SetShuffleEnabled(
                        shuffleEnabled = shuffleModeEnabled,
                    ),
                )
            }
            return
        }
        if (shuffleModeEnabled) {
            applyCurrentFirstShuffleOrder()
        }
        recordContinueListening()
        
        // Save state when shuffle mode changes - must be on Main thread to access player
        scope.launch {
            if (dataStore.get(PersistentQueueKey, true)) {
                saveQueueToDisk()
            }
        }
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
        refreshPlaybackNotification()
        val joined = musicTogetherCoordinator.togetherSessionState.value as? com.ruben.spotitube.together.TogetherSessionState.Joined
        if (joined?.role is com.ruben.spotitube.together.TogetherRole.Guest) {
            if (!musicTogetherCoordinator.isTogetherApplyingRemote) {
                if (!joined.roomState.settings.allowGuestsToControlPlayback) {
                    scope.launch(SilentHandler) { musicTogetherCoordinator.applyRemoteRoomState(joined.roomState) }
                    return
                }
                musicTogetherCoordinator.requestTogetherControl(
                    com.ruben.spotitube.together.ControlAction.SetRepeatMode(
                        repeatMode = repeatMode,
                    ),
                )
            }
            return
        }
        scope.launch {
            dataStore.edit { settings ->
                settings[RepeatModeKey] = repeatMode
            }
        }
        
        // Save state when repeat mode changes - must be on Main thread to access player
        scope.launch {
            if (dataStore.get(PersistentQueueKey, true)) {
                saveQueueToDisk()
            }
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        super.onPlayerError(error)
        streamErrorHandler.onPlayerError(error)
    }

    private suspend fun trimPlayerCacheToBytes(limitBytes: Long) {
        if (limitBytes <= 0L) return

        withContext(Dispatchers.IO) {
            val cacheDir = filesDir.resolve("exoplayer")
            val currentSpace = runCatching { playerCache.cacheSpace }.getOrNull() ?: 0L
            var totalBytes = if (currentSpace > 0L) currentSpace else cacheDir.directorySizeBytes()
            if (totalBytes <= limitBytes) return@withContext

            data class Candidate(
                val key: String,
                val lastTouchTimestamp: Long,
                val sizeBytes: Long,
            )

            val candidates =
                runCatching {
                    playerCache.keys.mapNotNull { key ->
                        runCatching {
                            val spans = playerCache.getCachedSpans(key)
                            if (spans.isEmpty()) return@runCatching null
                            val oldestTouch = spans.minOf { it.lastTouchTimestamp }
                            val sizeBytes = spans.sumOf { it.length }
                            Candidate(key = key, lastTouchTimestamp = oldestTouch, sizeBytes = sizeBytes)
                        }.getOrNull()
                    }.sortedBy { it.lastTouchTimestamp }
                }.getOrNull().orEmpty()

            for ((key, _, sizeBytes) in candidates) {
                if (totalBytes <= limitBytes) break
                val removedSize = sizeBytes.coerceAtLeast(0L)
                runCatching { playerCache.removeResource(key) }
                totalBytes -= removedSize
            }
        }
    }



    fun retryCurrentFromFreshStream() {
        streamErrorHandler.retryCurrentFromFreshStream()
    }



    private fun updateAudioOffload(enabled: Boolean) {
        runCatching {
            val builder = player.trackSelectionParameters.buildUpon()
            val audioOffloadPrefsClass = Class.forName("androidx.media3.common.AudioOffloadPreferences")
            val audioOffloadPrefsBuilderClass = Class.forName($$"androidx.media3.common.AudioOffloadPreferences$Builder")

            val modeFieldName = if (enabled) "AUDIO_OFFLOAD_MODE_ENABLED" else "AUDIO_OFFLOAD_MODE_DISABLED"
            val mode = audioOffloadPrefsClass.getField(modeFieldName).getInt(null)

            val prefsBuilder = audioOffloadPrefsBuilderClass.getDeclaredConstructor().newInstance()
            audioOffloadPrefsBuilderClass.getMethod("setAudioOffloadMode", Int::class.javaPrimitiveType).invoke(prefsBuilder, mode)
            val prefs = audioOffloadPrefsBuilderClass.getMethod("build").invoke(prefsBuilder)

            val setMethod =
                builder.javaClass.methods.firstOrNull { method ->
                    method.name == "setAudioOffloadPreferences" && method.parameterTypes.size == 1
                }
            if (setMethod != null) {
                setMethod.invoke(builder, prefs)
                player.trackSelectionParameters = builder.build()
            }
        }
        player.setOffloadEnabled(enabled)
    }

    override fun onPlaybackStatsReady(
        eventTime: AnalyticsListener.EventTime,
        playbackStats: PlaybackStats,
    ) {
        val mediaItem = eventTime.timeline.getWindow(eventTime.windowIndex, Timeline.Window()).mediaItem
        val minTimeMs = dataStore[HistoryDuration]?.times(1000f) ?: 25000f
        if (playbackStats.totalPlayTimeMs >= minTimeMs &&
            !dataStore.get(PauseListenHistoryKey, false)
        ) {
            ioScope.launch {
                try {
                    database.incrementPlayCount(mediaItem.mediaId)
                } catch (e: Exception) {
                    Timber.tag("MusicService").e(e, "Failed to record play count")
                }
            }
        }
    }

    private fun recordContinueListening() {
        val currentId = player.currentMediaItem?.mediaId?.takeIf { it.isNotBlank() } ?: return
        val type: String
        val id: String
        val title: String
        val thumb: String?
        val originIds: Set<String>

        when (val q = currentQueue) {
            is LocalAlbumRadio -> {
                type = "album"
                id = q.albumWithSongs.album.id
                title = q.albumWithSongs.album.title
                thumb = q.albumWithSongs.album.thumbnailUrl
                originIds = q.albumWithSongs.songs.map { it.id }.toSet()
            }
            is ListQueue -> {
                val qType = q.originType ?: return
                val qId = q.originId ?: return
                type = qType
                id = qId
                title = q.originTitle ?: q.title ?: ""
                thumb = q.originThumbnailUrl
                originIds = q.items.map { it.mediaId }.toSet()
            }
            else -> return
        }

        if (currentId !in originIds) return

        val timeline = player.currentTimeline
        if (timeline.isEmpty) return
        val shuffle = player.shuffleModeEnabled

        val itemsList = ArrayList<MediaMetadata>()
        var i = timeline.getFirstWindowIndex(shuffle)
        while (i != C.INDEX_UNSET) {
            val mediaItem = player.getMediaItemAt(i)
            if (mediaItem.mediaId in originIds) {
                mediaItem.toPersistableMetadata()?.let { itemsList.add(it) }
            }
            i = timeline.getNextWindowIndex(i, REPEAT_MODE_OFF, shuffle)
        }

        if (itemsList.isEmpty()) return

        ioScope.launch {
            ContinueListeningStore.upsert(
                ContinueListeningEntry(
                    originType = type,
                    originId = id,
                    title = title,
                    thumbnailUrl = thumb,
                    items = itemsList,
                    currentSongId = currentId,
                    updatedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    private suspend fun saveQueueToDisk() {
        val mediaItemsSnapshot = player.mediaItems.mapNotNull { it.toPersistableMetadata() }
        if (mediaItemsSnapshot.isEmpty()) return

        val currentMediaItemIndex = player.currentMediaItemIndex
        val currentPosition = player.currentPosition
        val automixSnapshot = automixController.automixItems.value.mapNotNull { it.metadata }
        val playWhenReady = player.playWhenReady
        val repeatMode = player.repeatMode
        val shuffleModeEnabled = player.shuffleModeEnabled
        val volume = playerVolume.value
        val playbackState = player.playbackState

        withContext(Dispatchers.IO) {
            // Save current queue with proper type information
            val persistQueue = currentQueue.toPersistQueue(
                title = queueTitle,
                items = mediaItemsSnapshot,
                mediaItemIndex = currentMediaItemIndex,
                position = currentPosition
            )
            
            val persistAutomix =
                PersistQueue(
                    title = "automix",
                    items = automixSnapshot,
                    mediaItemIndex = 0,
                    position = 0,
                )
                
            // Save player state
            val persistPlayerState = PersistPlayerState(
                playWhenReady = playWhenReady,
                repeatMode = repeatMode,
                shuffleModeEnabled = shuffleModeEnabled,
                volume = volume,
                currentPosition = currentPosition,
                currentMediaItemIndex = currentMediaItemIndex, // Redundant but part of data class
                playbackState = playbackState
            )
            
            persistentStateStore.writePersistentObject(PERSISTENT_QUEUE_FILE, persistQueue)
            persistentStateStore.writePersistentObject(PERSISTENT_AUTOMIX_FILE, persistAutomix)
            persistentStateStore.writePersistentObject(PERSISTENT_PLAYER_STATE_FILE, persistPlayerState)
        }
    }


    override fun onDestroy() {
        deviceStateController.unregisterBluetoothReceiver()
        try {
            scope.launch { musicTogetherCoordinator.stopTogetherInternal() }
        } catch (_: Exception) {}
        try {
            connectivityObserver.unregister()
        } catch (_: Exception) {}
        audioFocusController.abandonAudioFocus()
        try {
            audioEffectsController.releaseAudioEffects()
        } catch (_: Exception) {}
        try {
            if (dataStore.get(PersistentQueueKey, true) && player.mediaItemCount > 0) {
                val mediaItemsSnapshot = player.mediaItems.mapNotNull { it.metadata }
                val currentMediaItemIndex = player.currentMediaItemIndex
                val currentPosition = player.currentPosition
                val automixSnapshot = automixController.automixItems.value.mapNotNull { it.metadata }
                val repeatMode = player.repeatMode
                val shuffleModeEnabled = player.shuffleModeEnabled
                val volume = playerVolume.value
                val playbackState = player.playbackState
                val playWhenReady = player.playWhenReady
                runBlocking(Dispatchers.IO) {
                    val persistQueue = currentQueue.toPersistQueue(
                        title = queueTitle,
                        items = mediaItemsSnapshot,
                        mediaItemIndex = currentMediaItemIndex,
                        position = currentPosition
                    )
                    val persistAutomix = PersistQueue(
                        title = "automix",
                        items = automixSnapshot,
                        mediaItemIndex = 0,
                        position = 0,
                    )
                    val persistPlayerState = PersistPlayerState(
                        playWhenReady = playWhenReady,
                        repeatMode = repeatMode,
                        shuffleModeEnabled = shuffleModeEnabled,
                        volume = volume,
                        currentPosition = currentPosition,
                        currentMediaItemIndex = currentMediaItemIndex,
                        playbackState = playbackState
                    )

                    persistentStateStore.writePersistentObject(PERSISTENT_QUEUE_FILE, persistQueue)
                    persistentStateStore.writePersistentObject(PERSISTENT_AUTOMIX_FILE, persistAutomix)
                    persistentStateStore.writePersistentObject(PERSISTENT_PLAYER_STATE_FILE, persistPlayerState)
                }
            }
        } catch (_: Exception) {}
        try {
            mediaSession?.release()
            mediaSession = null
        } catch (_: Exception) {}
        try {
            crossfadeAudio?.release()
            crossfadeAudio = null
        } catch (_: Exception) {}
        try {
            player.removeListener(this)
            player.removeListener(sleepTimer)
            player.release()
        } catch (_: Exception) {}
        scopeJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): android.os.IBinder {
        hasBoundClients = true
        cancelIdleStop()
        val result = super.onBind(intent) ?: binder
        if (player.mediaItemCount > 0 && player.currentMediaItem != null) {
            currentMediaMetadata.value = player.currentMetadata
            scope.launch {
                delay(50.milliseconds)
                refreshPlaybackNotification()
            }
        }
        return result
    }

    override fun onUnbind(intent: Intent?): Boolean {
        hasBoundClients = false
        scheduleStopIfIdle()
        return super.onUnbind(intent)
    }

    override fun onRebind(intent: Intent?) {
        hasBoundClients = true
        cancelIdleStop()
        super.onRebind(intent)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        try {
            if (dataStore.get(PersistentQueueKey, true) && player.mediaItemCount > 0) {
                runBlocking { saveQueueToDisk() }
            }
            runCatching { stopAndClearPlayback() }
            runCatching {
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
            stopSelf()
        } catch (_: Exception) {}
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        Timber.tag("MusicService").d("onGetSession called from package=${controllerInfo.packageName}")
        return mediaSession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_NOT_STICKY
    }

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        Timber.tag("MusicService").d("onUpdateNotification called, startInForegroundRequired=$startInForegroundRequired, hasCalledStartForeground=$hasCalledStartForeground")
        if (startInForegroundRequired || player.mediaItemCount > 0) {
            ensureStartedAsForeground()
        }
        runCatching { super.onUpdateNotification(session, hasCalledStartForeground) }
            .onFailure { reportException(it) }
    }

    inner class MusicBinder : Binder() {
        val service: MusicService
            get() = this@MusicService
    }

    companion object {
        internal val playbackUrlCache = ConcurrentHashMap<String, Pair<String, Long>>()
        internal val streamRecoveryState = ConcurrentHashMap<String, Pair<Int, Long>>()

        fun clearPlaybackCacheAndRecoveryForRetry(mediaId: String) {
            playbackUrlCache.remove(mediaId)
            streamRecoveryState.remove(mediaId)
        }

        internal fun shouldStopServiceOnTaskRemoved(
            stopMusicOnTaskClearEnabled: Boolean,
            isHostSessionActive: Boolean,
            isPlaybackInactive: Boolean,
        ): Boolean = (isHostSessionActive && isPlaybackInactive) || stopMusicOnTaskClearEnabled

        const val ROOT = "root"
        const val SONG = "song"
        const val ARTIST = "artist"
        const val ALBUM = "album"
        const val PLAYLIST = "playlist"

        const val CHANNEL_ID = "music_channel_v2"
        const val NOTIFICATION_ID = 888
        const val ERROR_CODE_NO_STREAM = 1000001
        const val CHUNK_LENGTH = 512 * 1024L
        const val PERSISTENT_QUEUE_FILE = "persistent_queue.data"
        const val PERSISTENT_AUTOMIX_FILE = "persistent_automix.data"
        const val PERSISTENT_PLAYER_STATE_FILE = "persistent_player_state.data"
        const val MAX_CONSECUTIVE_ERR = 5
    }
}