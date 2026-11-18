/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ruben.spotitube.utils

import com.ruben.spotitube.innertube.YouTube
import com.ruben.spotitube.innertube.models.SongItem
import com.ruben.spotitube.ui.utils.resize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

object SongMetadataLoader {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val inFlightSongs = ConcurrentHashMap<String, Deferred<Unit>>()
    private val inFlightArtists = ConcurrentHashMap<String, Deferred<Unit>>()
    private val inFlightAlbums = ConcurrentHashMap<String, Deferred<Unit>>()

    fun loadMetadataForIds(ids: List<String>): Job? {
        val uniqueIds = ids.distinct().filter {
            SongItemMemoryCache.get(it) == null &&
            !SongItemMemoryCache.isFailedRecently(it) &&
            !inFlightSongs.containsKey(it)
        }
        if (uniqueIds.isEmpty()) return null

        val chunkDeferreds = uniqueIds.associateWith { CompletableDeferred<Unit>() }
        chunkDeferreds.forEach { (id, def) -> inFlightSongs[id] = def }

        return scope.launch {
            try {
                uniqueIds.chunked(50).forEach { chunk ->
                    if (!isActive) return@forEach
                    YouTube.queue(chunk).onSuccess { songItems ->
                        SongItemMemoryCache.putAll(songItems)
                        val returnedIds = songItems.map { it.id }.toSet()
                        if (isActive) {
                            chunk.filterNot { returnedIds.contains(it) }.forEach {
                                SongItemMemoryCache.markFailed(it)
                            }
                        }
                    }.onFailure { throwable ->
                        if (throwable !is CancellationException) {
                            if (isActive) {
                                chunk.forEach { SongItemMemoryCache.markFailed(it) }
                            }
                        }
                    }
                }
            } finally {
                uniqueIds.forEach { id ->
                    inFlightSongs.remove(id)
                    chunkDeferreds[id]?.complete(Unit)
                }
            }
        }
    }

    suspend fun resolve(ids: List<String>, timeoutMs: Long = 5000L): Map<String, SongItem> = withContext(Dispatchers.IO) {
        val distinctIds = ids.distinct()
        if (distinctIds.isEmpty()) return@withContext emptyMap()

        withTimeoutOrNull(300.milliseconds) {
            MetadataDiskCache.awaitLoaded()
        }

        val memHitIds = distinctIds.filter { SongItemMemoryCache.get(it) != null }
        val remainingIds = distinctIds.filter { SongItemMemoryCache.get(it) == null && !SongItemMemoryCache.isFailedRecently(it) }

        val inFlightSharedIds = remainingIds.filter { inFlightSongs.containsKey(it) }
        val neededNetworkIds = remainingIds.filter { !inFlightSongs.containsKey(it) }

        val deferredsToWait = mutableListOf<Deferred<Unit>>()

        inFlightSharedIds.forEach { id ->
            inFlightSongs[id]?.let { deferredsToWait.add(it) }
        }

        if (neededNetworkIds.isNotEmpty()) {
            neededNetworkIds.chunked(50).forEach { chunk ->
                val chunkDeferreds = chunk.associateWith { CompletableDeferred<Unit>() }
                chunkDeferreds.forEach { (id, def) ->
                    inFlightSongs[id] = def
                    deferredsToWait.add(def)
                }

                scope.launch {
                    try {
                        YouTube.queue(chunk).onSuccess { songItems ->
                            SongItemMemoryCache.putAll(songItems)
                            val returnedIds = songItems.map { it.id }.toSet()
                            chunk.filterNot { returnedIds.contains(it) }.forEach {
                                SongItemMemoryCache.markFailed(it)
                            }
                        }.onFailure { throwable ->
                            if (throwable !is CancellationException) {
                                chunk.forEach { SongItemMemoryCache.markFailed(it) }
                            }
                        }
                    } finally {
                        chunk.forEach { id ->
                            inFlightSongs.remove(id)
                            chunkDeferreds[id]?.complete(Unit)
                        }
                    }
                }
            }
        }

        PerfTiming.log("[META RESOLVE] type=songs requested=${distinctIds.size} memHit=${memHitIds.size} inFlightShared=${inFlightSharedIds.size} network=${neededNetworkIds.size}")

        if (deferredsToWait.isNotEmpty()) {
            withTimeoutOrNull(timeoutMs.milliseconds) {
                deferredsToWait.awaitAll()
            }
        }

        distinctIds.mapNotNull { id ->
            SongItemMemoryCache.get(id)?.let { id to it }
        }.toMap()
    }

