/*
 * ARMORY Project - Music Player (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ruben.spotitube.playback

import android.content.Context
import android.media.MediaCodecList
import android.net.ConnectivityManager
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.media3.database.DatabaseProvider
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import com.ruben.spotitube.App
import com.ruben.spotitube.constants.AudioQuality
import com.ruben.spotitube.constants.AudioQualityKey
import com.ruben.spotitube.constants.NetworkMeteredKey
import com.ruben.spotitube.constants.PlayerStreamClient
import com.ruben.spotitube.constants.PlayerStreamClientKey
import com.ruben.spotitube.db.MusicDatabase
import com.ruben.spotitube.db.entities.DownloadOriginEntity
import com.ruben.spotitube.db.entities.DownloadOriginType
import com.ruben.spotitube.db.entities.DownloadedSongEntity
import com.ruben.spotitube.db.entities.FormatEntity
import com.ruben.spotitube.di.DownloadCache
import com.ruben.spotitube.di.PlayerCache
import com.ruben.spotitube.innertube.YouTube
import com.ruben.spotitube.models.DownloadedSongMetadata
import com.ruben.spotitube.utils.StreamClientUtils
import com.ruben.spotitube.utils.YTPlayerUtils
import com.ruben.spotitube.utils.dataStore
import com.ruben.spotitube.utils.enumPreference
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton

data class DownloadProgress(
    val total: Int,
    val completed: Int,
    val failed: Int,
    val percent: Int,
    val isActive: Boolean,
)

@Singleton
class DownloadUtil
@Inject
constructor(
    @ApplicationContext context: Context,
    val database: MusicDatabase,
    val databaseProvider: DatabaseProvider,
    @DownloadCache val downloadCache: Cache,
    @PlayerCache val playerCache: Cache,
) {
    private val appContext = context.applicationContext
    private val connectivityManager = context.getSystemService<ConnectivityManager>()!!
    private val audioQuality by enumPreference(context, AudioQualityKey, AudioQuality.AUTO)
    private val preferredStreamClient by enumPreference(context, PlayerStreamClientKey, PlayerStreamClient.ANDROID_VR)
    private val songUrlCache = ConcurrentHashMap<String, Pair<String, Long>>()
    private val avoidStreamCodecs: Set<String> by lazy {
        if (deviceSupportsMimeType("audio/opus")) emptySet() else setOf("opus")
    }
    private val mediaOkHttpClient: OkHttpClient by lazy {
        OkHttpClient
            .Builder()
            .proxy(YouTube.streamProxy)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val request = chain.request()
                val host = request.url.host
                val isYouTubeMediaHost =
                    host.endsWith("googlevideo.com") ||
                        host.endsWith("googleusercontent.com") ||
                        host.endsWith("youtube.com") ||
                        host.endsWith("youtube-nocookie.com") ||
                        host.endsWith("ytimg.com")

                if (!isYouTubeMediaHost) return@addInterceptor chain.proceed(request)

                val clientParam = request.url.queryParameter("c")?.trim().orEmpty()

                val userAgent = StreamClientUtils.resolveUserAgent(clientParam)
                val originReferer = StreamClientUtils.resolveOriginReferer(clientParam)

                val builder = request.newBuilder().header("User-Agent", userAgent)
                originReferer.origin?.let { builder.header("Origin", it) }
                originReferer.referer?.let { builder.header("Referer", it) }

                chain.proceed(builder.build())
            }.build()
    }

    private val _downloads = MutableStateFlow<Map<String, Download>>(emptyMap())
    val downloads: StateFlow<Map<String, Download>> = _downloads.asStateFlow()

    val downloadedIds: StateFlow<Set<String>> = database.downloadedSongIds()
        .map { it.toSet() }
        .stateIn(App.instance.applicationScope, SharingStarted.Eagerly, emptySet())

    fun songState(songId: String): Flow<Int?> = combine(downloadedIds, _downloads) { ids, map ->
        if (songId in ids) {
            Download.STATE_COMPLETED
        } else {
            map[songId]?.state?.takeIf { it == Download.STATE_QUEUED || it == Download.STATE_DOWNLOADING }
        }
    }.distinctUntilChanged()

    fun originState(type: String, originId: String): Flow<Int> = combine(
        database.originSongIdsFlow(type, originId),
        downloadedIds,
        _downloads
    ) { songIds, done, map ->
        if (songIds.isEmpty()) return@combine Download.STATE_STOPPED
        val allDone = songIds.all { it in done }
        if (allDone) return@combine Download.STATE_COMPLETED

        val allDoneOrDownloading = songIds.all { id ->
            id in done || map[id]?.state.let { s -> s == Download.STATE_QUEUED || s == Download.STATE_DOWNLOADING }
        }
        val hasActive = songIds.any { id ->
            map[id]?.state.let { s -> s == Download.STATE_QUEUED || s == Download.STATE_DOWNLOADING }
        }
        if (allDoneOrDownloading && hasActive) {
            Download.STATE_DOWNLOADING
        } else {
            Download.STATE_STOPPED
        }
    }.distinctUntilChanged()

    fun songsState(songIds: List<String>): Flow<Int> = combine(
        downloadedIds,
        _downloads
    ) { done, map ->
        if (songIds.isEmpty()) return@combine Download.STATE_STOPPED
        val allDone = songIds.all { it in done }
        if (allDone) return@combine Download.STATE_COMPLETED

        val allDoneOrDownloading = songIds.all { id ->
            id in done || map[id]?.state.let { s -> s == Download.STATE_QUEUED || s == Download.STATE_DOWNLOADING }
        }
        val hasActive = songIds.any { id ->
            map[id]?.state.let { s -> s == Download.STATE_QUEUED || s == Download.STATE_DOWNLOADING }
        }
        if (allDoneOrDownloading && hasActive) {
            Download.STATE_DOWNLOADING
        } else {
            Download.STATE_STOPPED
        }
    }.distinctUntilChanged()

    suspend fun enqueue(
        originType: String,
        originId: String,
        originTitle: String?,
        originThumbnailUrl: String?,
        songs: List<DownloadedSongMetadata>
    ) = withContext(Dispatchers.IO) {
        val origins = songs.mapIndexed { index, meta ->
            DownloadOriginEntity(
                songId = meta.id,
                originType = originType,
                originId = if (originType == DownloadOriginType.SONG) meta.id else originId,
                position = index,
                originTitle = originTitle,
                originThumbnailUrl = originThumbnailUrl
            )
        }
        database.insertDownloadOrigins(origins)

        val currentDone = downloadedIds.value
        val currentDownloadsMap = _downloads.value

        for (meta in songs) {
            val isDownloaded = meta.id in currentDone
            val downloadState = currentDownloadsMap[meta.id]?.state
            val isQueuedOrDownloading = downloadState == Download.STATE_QUEUED || downloadState == Download.STATE_DOWNLOADING

            if (!isDownloaded && !isQueuedOrDownloading) {
                DownloadService.sendAddDownload(
                    appContext,
                    ExoDownloadService::class.java,
                    DownloadedSongMetadata.buildDownloadRequest(meta),
                    false
                )
            }
        }
    }

    suspend fun removeOrigin(originType: String, originId: String) = withContext(Dispatchers.IO) {
        val songIds = database.getOriginSongIds(originType, originId)
        database.deleteDownloadOrigin(originType, originId)
        for (songId in songIds) {
            if (database.countOriginsForSong(songId) == 0) {
                DownloadService.sendRemoveDownload(
                    appContext,
                    ExoDownloadService::class.java,
                    songId,
                    false
                )
                database.deleteDownloadedSong(songId)
            }
        }
    }

    suspend fun cancelPending(originType: String, originId: String) = withContext(Dispatchers.IO) {
        val songIds = database.getOriginSongIds(originType, originId)
        val done = downloadedIds.value
        database.deleteDownloadOrigin(originType, originId)
        for (songId in songIds) {
            if (songId !in done) {
                if (database.countOriginsForSong(songId) == 0) {
                    DownloadService.sendRemoveDownload(
                        appContext,
                        ExoDownloadService::class.java,
                        songId,
                        false
                    )
                    database.deleteDownloadedSong(songId)
                }
            }
        }
    }

    private val _progress = MutableStateFlow(DownloadProgress(0, 0, 0, 0, false))
    val progress: StateFlow<DownloadProgress> = _progress.asStateFlow()

    private val sessionIds = mutableSetOf<String>()
    private var sessionActive = false
    private var progressJob: Job? = null

    @Synchronized
    private fun updateProgress() {
        val currentDownloads = _downloads.value
        val activeDownloads = currentDownloads.values.filter {
            it.state == Download.STATE_QUEUED || it.state == Download.STATE_DOWNLOADING
        }
        val hasActiveNow = activeDownloads.isNotEmpty()

        if (hasActiveNow) {
            if (!sessionActive) {
                sessionIds.clear()
                sessionActive = true
            }
            for (download in activeDownloads) {
                sessionIds.add(download.request.id)
            }
        } else {
            sessionActive = false
        }

        if (sessionIds.isEmpty()) {
            _progress.value = DownloadProgress(0, 0, 0, 0, false)
            return
        }

        var completed = 0
        var failed = 0
        var totalPercentSum = 0f

        for (id in sessionIds) {
            val download = currentDownloads[id]
            if (download != null) {
                when (download.state) {
                    Download.STATE_COMPLETED -> {
                        completed++
                        totalPercentSum += 100f
                    }
                    Download.STATE_FAILED -> {
                        failed++
                        val p = if (download.percentDownloaded >= 0f) download.percentDownloaded else 0f
                        totalPercentSum += p
                    }
                    else -> {
                        val p = if (download.percentDownloaded >= 0f) download.percentDownloaded else 0f
                        totalPercentSum += p
                    }
                }
            }
        }

        val total = sessionIds.size
        val avgPercent = if (total > 0) (totalPercentSum / total).toInt().coerceIn(0, 100) else 0

        _progress.value = DownloadProgress(
            total = total,
            completed = completed,
            failed = failed,
            percent = avgPercent,
            isActive = hasActiveNow,
        )
    }

    private fun checkProgressLoop() {
        synchronized(this) {
            if (progressJob?.isActive == true) return
            if (downloadManager.currentDownloads.isEmpty()) return
            progressJob = App.instance.applicationScope.launch(Dispatchers.Default) {
                while (downloadManager.currentDownloads.isNotEmpty()) {
                    val current = downloadManager.currentDownloads
                    if (current.isEmpty()) break
                    _downloads.update { map ->
                        map.toMutableMap().apply {
                            for (download in current) {
                                set(download.request.id, download)
                            }
                        }
                    }
                    updateProgress()
                    delay(500)
                }
                updateProgress()
            }
        }
    }

    private val dataSourceFactory =
        ResolvingDataSource.Factory(
            OkHttpDataSource.Factory(
                mediaOkHttpClient,
            ),
        ) { dataSpec ->
            val mediaId = dataSpec.key ?: error("No media id")
            songUrlCache[mediaId]?.takeIf { it.second > System.currentTimeMillis() }?.let {
                return@Factory dataSpec.withUri(it.first.toUri())
            }
            val playbackData = runBlocking(Dispatchers.IO) {
                val networkMeteredPref = context.dataStore.data.first()[NetworkMeteredKey] ?: true
                YTPlayerUtils.playerResponseForPlayback(
                    mediaId,
                    audioQuality = audioQuality,
                    preferredStreamClient = preferredStreamClient,
                    connectivityManager = connectivityManager,
                    networkMetered = networkMeteredPref,
                    avoidCodecs = avoidStreamCodecs,
                )
            }.getOrThrow()
            val format = playbackData.format

            database.query {
                upsert(
                    FormatEntity(
                        id = mediaId,
                        itag = format.itag,
                        mimeType = format.mimeType.split(";").getOrNull(0) ?: format.mimeType,
                        codecs = format.mimeType.substringAfter("codecs=\"", "").substringBefore("\""),
                        bitrate = format.bitrate,
                        sampleRate = format.audioSampleRate,
                        contentLength = format.contentLength ?: -1L,
                        loudnessDb = playbackData.audioConfig?.loudnessDb,
                        perceptualLoudnessDb = playbackData.audioConfig?.perceptualLoudnessDb,
                        playbackUrl = playbackData.playbackTracking?.videostatsPlaybackUrl?.baseUrl
                    ),
                )
            }

            val streamUrl = playbackData.streamUrl

            songUrlCache[mediaId] = streamUrl to (System.currentTimeMillis() + (playbackData.streamExpiresInSeconds * 1000L))
            dataSpec.withUri(streamUrl.toUri())
        }

    val downloadNotificationHelper =
        DownloadNotificationHelper(context, ExoDownloadService.CHANNEL_ID)

    val downloadManager: DownloadManager =
        DownloadManager(
            context,
            databaseProvider,
            downloadCache,
            dataSourceFactory,
            Executor(Runnable::run)
        ).apply {
            maxParallelDownloads = 3
            addListener(
                object : DownloadManager.Listener {
                    override fun onDownloadChanged(
                        downloadManager: DownloadManager,
                        download: Download,
                        finalException: Exception?,
                    ) {
                        _downloads.update { map ->
                            map.toMutableMap().apply {
                                set(download.request.id, download)
                            }
                        }
                        if (download.state == Download.STATE_FAILED) {
                            Timber.tag("DownloadUtil").e(
                                finalException,
                                "Download failed: id=%s, failureReason=%d",
                                download.request.id,
                                download.failureReason,
                            )
                        }
                        if (download.state == Download.STATE_COMPLETED) {
                            App.instance.applicationScope.launch(Dispatchers.IO) {
                                val meta = DownloadedSongMetadata.fromDownload(download)
                                if (meta != null) {
                                    val isCached = if (download.contentLength > 0) {
                                        downloadCache.isCached(download.request.id, 0, download.contentLength)
                                    } else {
                                        true
                                    }
                                    if (!isCached) {
                                        Timber.tag("DownloadUtil").w("Download completed but cache check failed for id=%s", download.request.id)
                                    } else {
                                        database.upsertDownloadedSong(
                                            DownloadedSongEntity(
                                                id = meta.id,
                                                title = meta.title,
                                                artistName = meta.artistName,
                                                thumbnailUrl = meta.thumbnailUrl,
                                                duration = meta.duration,
                                                albumId = meta.albumId,
                                                albumName = meta.albumName,
                                                explicit = meta.explicit
                                            )
                                        )
                                        if (database.countOriginsForSong(meta.id) == 0) {
                                            database.insertDownloadOrigins(
                                                listOf(
                                                    DownloadOriginEntity(
                                                        songId = meta.id,
                                                        originType = DownloadOriginType.SONG,
                                                        originId = meta.id,
                                                        position = 0
                                                    )
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        updateProgress()
                        checkProgressLoop()
                    }

                    override fun onDownloadRemoved(
                        downloadManager: DownloadManager,
                        download: Download,
                    ) {
                        _downloads.update { map ->
                            map - download.request.id
                        }
                        updateProgress()
                        checkProgressLoop()

                        App.instance.applicationScope.launch(Dispatchers.IO) {
                            val songId = download.request.id
                            database.deleteDownloadedSong(songId)
                            database.deleteOriginsForSong(songId)
                        }
                    }
                }
            )
        }

    init {
        downloadManager.addListener(ExoDownloadService.TerminalStateNotificationHelper(context, downloadNotificationHelper, 1000))
        App.instance.applicationScope.launch(Dispatchers.IO) {
            val result = mutableMapOf<String, Download>()
            downloadManager.downloadIndex.getDownloads().use { cursor ->
                while (cursor.moveToNext()) {
                    result[cursor.download.request.id] = cursor.download
                }
            }
            _downloads.update { actual -> result + actual }
            updateProgress()
            checkProgressLoop()

            val directSongs = database.getDownloadedSongsDirect()
            for (entity in directSongs) {
                val id = entity.id
                val download = result[id]
                val isValid = if (download != null && download.state == Download.STATE_COMPLETED) {
                    if (download.contentLength > 0) {
                        downloadCache.isCached(id, 0, download.contentLength)
                    } else {
                        true
                    }
                } else {
                    false
                }

                if (!isValid) {
                    Timber.tag("DownloadUtil").w("Reconcile: removing invalid downloaded song id=%s", id)
                    database.deleteDownloadedSong(id)
                    database.deleteOriginsForSong(id)
                }
            }

            val currentDownloadedIds = database.getDownloadedSongsDirect().map { it.id }.toSet()
            val allOriginSongIds = database.getAllOriginSongIds()
            for (id in allOriginSongIds) {
                if (id !in currentDownloadedIds && !result.containsKey(id)) {
                    database.deleteOriginsForSong(id)
                }
            }

            val existingIds = database.getDownloadedSongsDirect().map { it.id }.toSet()
            for (download in result.values) {
                if (download.state == Download.STATE_COMPLETED) {
                    val meta = DownloadedSongMetadata.fromDownload(download)
                    if (meta != null && meta.id !in existingIds) {
                        database.upsertDownloadedSong(
                            DownloadedSongEntity(
                                id = meta.id,
                                title = meta.title,
                                artistName = meta.artistName,
                                thumbnailUrl = meta.thumbnailUrl,
                                duration = meta.duration,
                                albumId = meta.albumId,
                                albumName = meta.albumName,
                                explicit = meta.explicit
                            )
                        )
                        database.insertDownloadOrigins(
                            listOf(
                                DownloadOriginEntity(
                                    songId = meta.id,
                                    originType = DownloadOriginType.SONG,
                                    originId = meta.id,
                                    position = 0
                                )
                            )
                        )
                    }
                }
            }
        }
    }

    fun getDownload(songId: String): Flow<Download?> = _downloads.map { it[songId] }

    private fun deviceSupportsMimeType(mimeType: String): Boolean {
        return runCatching {
            val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
            codecList.codecInfos.any { info ->
                !info.isEncoder && info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) }
            }
        }.getOrDefault(false)
    }
}
