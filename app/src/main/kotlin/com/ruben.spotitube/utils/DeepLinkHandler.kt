package com.ruben.spotitube.utils

import android.content.Intent
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.navigation.NavHostController
import com.ruben.spotitube.BuildConfig
import com.ruben.spotitube.extensions.toMediaItem
import com.ruben.spotitube.innertube.YouTube
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DeepLinkHandler(
    private val scope: CoroutineScope,
    private val onPlaySong: (MediaItem) -> Unit,
    private val startMusicService: () -> Unit,
) {
    fun handle(intent: Intent, navController: NavHostController) {
        val navigateTo = intent.getStringExtra("navigate_to")
        if (navigateTo != null) {
            navController.navigate(navigateTo) {
                launchSingleTop = true
            }
            intent.removeExtra("navigate_to")
            return
        }

        val uri = intent.data ?: intent.extras?.getString(Intent.EXTRA_TEXT)?.toUri()
        if (uri == null) return

        val coroutineScope = scope
        val authority = uri.authority?.lowercase()
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()
        val pathSegments = uri.pathSegments.filter(String::isNotBlank)

        fun navigateWithPop(targetRoute: String, routePrefix: String) {
            val currentRoute = navController.currentDestination?.route.orEmpty()
            if (currentRoute.startsWith(routePrefix)) {
                navController.popBackStack()
            }
            navController.navigate(targetRoute) {
                launchSingleTop = true
            }
        }

        val normalizedDeepLinkPair: Pair<String, String>? = when {
            scheme == "armory" -> {
                val type = authority
                val id = pathSegments.firstOrNull()
                if (!type.isNullOrBlank() && !id.isNullOrBlank()) type to id else null
            }
            scheme == "https" && host == BuildConfig.SHARE_HOST.lowercase() -> {
                val type = pathSegments.firstOrNull()?.lowercase()
                val id = pathSegments.getOrNull(1)
                if (!type.isNullOrBlank() && !id.isNullOrBlank()) type to id else null
            }
            else -> null
        }

        if (normalizedDeepLinkPair != null) {
            val (type, id) = normalizedDeepLinkPair

            when (type) {
                "together" -> {
                    val code = id.filter { it in '0'..'9' }
                    if (code.length == 6) {
                        navigateWithPop("settings/music_together?code=$code", "settings/music_together")
                    }
                    return
                }
                "song" -> {
                    coroutineScope.launch {
                        try {
                            val result = withContext(Dispatchers.IO) {
                                YouTube.queue(listOf(id))
                            }
                            result.onSuccess { queued ->
                                val mediaItem =
                                    queued.firstOrNull { it.id == id }?.toMediaItem()
                                        ?: queued.firstOrNull()?.toMediaItem()
                                        ?: MediaItem
                                            .Builder()
                                            .setMediaId(id)
                                            .setUri(id)
                                            .setCustomCacheKey(id)
                                            .build()
                                startMusicService()
                                onPlaySong(mediaItem)
                            }.onFailure {
                                reportException(it)
                            }
                        } catch (e: Exception) {
                            reportException(e)
                        }
                    }
                    return
                }
                "album" -> {
                    navigateWithPop("album/$id", "album")
                    return
                }
                "artist" -> {
                    navigateWithPop("artist/$id", "artist")
                    return
                }
                "playlist" -> {
                    val cleanPlaylistId = id.removePrefix("VL")
                    navigateWithPop("online_playlist/$cleanPlaylistId", "online_playlist")
                    return
                }
            }
        }

        when (val path = uri.pathSegments.firstOrNull()) {
            "playlist" -> uri.getQueryParameter("list")?.let { playlistId ->
                if (playlistId.startsWith("OLAK5uy_")) {
                    coroutineScope.launch {
                        YouTube.albumSongs(playlistId).onSuccess { songs ->
                            songs.firstOrNull()?.album?.id?.let { browseId ->
                                navigateWithPop("album/$browseId", "album")
                            }
                        }.onFailure { reportException(it) }
                    }
                } else {
                    navigateWithPop("online_playlist/$playlistId", "online_playlist")
                }
            }

            "browse" -> uri.lastPathSegment?.let { browseId ->
                navigateWithPop("album/$browseId", "album")
            }

            "channel", "c" -> uri.lastPathSegment?.let { artistId ->
                navigateWithPop("artist/$artistId", "artist")
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
                        try {
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
                                startMusicService()
                                onPlaySong(mediaItem)
                            }.onFailure {
                                reportException(it)
                            }
                        } catch (e: Exception) {
                            reportException(e)
                        }
                    }
                }
            }
        }
    }
}