    suspend fun resolveArtists(ids: List<String>, timeoutMs: Long = 5000L): Map<String, ArtistMetadata> = withContext(Dispatchers.IO) {
        val distinctIds = ids.distinct()
        if (distinctIds.isEmpty()) return@withContext emptyMap()

        withTimeoutOrNull(300.milliseconds) {
            MetadataDiskCache.awaitLoaded()
        }

        val memHitIds = distinctIds.filter { ArtistMemoryCache.get(it) != null }
        val remainingIds = distinctIds.filter { ArtistMemoryCache.get(it) == null }

        val inFlightSharedIds = remainingIds.filter { inFlightArtists.containsKey(it) }
        val neededNetworkIds = remainingIds.filter { !inFlightArtists.containsKey(it) }

        val deferredsToWait = mutableListOf<Deferred<Unit>>()

        inFlightSharedIds.forEach { id ->
            inFlightArtists[id]?.let { deferredsToWait.add(it) }
        }

        if (neededNetworkIds.isNotEmpty()) {
            neededNetworkIds.forEach { artistId ->
                val def = CompletableDeferred<Unit>()
                inFlightArtists[artistId] = def
                deferredsToWait.add(def)

                scope.launch {
                    try {
                        YouTube.artist(artistId).onSuccess { page ->
                            val meta = ArtistMetadata(
                                id = artistId,
                                name = page.artist.title,
                                thumbnailUrl = page.artist.thumbnail?.resize(544, 544)
                            )
                            ArtistMemoryCache.put(artistId, meta)
                        }
                    } finally {
                        inFlightArtists.remove(artistId)
                        def.complete(Unit)
                    }
                }
            }
        }

        PerfTiming.log("[META RESOLVE] type=artists requested=${distinctIds.size} memHit=${memHitIds.size} inFlightShared=${inFlightSharedIds.size} network=${neededNetworkIds.size}")

        if (deferredsToWait.isNotEmpty()) {
            withTimeoutOrNull(timeoutMs.milliseconds) {
                deferredsToWait.awaitAll()
            }
        }

        distinctIds.mapNotNull { id ->
            ArtistMemoryCache.get(id)?.let { id to it }
        }.toMap()
    }

    suspend fun resolveAlbums(ids: List<String>, timeoutMs: Long = 5000L): Map<String, AlbumMetadata> = withContext(Dispatchers.IO) {
        val distinctIds = ids.distinct()
        if (distinctIds.isEmpty()) return@withContext emptyMap()

        withTimeoutOrNull(300.milliseconds) {
            MetadataDiskCache.awaitLoaded()
        }

        val memHitIds = distinctIds.filter { AlbumMemoryCache.get(it) != null }
        val remainingIds = distinctIds.filter { AlbumMemoryCache.get(it) == null }

        val inFlightSharedIds = remainingIds.filter { inFlightAlbums.containsKey(it) }
        val neededNetworkIds = remainingIds.filter { !inFlightAlbums.containsKey(it) }

        val deferredsToWait = mutableListOf<Deferred<Unit>>()

        inFlightSharedIds.forEach { id ->
            inFlightAlbums[id]?.let { deferredsToWait.add(it) }
        }

        if (neededNetworkIds.isNotEmpty()) {
            neededNetworkIds.forEach { albumId ->
                val def = CompletableDeferred<Unit>()
                inFlightAlbums[albumId] = def
                deferredsToWait.add(def)

                scope.launch {
                    try {
                        YouTube.album(albumId).onSuccess { page ->
                            val meta = AlbumMetadata(
                                id = albumId,
                                title = page.album.title,
                                artistName = page.album.artists?.joinToString(", ") { it.name }?.ifEmpty { null },
                                thumbnailUrl = page.album.thumbnail?.resize(544, 544),
                                songCount = page.songs.size
                            )
                            AlbumMemoryCache.put(albumId, meta)
                        }
                    } finally {
                        inFlightAlbums.remove(albumId)
                        def.complete(Unit)
                    }
                }
            }
        }

        PerfTiming.log("[META RESOLVE] type=albums requested=${distinctIds.size} memHit=${memHitIds.size} inFlightShared=${inFlightSharedIds.size} network=${neededNetworkIds.size}")

        if (deferredsToWait.isNotEmpty()) {
            withTimeoutOrNull(timeoutMs.milliseconds) {
                deferredsToWait.awaitAll()
            }
        }

        distinctIds.mapNotNull { id ->
            AlbumMemoryCache.get(id)?.let { id to it }
        }.toMap()
    }
}
