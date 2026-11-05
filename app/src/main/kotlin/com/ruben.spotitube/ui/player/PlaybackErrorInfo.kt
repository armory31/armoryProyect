package com.ruben.spotitube.ui.player

/*
 * ArchiveTune (2026)
 * © Chartreux Westia — github.com/koiverse
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource

internal enum class PlaybackErrorKind {
    NoInternet,
    Timeout,
    NoStream,
    MalformedStream,
    Decoder,
    Http,
    Unknown,
}

internal data class PlaybackErrorInfo(
    val kind: PlaybackErrorKind,
    val httpCode: Int?,
)

internal fun PlaybackException.toPlaybackErrorInfo(currentMediaId: String? = null): PlaybackErrorInfo {
    val httpCode = httpStatusCodeOrNull()
    val kind =
        when {
            errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> PlaybackErrorKind.NoInternet
            errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> PlaybackErrorKind.Timeout
            httpCode in setOf(403, 404, 410, 416) -> PlaybackErrorKind.NoStream
            errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> PlaybackErrorKind.MalformedStream
            errorCode in setOf(
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                PlaybackException.ERROR_CODE_DECODING_FAILED,
                PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            ) -> PlaybackErrorKind.Decoder
            httpCode != null -> PlaybackErrorKind.Http
            else -> PlaybackErrorKind.Unknown
        }

    return PlaybackErrorInfo(
        kind = kind,
        httpCode = httpCode,
    )
}

internal fun PlaybackException.httpStatusCodeOrNull(): Int? {
    var throwable: Throwable? = cause
    while (throwable != null) {
        if (throwable is HttpDataSource.InvalidResponseCodeException) return throwable.responseCode
        throwable = throwable.cause
    }
    return null
}
