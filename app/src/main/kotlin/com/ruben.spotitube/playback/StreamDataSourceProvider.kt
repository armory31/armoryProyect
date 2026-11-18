package com.ruben.spotitube.playback

import android.content.Context
import android.media.MediaCodecList
import android.net.ConnectivityManager
import androidx.core.net.toUri
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.mp4.FragmentedMp4Extractor
import androidx.media3.extractor.mp4.Mp4Extractor
import com.ruben.spotitube.R
import com.ruben.spotitube.constants.AudioQuality
import com.ruben.spotitube.constants.PlayerStreamClient
import com.ruben.spotitube.db.MusicDatabase
import com.ruben.spotitube.db.entities.FormatEntity
import com.ruben.spotitube.extensions.findNextMediaItemById
import com.ruben.spotitube.extensions.metadata
import com.ruben.spotitube.innertube.YouTube
import com.ruben.spotitube.utils.StreamClientUtils
import com.ruben.spotitube.utils.YTPlayerUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import timber.log.Timber
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class StreamDataSourceProvider(
    private val context: Context,
    private val database: MusicDatabase,
    private val player: () -> ExoPlayer,
    private val scope: () -> CoroutineScope,
    private val playerCache: () -> Cache,
    private val downloadCache: () -> Cache,
    private val connectivityManager: () -> ConnectivityManager,
    private val audioQuality: () -> AudioQuality,
    private val preferredStreamClient: () -> PlayerStreamClient,
) {
    private val resolvedSongIdsSet = java.util.Collections.synchronizedSet(mutableSetOf<String>())

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

    private fun deviceSupportsMimeType(mimeType: String): Boolean {
        return runCatching {
            val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
            codecList.codecInfos.any { info ->
                !info.isEncoder && info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) }
            }
        }.getOrDefault(false)
    }

    private suspend fun recoverSong(
        mediaId: String,
        playbackData: YTPlayerUtils.PlaybackData? = null
    ) {
        if (resolvedSongIdsSet.contains(mediaId)) return
        val song = database.getSavedSongDirect(mediaId)
        if (song == null || song.duration != -1) {
            if (resolvedSongIdsSet.size > 200) resolvedSongIdsSet.clear()
            resolvedSongIdsSet.add(mediaId)
            return
        }

        val mediaMetadata = withContext(Dispatchers.Main) {
            player().findNextMediaItemById(mediaId)?.metadata
        }
        if (mediaMetadata == null) {
            if (resolvedSongIdsSet.size > 200) resolvedSongIdsSet.clear()
            resolvedSongIdsSet.add(mediaId)
            return
        }

        val duration = mediaMetadata.duration.takeIf { it != -1 }
            ?: (playbackData?.videoDetails ?: YTPlayerUtils.playerResponseForMetadata(mediaId)
                .getOrNull()?.videoDetails)?.lengthSeconds?.toInt()
            ?: -1

        if (duration != -1) {
            database.query {
                updateSongDuration(mediaId, duration)
            }
        }
        if (resolvedSongIdsSet.size > 200) resolvedSongIdsSet.clear()
        resolvedSongIdsSet.add(mediaId)
    }

    private fun createCacheDataSource(): CacheDataSource.Factory =
        CacheDataSource
            .Factory()
            .setCache(downloadCache())
            .setUpstreamDataSourceFactory(
                CacheDataSource
                    .Factory()
                    .setCache(playerCache())
                    .setUpstreamDataSourceFactory(
                        DefaultDataSource.Factory(
                            context,
                            OkHttpDataSource.Factory(
                                mediaOkHttpClient,
                            ),
                        ),
                    )
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            ).setCacheWriteDataSinkFactory(null)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    private fun createDataSourceFactory(): DataSource.Factory {
        return ResolvingDataSource.Factory(createCacheDataSource()) { dataSpec ->
            val mediaId = dataSpec.key ?: error("No media id")

            val requiredCachedLength =
                if (dataSpec.length >= 0) {
                    dataSpec.length
                } else {
                    val contentLength =
                        runBlocking(Dispatchers.IO) {
                            database.format(mediaId).first()?.contentLength
                        } ?: runCatching {
                            downloadCache()
                                .getContentMetadata(mediaId)
                                .get(ContentMetadata.KEY_CONTENT_LENGTH, -1L)
                        }.getOrNull()?.takeIf { it > 0L } ?: runCatching {
                            playerCache()
                                .getContentMetadata(mediaId)
                                .get(ContentMetadata.KEY_CONTENT_LENGTH, -1L)
                        }.getOrNull()?.takeIf { it > 0L }

                    contentLength?.let { nonNullContentLength ->
                        (nonNullContentLength - dataSpec.position).takeIf { it > 0L }
                    }
                }

            if (requiredCachedLength != null) {
                val isFullyCached =
                    downloadCache().isCached(mediaId, dataSpec.position, requiredCachedLength) ||
                        playerCache().isCached(mediaId, dataSpec.position, requiredCachedLength)
                if (isFullyCached) {
                    scope().launch(Dispatchers.IO) { recoverSong(mediaId) }
                    return@Factory dataSpec
                }
            }

            MusicService.playbackUrlCache[mediaId]?.takeIf { it.second > System.currentTimeMillis() }?.let {
                scope().launch(Dispatchers.IO) { recoverSong(mediaId) }
                return@Factory dataSpec.withUri(it.first.toUri())
            }

            val playbackData = runBlocking(Dispatchers.IO) {
                YTPlayerUtils.playerResponseForPlayback(
                    mediaId,
                    audioQuality = audioQuality(),
                    connectivityManager = connectivityManager(),
                    preferredStreamClient = preferredStreamClient(),
                    avoidCodecs = avoidStreamCodecs,
                )
            }.getOrElse { throwable ->
                when (throwable) {
                    is PlaybackException -> throw throwable

                    is ConnectException, is UnknownHostException -> {
                        throw PlaybackException(
                            context.getString(R.string.error_no_internet),
                            throwable,
                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
                        )
                    }

                    is SocketTimeoutException -> {
                        throw PlaybackException(
                            context.getString(R.string.error_timeout),
                            throwable,
                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
                        )
                    }

                    else -> throw PlaybackException(
                        context.getString(R.string.error_unknown),
                        throwable,
                        PlaybackException.ERROR_CODE_REMOTE_ERROR
                    )
                }
            }

            run {
                val format = playbackData.format
                val loudnessDb = playbackData.audioConfig?.loudnessDb
                val perceptualLoudnessDb = playbackData.audioConfig?.perceptualLoudnessDb

                Timber.tag("AudioNormalization").d("Storing format for $mediaId with loudnessDb: $loudnessDb, perceptualLoudnessDb: $perceptualLoudnessDb")
                if (loudnessDb == null && perceptualLoudnessDb == null) {
                    Timber.tag("AudioNormalization").w("No loudness data available from YouTube for video: $mediaId")
                }

                database.query {
                    upsert(
                        FormatEntity(
                            id = mediaId,
                            itag = format.itag,
                            mimeType = format.mimeType.substringBefore(";"),
                            codecs = format.mimeType.substringAfter("codecs=\"", "").substringBefore("\""),
                            bitrate = format.bitrate,
                            sampleRate = format.audioSampleRate,
                            contentLength = format.contentLength ?: -1L,
                            loudnessDb = loudnessDb,
                            perceptualLoudnessDb = perceptualLoudnessDb,
                            playbackUrl = playbackData.playbackTracking?.videostatsPlaybackUrl?.baseUrl
                        )
                    )
                }
                scope().launch(Dispatchers.IO) { recoverSong(mediaId, playbackData) }

                val streamUrl = playbackData.streamUrl

                MusicService.playbackUrlCache[mediaId] =
                    streamUrl to System.currentTimeMillis() + (playbackData.streamExpiresInSeconds * 1000L)
                return@Factory dataSpec.withUri(streamUrl.toUri())
            }
        }
    }

    fun createMediaSourceFactory() =
        DefaultMediaSourceFactory(
            createDataSourceFactory(),
        ) {
            arrayOf(Mp4Extractor(), FragmentedMp4Extractor(), MatroskaExtractor())
        }

    fun createRenderersFactory() =
        object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ) = DefaultAudioSink
                .Builder(this@StreamDataSourceProvider.context)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessorChain(
                    DefaultAudioSink.DefaultAudioProcessorChain(
                        SilenceSkippingAudioProcessor(
                            1_500_000L,
                            0.35f,
                            500_000L,
                            10,
                            150.toShort(),
                        ),
                        androidx.media3.common.audio.SonicAudioProcessor(),
                    ),
                ).build()
        }
}
