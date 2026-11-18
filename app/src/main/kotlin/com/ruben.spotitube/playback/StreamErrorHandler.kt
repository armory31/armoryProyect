package com.ruben.spotitube.playback

import androidx.datastore.preferences.core.edit
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.ExoPlayer
import com.ruben.spotitube.App
import com.ruben.spotitube.constants.AutoSkipNextOnErrorKey
import com.ruben.spotitube.constants.PlayerStreamClient
import com.ruben.spotitube.constants.SkipSilenceKey
import com.ruben.spotitube.utils.YTPlayerUtils
import com.ruben.spotitube.utils.dataStore
import com.ruben.spotitube.utils.get
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import timber.log.Timber
import kotlin.math.min

class StreamErrorHandler(
    private val player: () -> ExoPlayer,
    private val scope: () -> CoroutineScope,
    private val isNetworkConnected: () -> Boolean,
    private val downloadCache: () -> Cache,
    private val playerCache: () -> Cache,
    private val preferredStreamClient: () -> PlayerStreamClient,
    private val onWaitForNetwork: () -> Unit,
    private val onSkipOnError: () -> Unit,
    private val onStopOnError: () -> Unit,
) {
    @Volatile
    private var pendingStreamRefreshValidationMediaId: String? = null

    @Volatile
    private var refreshValidatedPlayingMediaId: String? = null

    fun clearStreamRefreshGuards(activeMediaId: String? = null) {
        val normalizedActiveMediaId = activeMediaId?.trim()?.takeIf { it.isNotBlank() }
        if (normalizedActiveMediaId == null || refreshValidatedPlayingMediaId != normalizedActiveMediaId) {
            refreshValidatedPlayingMediaId = null
        }
        if (normalizedActiveMediaId == null || pendingStreamRefreshValidationMediaId != normalizedActiveMediaId) {
            pendingStreamRefreshValidationMediaId = null
        }
    }

    fun onPlaybackReady(activeMediaId: String?) {
        val p = player()
        if (
            p.playbackState == Player.STATE_READY &&
            p.playWhenReady &&
            p.isPlaying &&
            activeMediaId != null &&
            pendingStreamRefreshValidationMediaId == activeMediaId
        ) {
            refreshValidatedPlayingMediaId = activeMediaId
            pendingStreamRefreshValidationMediaId = null
            MusicService.streamRecoveryState.remove(activeMediaId)
            Timber.tag("MusicService").i("Stream refresh validated and playback resumed for $activeMediaId")
        }
    }

    fun retryCurrentFromFreshStream() {
        val p = player()
        val mediaId = p.currentMediaItem?.mediaId ?: return
        clearStreamRefreshGuards(mediaId)
        YTPlayerUtils.invalidateCachedStreamUrls(mediaId)
        MusicService.playbackUrlCache.remove(mediaId)
        pendingStreamRefreshValidationMediaId = mediaId
        p.prepare()
        p.playWhenReady = true
    }

    private fun PlaybackException.httpStatusCodeOrNull(): Int? {
        var t: Throwable? = cause
        while (t != null) {
            if (t is HttpDataSource.InvalidResponseCodeException) return t.responseCode
            t = t.cause
        }
        return null
    }

    private fun markAndCheckRecoveryAllowance(mediaId: String): Boolean {
        val now = System.currentTimeMillis()
        val (count, lastAt) = MusicService.streamRecoveryState[mediaId] ?: (0 to 0L)
        val nextCount = if (now - lastAt > 45_000L) 1 else count + 1
        if (nextCount > 2) return false
        MusicService.streamRecoveryState[mediaId] = nextCount to now
        return true
    }

    private fun shouldSkipRedundantStreamRefresh(mediaId: String): Boolean {
        if (refreshValidatedPlayingMediaId != mediaId) return false
        val expiresAt = MusicService.playbackUrlCache[mediaId]?.second ?: return false
        if (expiresAt <= System.currentTimeMillis()) {
            refreshValidatedPlayingMediaId = null
            return false
        }
        return true
    }

    fun onPlayerError(error: PlaybackException) {
        val p = player()
        val isConnectionError = (error.cause?.cause is PlaybackException) &&
                (error.cause?.cause as PlaybackException).errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED

        if (!isNetworkConnected() || isConnectionError) {
            onWaitForNetwork()
            return
        }

        val currentMediaId = p.currentMediaItem?.mediaId
        val httpStatusCode = error.httpStatusCodeOrNull()

        if (currentMediaId != null && YTPlayerUtils.isBotDetectionException(error)) {
            if (markAndCheckRecoveryAllowance(currentMediaId)) {
                Timber.tag("MusicService").w(
                    "Bot detection error for $currentMediaId — clearing caches and retrying with fresh stream"
                )
                YTPlayerUtils.invalidateCachedStreamUrls(currentMediaId)
                MusicService.playbackUrlCache.remove(currentMediaId)
                pendingStreamRefreshValidationMediaId = currentMediaId
                p.prepare()
                p.playWhenReady = true
                return
            }
        }

        val shouldAttemptStreamRefresh =
            currentMediaId != null && (
                error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE ||
                    httpStatusCode in setOf(403, 404, 410, 416, 429, 500, 502, 503)
                )

        if (currentMediaId != null && error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND) {
            scope().launch(Dispatchers.IO) {
                runCatching { downloadCache().removeResource(currentMediaId) }
                runCatching { playerCache().removeResource(currentMediaId) }
            }
        }

        if (shouldAttemptStreamRefresh && shouldSkipRedundantStreamRefresh(currentMediaId)) {
            Timber.tag("MusicService").w(
                "Skipping redundant stream refresh for $currentMediaId after validated recovery; resuming playback without URL refresh"
            )
            refreshValidatedPlayingMediaId = null
            p.prepare()
            p.playWhenReady = true
            return
        }

        if (shouldAttemptStreamRefresh && markAndCheckRecoveryAllowance(currentMediaId)) {
            val failingStreamClientKey =
                MusicService.playbackUrlCache[currentMediaId]
                    ?.first
                    ?.toHttpUrlOrNull()
                    ?.queryParameter("c")
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
            Timber.tag("MusicService").w(
                "Attempting stream refresh for $currentMediaId (http=$httpStatusCode, code=${error.errorCode}, client=${failingStreamClientKey ?: "unknown"})"
            )
            YTPlayerUtils.markStreamClientFailed(currentMediaId, failingStreamClientKey, httpStatusCode)
            YTPlayerUtils.markPreferredClientFailed(currentMediaId, preferredStreamClient(), httpStatusCode)
            YTPlayerUtils.invalidateCachedStreamUrls(currentMediaId)
            MusicService.playbackUrlCache.remove(currentMediaId)
            pendingStreamRefreshValidationMediaId = currentMediaId
            p.prepare()
            p.playWhenReady = true
            return
        }

        val skipSilenceCurrentlyEnabled = App.instance.dataStore.get(SkipSilenceKey, false)
        val causeText = (error.cause?.stackTraceToString() ?: error.stackTraceToString()).lowercase()
        val looksLikeSilenceProcessor = skipSilenceCurrentlyEnabled && (
            "silenceskippingaudioprocessor" in causeText || "silence" in causeText
        )

        if (looksLikeSilenceProcessor) {
            scope().launch {
                try {
                    App.instance.dataStore.edit { settings ->
                        settings[SkipSilenceKey] = false
                    }
                    p.skipSilenceEnabled = false
                    val currentPos = p.currentPosition
                    val targetPos = min(currentPos + 1500L, if (p.duration > 0) p.duration - 1000L else currentPos + 1500L)
                    p.seekTo(targetPos)
                    p.prepare()
                    p.play()
                    return@launch
                } catch (t: Throwable) {
                    Timber.tag("MusicService").e(t, "failed to recover from silence-skipper error")
                }
                if (App.instance.dataStore.get(AutoSkipNextOnErrorKey, false)) {
                    onSkipOnError()
                } else {
                    onStopOnError()
                }
            }

            return
        }

        if (App.instance.dataStore.get(AutoSkipNextOnErrorKey, false)) {
            onSkipOnError()
        } else {
            onStopOnError()
        }
    }
}
