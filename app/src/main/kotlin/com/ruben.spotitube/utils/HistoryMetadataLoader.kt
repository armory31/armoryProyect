/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ruben.spotitube.utils

import com.ruben.spotitube.innertube.YouTube
import com.ruben.spotitube.innertube.models.SongItem
import com.ruben.spotitube.ui.utils.resize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Collections
import kotlin.time.Duration.Companion.milliseconds

object HistoryMetadataLoader {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingLoadingIds = Collections.synchronizedSet(mutableSetOf<String>())

    fun loadMetadataForIds(ids: List<String>): Job? {
        val uniqueIds = ids.distinct().filter { 
            HistoryItemMemoryCache.get(it) == null && 
            !HistoryItemMemoryCache.isFailedRecently(it) && 
            !pendingLoadingIds.contains(it) 
        }
        if (uniqueIds.isEmpty()) return null

        pendingLoadingIds.addAll(uniqueIds)

        return scope.launch {
            try {
                uniqueIds.chunked(50).forEach { chunk ->
                    if (!isActive) return@forEach
                    YouTube.queue(chunk).onSuccess { songItems ->
                        HistoryItemMemoryCache.putAll(songItems)
                        val returnedIds = songItems.map { it.id }.toSet()
                        if (isActive) {
                            chunk.filterNot { returnedIds.contains(it) }.forEach {
                                HistoryItemMemoryCache.markFailed(it)
                            }
                        }
                    }.onFailure { throwable ->
                        if (throwable is kotlinx.coroutines.CancellationException) {
                            throw throwable
                        }
                        if (isActive) {
                            chunk.forEach { HistoryItemMemoryCache.markFailed(it) }
                        }
                    }
                }
            } finally {
                pendingLoadingIds.removeAll(uniqueIds.toSet())
            }
        }
    }

    suspend fun resolve(ids: List<String>, timeoutMs: Long = 5000L): Map<String, SongItem> = withContext(Dispatchers.IO) {
        val distinctIds = ids.distinct()
        val missingIds = distinctIds.filter { 
            HistoryItemMemoryCache.get(it) == null && !HistoryItemMemoryCache.isFailedRecently(it) 
        }

        if (missingIds.isNotEmpty()) {
            withTimeoutOrNull(timeoutMs.milliseconds) {
                missingIds.chunked(50).map { chunk ->
                    async {
                        YouTube.queue(chunk).onSuccess { songItems ->
                            HistoryItemMemoryCache.putAll(songItems)
                            val returnedIds = songItems.map { it.id }.toSet()
                            chunk.filterNot { returnedIds.contains(it) }.forEach {
                                HistoryItemMemoryCache.markFailed(it)
                            }
                        }.onFailure { throwable ->
                            if (throwable !is kotlinx.coroutines.CancellationException) {
                                chunk.forEach { HistoryItemMemoryCache.markFailed(it) }
                            }
                        }
                    }
                }.awaitAll()
            }
        }

        distinctIds.mapNotNull { id ->
            HistoryItemMemoryCache.get(id)?.let { id to it }
        }.toMap()
    }

    suspend fun resolveArtists(ids: List<String>, timeoutMs: Long = 5000L): Map<String, ArtistMetadata> = withContext(Dispatchers.IO) {
        val distinctIds = ids.distinct()
        val missingIds = distinctIds.filter { HistoryArtistMemoryCache.get(it) == null }

        if (missingIds.isNotEmpty()) {
            withTimeoutOrNull(timeoutMs.milliseconds) {
                missingIds.map { artistId ->
                    async {
                        YouTube.artist(artistId).onSuccess { page ->
                            val meta = ArtistMetadata(
                                id = artistId,
                                name = page.artist.title,
                                thumbnailUrl = page.artist.thumbnail?.resize(544, 544)
                            )
                            HistoryArtistMemoryCache.put(artistId, meta)
                        }
                    }
                }.awaitAll()
            }
        }

        distinctIds.mapNotNull { id ->
            HistoryArtistMemoryCache.get(id)?.let { id to it }
        }.toMap()
    }

    suspend fun resolveAlbums(ids: List<String>, timeoutMs: Long = 5000L): Map<String, AlbumMetadata> = withContext(Dispatchers.IO) {
        val distinctIds = ids.distinct()
        val missingIds = distinctIds.filter { HistoryAlbumMemoryCache.get(it) == null }

        if (missingIds.isNotEmpty()) {
            withTimeoutOrNull(timeoutMs.milliseconds) {
                missingIds.map { albumId ->
                    async {
                        YouTube.album(albumId).onSuccess { page ->
                            val meta = AlbumMetadata(
                                id = albumId,
                                title = page.album.title,
                                artistName = page.album.artists?.joinToString(", ") { it.name }?.ifEmpty { null },
                                thumbnailUrl = page.album.thumbnail?.resize(544, 544),
                                songCount = page.songs.size
                            )
                            HistoryAlbumMemoryCache.put(albumId, meta)
                        }
                    }
                }.awaitAll()
            }
        }

        distinctIds.mapNotNull { id ->
            HistoryAlbumMemoryCache.get(id)?.let { id to it }
        }.toMap()
    }
}
