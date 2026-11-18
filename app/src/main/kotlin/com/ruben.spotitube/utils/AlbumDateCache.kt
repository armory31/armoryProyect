/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ruben.spotitube.utils

import androidx.compose.runtime.mutableStateMapOf
import com.ruben.spotitube.App
import com.ruben.spotitube.innertube.YouTube
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

@Serializable
private data class AlbumDateEntry(
    val albumId: String,
    val date: String,
)

object AlbumDateCache {
    private val dates = mutableStateMapOf<String, String>()
    private val failed = ConcurrentHashMap.newKeySet<String>()

    private val inFlightMap = ConcurrentHashMap<String, CompletableDeferred<String?>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val diskMutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var isDiskLoaded = false

    private fun getCacheFile(): File = File(App.instance.cacheDir, "metadata_album_dates.json")

    private suspend fun awaitDiskLoaded() = withContext(Dispatchers.IO) {
        diskMutex.withLock {
            if (isDiskLoaded) return@withLock
            runCatching {
                val file = getCacheFile()
                if (file.exists()) {
                    val content = file.readText()
                    val list = json.decodeFromString<List<AlbumDateEntry>>(content)
                    withContext(Dispatchers.Main) {
                        list.forEach { entry ->
                            dates[entry.albumId] = entry.date
                        }
                    }
                }
            }
            isDiskLoaded = true
        }
    }

    private fun ensureDiskLoaded() {
        if (!isDiskLoaded) {
            scope.launch { awaitDiskLoaded() }
        }
    }

    private fun persistToDisk() {
        val snapshot = dates.toMap()
        scope.launch {
            diskMutex.withLock {
                runCatching {
                    val currentEntries = snapshot.entries.map { (id, date) ->
                        AlbumDateEntry(id, date)
                    }
                    val trimmed = if (currentEntries.size > 600) {
                        currentEntries.takeLast(600)
                    } else {
                        currentEntries
                    }

                    val targetFile = getCacheFile()
                    val tmpFile = File(App.instance.cacheDir, "metadata_album_dates.json.tmp")
                    val content = json.encodeToString(trimmed)
                    tmpFile.writeText(content)

                    try {
                        Files.move(
                            tmpFile.toPath(),
                            targetFile.toPath(),
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE,
                        )
                    } catch (_: Exception) {
                        if (!tmpFile.renameTo(targetFile)) {
                            Files.move(tmpFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                        }
                    }
                }
            }
        }
    }

    fun get(albumId: String): String? {
        ensureDiskLoaded()
        return dates[albumId]
    }

    suspend fun resolve(albumId: String, year: Int?): String? {
        return resolveShared(albumId) {
            YouTube.albumReleaseDate(albumId).getOrNull()
        }
    }

    suspend fun resolveFromSong(albumId: String, firstSongId: String, year: Int?): String? {
        return resolveShared(albumId) {
            YouTube.releaseDateOfSong(firstSongId, year)
        }
    }

    private suspend fun resolveShared(albumId: String, fetch: suspend () -> String?): String? {
        awaitDiskLoaded()
        dates[albumId]?.let { return it }
        if (failed.contains(albumId)) return null

        val mine = CompletableDeferred<String?>()
        val existing = inFlightMap.putIfAbsent(albumId, mine)
        if (existing != null) {
            return existing.await()
        }

        try {
            val result = runCatching { fetch() }.getOrElse { e ->
                if (e is CancellationException) throw e
                null
            }
            if (result != null) {
                withContext(Dispatchers.Main) {
                    dates[albumId] = result
                }
                persistToDisk()
            } else {
                failed.add(albumId)
            }
            mine.complete(result)
            return result
        } finally {
            if (!mine.isCompleted) {
                mine.complete(null)
            }
            inFlightMap.remove(albumId)
        }
    }

    fun monthIndex(text: String?): Int? {
        if (text.isNullOrBlank()) return null
        val parts = text.split("/")
        if (parts.size != 2) return null
        val month = parts[0].toIntOrNull() ?: return null
        val year = parts[1].toIntOrNull() ?: return null
        if (month !in 1..12) return null
        return year * 12 + (month - 1)
    }
}
