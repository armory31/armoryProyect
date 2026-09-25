/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ruben.spotitube.playback.queues

import androidx.media3.common.MediaItem
import com.ruben.spotitube.db.MusicDatabase
import com.ruben.spotitube.extensions.toMediaItem
import com.ruben.spotitube.innertube.YouTube
import com.ruben.spotitube.innertube.models.WatchEndpoint
import com.ruben.spotitube.models.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class LocalMixQueue(
    private val database: MusicDatabase,
    private val playlistId: String,
    private val maxMixSize: Int = 50,
) : Queue {
    override val preloadItem: MediaMetadata? = null

    override suspend fun getInitialStatus(): Queue.Status = withContext(Dispatchers.IO) {
        val playlistSongEntities = database.playlistSongs(playlistId).first()
        val playlistSongIds = playlistSongEntities.map { it.map.songId }

        val relatedSongs = playlistSongIds.flatMap { songId ->
            database.relatedSongs(songId)
        }
        val uniqueRelated = relatedSongs.filter { song -> song.id !in playlistSongIds }.distinctBy { it.id }
        val finalMix = uniqueRelated.take(maxMixSize)

        if (finalMix.isNotEmpty()) {
            return@withContext Queue.Status(
                title = "Mix from Playlist",
                items = finalMix.map { it.toMediaItem() },
                mediaItemIndex = 0,
            )
        }

        val seedSongId = playlistSongIds.firstOrNull()
            ?: return@withContext Queue.Status(title = "Mix from Playlist", items = emptyList(), mediaItemIndex = 0)

        val nextResult = YouTube.next(WatchEndpoint(videoId = seedSongId)).getOrNull()
        val fromNext = nextResult?.items
            ?.map { it.toMediaItem() }
            ?.filter { it.mediaId !in playlistSongIds }
            .orEmpty()

        val fromRelated = nextResult?.relatedEndpoint?.let { endpoint ->
            YouTube.related(endpoint).getOrNull()?.songs
                ?.map { it.toMediaItem() }
                ?.filter { it.mediaId !in playlistSongIds }
        }.orEmpty()

        val onlineMix = (fromNext + fromRelated)
            .distinctBy { it.mediaId }
            .take(maxMixSize)

        Queue.Status(
            title = "Mix from Playlist",
            items = onlineMix,
            mediaItemIndex = 0,
        )
    }

    override fun hasNextPage(): Boolean = false
    override suspend fun nextPage(): List<MediaItem> = emptyList()
}