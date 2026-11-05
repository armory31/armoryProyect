/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ruben.spotitube

import android.app.ActivityManager
import android.app.Application
import android.content.Intent
import android.os.Build
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.datastore.preferences.core.edit
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.request.CachePolicy
import coil3.request.allowHardware
import coil3.request.crossfade
import com.google.firebase.FirebaseApp
import com.ruben.spotitube.constants.ContentCountryKey
import com.ruben.spotitube.constants.ContentLanguageKey
import com.ruben.spotitube.constants.CountryCodeToName
import com.ruben.spotitube.constants.LanguageCodeToName
import com.ruben.spotitube.constants.MaxImageCacheSizeKey
import com.ruben.spotitube.constants.ProxyEnabledKey
import com.ruben.spotitube.constants.ProxyTypeKey
import com.ruben.spotitube.constants.ProxyUrlKey
import com.ruben.spotitube.constants.SYSTEM_DEFAULT
import com.ruben.spotitube.constants.SmartTrimmerKey
import com.ruben.spotitube.constants.StreamBypassProxyKey
import com.ruben.spotitube.constants.VisitorDataKey
import com.ruben.spotitube.constants.YouTubeClientScoresKey
import com.ruben.spotitube.db.MusicDatabase
import com.ruben.spotitube.extensions.toEnum
import com.ruben.spotitube.extensions.toInetSocketAddress
import com.ruben.spotitube.innertube.YouTube
import com.ruben.spotitube.innertube.models.YouTubeLocale
import com.ruben.spotitube.kugou.KuGou
import com.ruben.spotitube.sync.SyncManager
import com.ruben.spotitube.ui.player.CanvasArtworkPlaybackCache
import com.ruben.spotitube.utils.PreferenceStore
import com.ruben.spotitube.utils.YTPlayerUtils
import com.ruben.spotitube.utils.dataStore
import com.ruben.spotitube.utils.reportException
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.PrintWriter
import java.io.StringWriter
import java.net.Proxy
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.system.exitProcess

object HomeTiming {
    var startTimeMs: Long = 0L
    fun log(event: String) {
        val elapsed = android.os.SystemClock.elapsedRealtime() - startTimeMs
        Timber.tag("HomeTiming").i("$event +${elapsed}ms")
    }
}

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

    private val appExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Timber.e(throwable, "Unhandled exception in applicationScope")
    }
    internal val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main + appExceptionHandler)
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized = _isInitialized.asStateFlow()
    private val didRunImageCacheTrim = AtomicBoolean(false)
    private val isFirstVisitorDataRead = AtomicBoolean(false)
    private val isFirstInitializedLogged = AtomicBoolean(false)

    private fun currentProcessName(): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getProcessName()
        } else {
            val pid = android.os.Process.myPid()
            val activityManager = getSystemService(ACTIVITY_SERVICE) as? ActivityManager
            activityManager?.runningAppProcesses
                ?.firstOrNull { it.pid == pid }
                ?.processName
        }
    }


    
    override fun onCreate() {
        HomeTiming.startTimeMs = android.os.SystemClock.elapsedRealtime()
        HomeTiming.log("App.onCreate start")
        super.onCreate()
        instance = this
        if (currentProcessName()?.endsWith(":crash") == true) {
            FirebaseApp.initializeApp(this)
            Timber.plant(Timber.DebugTree())
            return
        }
        
        FirebaseApp.initializeApp(this)
        Timber.plant(Timber.DebugTree())
        try {
            Timber.plant(com.ruben.spotitube.utils.GlobalLogTree())
        } catch (_: Exception) {}

        applicationScope.launch(Dispatchers.IO) {
            PreferenceStore.start(this@App)
            HomeTiming.log("PreferenceStore.start end")
            MusicDatabase.warmUp(this@App, database)
            HomeTiming.log("MusicDatabase.warmUp end")
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
        HomeTiming.log("initializeCriticalSync end gl=${YouTube.locale.gl}")
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

                    val rawVisitorData = prefs[VisitorDataKey]
                    if (isFirstVisitorDataRead.compareAndSet(false, true)) {
                        HomeTiming.log("VisitorDataKey read (wasEmpty=${rawVisitorData.isNullOrBlank() || rawVisitorData == "null"})")
                    }
                    YouTube.visitorData = rawVisitorData?.takeIf { it.isNotBlank() && it != "null" } ?: YouTube.visitorData

                    val scoresJson = prefs[YouTubeClientScoresKey]
                    if (scoresJson != null) {
                        YTPlayerUtils.initScores(scoresJson)
                    }

                    val smartTrimmer = prefs[SmartTrimmerKey] ?: false
                    val maxCacheSizeMb = prefs[MaxImageCacheSizeKey]
                    val sp = getSharedPreferences("image_cache_prefs", MODE_PRIVATE)
                    val editor = sp.edit().putBoolean("smart_trimmer", smartTrimmer)
                    if (maxCacheSizeMb != null) {
                        editor.putInt("max_image_cache_mb", maxCacheSizeMb)
                    } else {
                        editor.remove("max_image_cache_mb")
                    }
                    editor.apply()

                    val imageCacheConfig = resolveImageDiskCacheConfig(maxCacheSizeMb)
                    if (smartTrimmer && imageCacheConfig.policy == CachePolicy.ENABLED && didRunImageCacheTrim.compareAndSet(false, true)) {
                        val diskCache = DiskCache.Builder()
                            .directory(cacheDir.resolve("coil"))
                            .maxSizeBytes(imageCacheConfig.maxSizeBytes)
                            .build()
                        trimImageDiskCache(diskCache)
                    }

                    _isInitialized.value = true
                    if (isFirstInitializedLogged.compareAndSet(false, true)) {
                        HomeTiming.log("_isInitialized = true")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error during deferred initialization")
                reportException(e)
            }
        }

        applicationScope.launch(Dispatchers.IO) {
            dataStore.data
                .map { it[VisitorDataKey] }
                .distinctUntilChanged()
                .collect { currentVisitorData ->
                    if (currentVisitorData.isNullOrBlank() || currentVisitorData == "null") {
                        YouTube.visitorData()
                            .onSuccess { newVisitorData ->
                                HomeTiming.log("YouTube.visitorData success")
                                dataStore.edit { prefs ->
                                    prefs[VisitorDataKey] = newVisitorData
                                }
                            }
                            .onFailure { exception ->
                                HomeTiming.log("YouTube.visitorData failure: ${exception.message}")
                                reportException(exception)
                            }
                    }
                }
        }

        try {
            Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
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
        val sp = getSharedPreferences("image_cache_prefs", MODE_PRIVATE)
        val smartTrimmer = sp.getBoolean("smart_trimmer", false)
        val maxMb = if (sp.contains("max_image_cache_mb")) sp.getInt("max_image_cache_mb", 512) else null
        val imageCacheConfig = resolveImageDiskCacheConfig(maxMb)

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
