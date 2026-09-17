/*
 * ARMORY Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.arturo254.opentune

import android.app.Application
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.datastore.preferences.core.edit
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.request.CachePolicy
import coil3.request.allowHardware
import coil3.request.crossfade
import com.arturo254.opentune.constants.*
import com.arturo254.opentune.extensions.*
import com.arturo254.opentune.ui.theme.ThemePalettes
import com.arturo254.opentune.utils.dataStore
import com.arturo254.opentune.utils.PreferenceStore
import com.arturo254.opentune.utils.get
import com.arturo254.opentune.utils.reportException
import com.arturo254.opentune.db.MusicDatabase
import com.arturo254.opentune.innertube.YouTube
import com.arturo254.opentune.innertube.models.YouTubeLocale
import com.arturo254.opentune.kugou.KuGou
import com.arturo254.opentune.lastfm.LastFM
import com.arturo254.opentune.ui.player.CanvasArtworkPlaybackCache
import com.arturo254.opentune.sync.SyncManager
import com.arturo254.opentune.utils.YTPlayerUtils
import com.google.firebase.FirebaseApp
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import android.content.Intent
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess
import timber.log.Timber
import java.net.Proxy
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

@HiltAndroidApp
class App : Application(), SingletonImageLoader.Factory, Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var syncManager: SyncManager

    @Inject
    lateinit var database: MusicDatabase

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized = _isInitialized.asStateFlow()
    private val didRunImageCacheTrim = AtomicBoolean(false)

    private fun currentProcessName(): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            val pid = android.os.Process.myPid()
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            activityManager?.runningAppProcesses
                ?.firstOrNull { it.pid == pid }
                ?.processName
        }
    }


    
    override fun onCreate() {
        super.onCreate()
        instance = this
        if (currentProcessName()?.endsWith(":crash") == true) {
            FirebaseApp.initializeApp(this)
            Timber.plant(Timber.DebugTree())
            return
        }
        
        FirebaseApp.initializeApp(this)
        PreferenceStore.start(this)
        Timber.plant(Timber.DebugTree())
        try {
            Timber.plant(com.arturo254.opentune.utils.GlobalLogTree())
        } catch (_: Exception) {}

        applicationScope.launch(Dispatchers.IO) {
            MusicDatabase.warmUp(this@App, database)
            initializeCriticalSync()
        }
        initializeDeferredAsync()
    }

    private fun initializeCriticalSync() {
        CanvasArtworkPlaybackCache.init(this)

        val locale = Locale.getDefault()
        val languageTag = locale.toLanguageTag().replace("-Hant", "")
        YouTube.locale = YouTubeLocale(
            gl = locale.country.takeIf { it in CountryCodeToName } ?: "US",
            hl = locale.language.takeIf { it in LanguageCodeToName }
                ?: languageTag.takeIf { it in LanguageCodeToName }
                ?: "en"
        )
        if (languageTag == "zh-TW") {
            KuGou.useTraditionalChinese = true
        }
        LastFM.initialize(
            apiKey = BuildConfig.LASTFM_API_KEY,
            secret = BuildConfig.LASTFM_SECRET
        )
    }

    private fun initializeDeferredAsync() {
        // Collect all necessary preferences in a more granular way to reduce main thread overhead
        applicationScope.launch(Dispatchers.IO) {
            try {
                dataStore.data.collect { prefs ->
                    // YT Locale
                    val country = prefs[ContentCountryKey]?.takeIf { it != SYSTEM_DEFAULT }
                    val lang = prefs[ContentLanguageKey]?.takeIf { it != SYSTEM_DEFAULT }
                    if (country != null || lang != null) {
                        YouTube.locale = YouTube.locale.copy(
                            gl = country ?: YouTube.locale.gl,
                            hl = lang ?: YouTube.locale.hl
                        )
                    }

                    // LastFM
                    LastFM.sessionKey = prefs[LastFMSessionKey]

                    // Proxy - Only re-init if really changed to avoid client recreation overhead
                    val proxyEnabled = prefs[ProxyEnabledKey] == true
                    if (proxyEnabled) {
                        try {
                            val newProxy = Proxy(
                                prefs[ProxyTypeKey].toEnum(defaultValue = Proxy.Type.HTTP),
                                prefs[ProxyUrlKey]!!.toInetSocketAddress()
                            )
                            if (YouTube.proxy != newProxy) {
                                YouTube.proxy = newProxy
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@App, "Failed to parse proxy url.", LENGTH_SHORT).show()
                            }
                            reportException(e)
                        }
                        YouTube.streamBypassProxy = prefs[StreamBypassProxyKey] == true
                    } else if (YouTube.proxy != null) {
                        YouTube.proxy = null
                    }

                    // YT Settings
                    YouTube.useLoginForBrowse = prefs[UseLoginForBrowse] != false
                    YouTube.visitorData = prefs[VisitorDataKey]?.takeIf { it != "null" } ?: YouTube.visitorData
                    YouTube.dataSyncId = prefs[DataSyncIdKey]?.let {
                        it.takeIf { !it.contains("||") }
                            ?: it.takeIf { it.endsWith("||") }?.substringBefore("||")
                            ?: it.substringAfter("||")
                    }
                    YouTube.cookie = prefs[InnerTubeCookieKey]
                    YouTube.webClientPoTokenEnabled = prefs[WebClientPoTokenEnabledKey] ?: false
                    YouTube.poToken = prefs[PoTokenKey]?.takeIf { it.isNotBlank() }
                    YouTube.poTokenGvs = prefs[PoTokenGvsKey]?.takeIf { it.isNotBlank() }
                    YouTube.poTokenPlayer = prefs[PoTokenPlayerKey]?.takeIf { it.isNotBlank() }
                    
                    YTPlayerUtils.initScores(prefs[YouTubeClientScoresKey])

                    _isInitialized.value = true
                }
            } catch (e: Exception) {
                Timber.e(e, "Error during deferred initialization")
                reportException(e)
            }
        }

        // Keep the visitor data update logic separate as it might trigger a network call/save
        applicationScope.launch(Dispatchers.IO) {
            dataStore.data
                .map { it[VisitorDataKey] }
                .distinctUntilChanged()
                .collect { visitorData ->
                    if (visitorData == null || visitorData == "null") {
                        YouTube.visitorData().onSuccess { newVisitorData ->
                            dataStore.edit { settings ->
                                settings[VisitorDataKey] = newVisitorData
                            }
                        }.onFailure {
                            reportException(it)
                        }
                    }
                }
        }

        try {
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    val sw = StringWriter()
                    val pw = PrintWriter(sw)
                    throwable.printStackTrace(pw)
                    val stack = sw.toString()

                    val intent = Intent(this@App, DebugActivity::class.java).apply {
                        putExtra(DebugActivity.EXTRA_STACK_TRACE, stack)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    }
                    startActivity(intent)
                    try { Thread.sleep(100) } catch (_: InterruptedException) {}
                } catch (e: Exception) {
                    reportException(e)
                } finally {
                    android.os.Process.killProcess(android.os.Process.myPid())
                    exitProcess(2)
                }
            }
        } catch (e: Exception) {
            reportException(e)
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val smartTrimmer = dataStore[SmartTrimmerKey] ?: false
        val imageCacheConfig = resolveImageDiskCacheConfig(dataStore[MaxImageCacheSizeKey])

        val diskCache = DiskCache.Builder()
            .directory(cacheDir.resolve("coil"))
            .maxSizeBytes(imageCacheConfig.maxSizeBytes)
            .build()

        if (smartTrimmer && imageCacheConfig.policy == CachePolicy.ENABLED && didRunImageCacheTrim.compareAndSet(false, true)) {
            applicationScope.launch(Dispatchers.IO) { trimImageDiskCache(diskCache) }
        }

        return ImageLoader.Builder(this)
            .crossfade(true)
            .allowHardware(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            .diskCache(diskCache)
            .diskCachePolicy(imageCacheConfig.policy)
            .build()
    }

    private fun trimImageDiskCache(diskCache: DiskCache) {
        try {
            val limitBytes = diskCache.maxSize
            if (limitBytes <= 0L || limitBytes == Long.MAX_VALUE) return

            val dir = java.io.File(diskCache.directory.toString())
            if (!dir.exists()) return

            val files = dir.walkTopDown().filter { it.isFile }.sortedBy { it.lastModified() }.toList()
            var currentSize = files.sumOf { it.length() }
            if (currentSize <= limitBytes) return

            for (file in files) {
                if (currentSize <= limitBytes) break
                val size = file.length()
                if (runCatching { file.delete() }.getOrDefault(false)) currentSize -= size
            }
        } catch (_: Exception) {
        }
    }

    companion object {
        lateinit var instance: App
            private set

        fun forgetAccount(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                context.dataStore.edit { settings ->
                    settings.remove(InnerTubeCookieKey)
                    settings.remove(PoTokenKey)
                    settings.remove(VisitorDataKey)
                    settings.remove(DataSyncIdKey)
                    settings.remove(AccountNameKey)
                    settings.remove(AccountEmailKey)
                    settings.remove(AccountChannelHandleKey)
                }
            }
        }
    }
}

internal data class ImageDiskCacheConfig(
    val policy: CachePolicy,
    val maxSizeBytes: Long,
)

internal fun resolveImageDiskCacheConfig(maxImageCacheSizeMb: Int?): ImageDiskCacheConfig {
    val sizeMb = maxImageCacheSizeMb ?: 512
    if (sizeMb == 0) return ImageDiskCacheConfig(policy = CachePolicy.DISABLED, maxSizeBytes = 1L)
    if (sizeMb < 0) return ImageDiskCacheConfig(policy = CachePolicy.ENABLED, maxSizeBytes = Long.MAX_VALUE)
    val bytesPerMb = 1024L * 1024L
    val safeSizeMb = sizeMb.toLong().coerceAtMost(Long.MAX_VALUE / bytesPerMb)
    return ImageDiskCacheConfig(policy = CachePolicy.ENABLED, maxSizeBytes = safeSizeMb * bytesPerMb)
}
