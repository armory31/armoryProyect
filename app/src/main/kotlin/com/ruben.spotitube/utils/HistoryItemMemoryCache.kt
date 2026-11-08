package com.ruben.spotitube.utils

import android.os.SystemClock
import android.util.LruCache
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateMapOf
import com.ruben.spotitube.App
import com.ruben.spotitube.innertube.models.SongItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Immutable
data class ArtistMetadata(
    val id: String,
    val name: String,
    val thumbnailUrl: String? = null,
)

@Immutable
data class AlbumMetadata(
    val id: String,
    val title: String,
    val artistName: String? = null,
    val thumbnailUrl: String? = null,
    val songCount: Int = 0,
)

@Serializable
private data class SerializableArtist(
    val id: String? = null,
    val name: String,
)

@Serializable
private data class SerializableAlbum(
    val id: String,
    val name: String,
)

@Serializable
private data class SerializableSongItem(
    val id: String,
    val title: String,
    val artists: List<SerializableArtist> = emptyList(),
    val album: SerializableAlbum? = null,
    val duration: Int? = null,
    val thumbnail: String,
    val explicit: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
)

@Serializable
private data class SerializableArtistMetadata(
    val id: String,
    val name: String,
    val thumbnailUrl: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
)

@Serializable
private data class SerializableAlbumMetadata(
    val id: String,
    val title: String,
    val artistName: String? = null,
    val thumbnailUrl: String? = null,
    val songCount: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
)

private fun SongItem.toSerializable(timestamp: Long = System.currentTimeMillis()) = SerializableSongItem(
    id = id,
    title = title,
    artists = artists.map { SerializableArtist(it.id, it.name) },
    album = album?.let { SerializableAlbum(it.id, it.name) },
    duration = duration,
    thumbnail = thumbnail,
    explicit = explicit,
    timestamp = timestamp,
)

private fun SerializableSongItem.toSongItem() = SongItem(
    id = id,
    title = title,
    artists = artists.map { com.ruben.spotitube.innertube.models.Artist(name = it.name, id = it.id) },
    album = album?.let { com.ruben.spotitube.innertube.models.Album(name = it.name, id = it.id) },
    duration = duration,
    thumbnail = thumbnail,
    explicit = explicit,
)

object HistoryDiskCache {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val isLoaded = CompletableDeferred<Unit>()
    private var saveJob: Job? = null

    init {
        scope.launch {
            loadFromDisk()
        }
    }

    suspend fun awaitLoaded() {
        isLoaded.await()
    }

    private fun loadFromDisk() {
        val startMs = SystemClock.elapsedRealtime()
        var songsCount = 0
        var artistsCount = 0
        var albumsCount = 0
        try {
            val cacheDir = App.instance.cacheDir
            val now = System.currentTimeMillis()
            val maxAgeMs = 30L * 24 * 3600 * 1000

            // Songs
            val songsFile = File(cacheDir, "metadata_songs.json")
            if (songsFile.exists()) {
                runCatching {
                    val text = songsFile.readText()
                    val items = json.decodeFromString<List<SerializableSongItem>>(text)
                    val valid = items.filter { (now - it.timestamp) <= maxAgeMs }.take(500)
                    valid.forEach { dto ->
                        HistoryItemMemoryCache.putFromDisk(dto.toSongItem())
                    }
                    songsCount = valid.size
                }
            }

            // Artists
            val artistsFile = File(cacheDir, "metadata_artists.json")
            if (artistsFile.exists()) {
                runCatching {
                    val text = artistsFile.readText()
                    val items = json.decodeFromString<List<SerializableArtistMetadata>>(text)
                    val valid = items.filter { (now - it.timestamp) <= maxAgeMs }.take(300)
                    valid.forEach { dto ->
                        HistoryArtistMemoryCache.putFromDisk(ArtistMetadata(dto.id, dto.name, dto.thumbnailUrl))
                    }
                    artistsCount = valid.size
                }
            }

            // Albums
            val albumsFile = File(cacheDir, "metadata_albums.json")
            if (albumsFile.exists()) {
                runCatching {
                    val text = albumsFile.readText()
                    val items = json.decodeFromString<List<SerializableAlbumMetadata>>(text)
                    val valid = items.filter { (now - it.timestamp) <= maxAgeMs }.take(300)
                    valid.forEach { dto ->
                        HistoryAlbumMemoryCache.putFromDisk(AlbumMetadata(dto.id, dto.title, dto.artistName, dto.thumbnailUrl, dto.songCount))
                    }
                    albumsCount = valid.size
                }
            }
        } catch (_: Throwable) {
            // Ignore failure or corrupt JSON and continue empty
        } finally {
            isLoaded.complete(Unit)
            val duration = SystemClock.elapsedRealtime() - startMs
            PerfTiming.log("[META DISK] loaded songs=$songsCount artists=$artistsCount albums=$albumsCount +${duration}ms")
        }
    }

    @Synchronized
    fun notifyChanged() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(2000L)
            saveToDisk()
        }
    }

    private suspend fun saveToDisk() = withContext(Dispatchers.IO) {
        val startMs = SystemClock.elapsedRealtime()
        var songsCount = 0
        var artistsCount = 0
        var albumsCount = 0
        try {
            val cacheDir = App.instance.cacheDir
            val now = System.currentTimeMillis()

            // Songs
            val songsList = HistoryItemMemoryCache.getAllForDisk().take(500).map { it.toSerializable(now) }
            songsCount = songsList.size
            writeAtomic(File(cacheDir, "metadata_songs.json"), json.encodeToString(songsList))

            // Artists
            val artistsList = HistoryArtistMemoryCache.getAllForDisk().take(300).map {
                SerializableArtistMetadata(it.id, it.name, it.thumbnailUrl, now)
            }
            artistsCount = artistsList.size
            writeAtomic(File(cacheDir, "metadata_artists.json"), json.encodeToString(artistsList))

            // Albums
            val albumsList = HistoryAlbumMemoryCache.getAllForDisk().take(300).map {
                SerializableAlbumMetadata(it.id, it.title, it.artistName, it.thumbnailUrl, it.songCount, now)
            }
            albumsCount = albumsList.size
            writeAtomic(File(cacheDir, "metadata_albums.json"), json.encodeToString(albumsList))

            val duration = SystemClock.elapsedRealtime() - startMs
            PerfTiming.log("[META DISK] saved songs=$songsCount artists=$artistsCount albums=$albumsCount +${duration}ms")
        } catch (_: Throwable) {}
    }

    private fun writeAtomic(targetFile: File, content: String) {
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
        tempFile.writeText(content)
        if (!tempFile.renameTo(targetFile)) {
            targetFile.delete()
            tempFile.renameTo(targetFile)
        }
    }

    fun clearDisk() {
        saveJob?.cancel()
        scope.launch {
            runCatching {
                val cacheDir = App.instance.cacheDir
                File(cacheDir, "metadata_songs.json").delete()
                File(cacheDir, "metadata_artists.json").delete()
                File(cacheDir, "metadata_albums.json").delete()
            }
        }
    }
}

object HistoryItemMemoryCache {
    private const val MAX_ITEMS = 500
    private const val FAILED_RETRY_INTERVAL_MS = 3 * 60 * 1000L // 3 minutes

    private val lruMap = object : LruCache<String, SongItem>(MAX_ITEMS) {
        override fun entryRemoved(evicted: Boolean, key: String?, oldValue: SongItem?, newValue: SongItem?) {
            if (evicted && key != null) {
                cache.remove(key)
            }
        }
    }

    private val cache = mutableStateMapOf<String, SongItem>()
    private val failedIdsWithTime = mutableStateMapOf<String, Long>()

    @Synchronized
    fun get(videoId: String): SongItem? {
        val item = lruMap.get(videoId)
        if (item != null && cache[videoId] == null) {
            cache[videoId] = item
        }
        return cache[videoId]
    }

    @Synchronized
    fun isFailedRecently(videoId: String): Boolean {
        val failedTime = failedIdsWithTime[videoId] ?: return false
        if ((System.currentTimeMillis() - failedTime) > FAILED_RETRY_INTERVAL_MS) {
            failedIdsWithTime.remove(videoId)
            return false
        }
        return true
    }

    @Synchronized
    fun markFailed(videoId: String) {
        if (failedIdsWithTime.size >= 200) {
            failedIdsWithTime.keys.firstOrNull()?.let { failedIdsWithTime.remove(it) }
        }
        failedIdsWithTime[videoId] = System.currentTimeMillis()
    }

    @Synchronized
    fun put(videoId: String, item: SongItem) {
        failedIdsWithTime.remove(videoId)
        lruMap.put(videoId, item)
        cache[videoId] = item
        HistoryDiskCache.notifyChanged()
    }

    @Synchronized
    fun putAll(items: List<SongItem>) {
        if (items.isEmpty()) return
        items.forEach { item ->
            failedIdsWithTime.remove(item.id)
            lruMap.put(item.id, item)
            cache[item.id] = item
        }
        HistoryDiskCache.notifyChanged()
    }

    @Synchronized
    fun putFromDisk(item: SongItem) {
        failedIdsWithTime.remove(item.id)
        lruMap.put(item.id, item)
        cache[item.id] = item
    }

    @Synchronized
    fun getAllForDisk(): List<SongItem> {
        return cache.values.toList()
    }

    @Synchronized
    fun clear() {
        lruMap.evictAll()
        cache.clear()
        failedIdsWithTime.clear()
        HistoryDiskCache.clearDisk()
    }
}

object HistoryArtistMemoryCache {
    private const val MAX_ITEMS = 300
    private val lruMap = object : LruCache<String, ArtistMetadata>(MAX_ITEMS) {
        override fun entryRemoved(evicted: Boolean, key: String?, oldValue: ArtistMetadata?, newValue: ArtistMetadata?) {
            if (evicted && key != null) cache.remove(key)
        }
    }
    private val cache = mutableStateMapOf<String, ArtistMetadata>()

    @Synchronized
    fun get(artistId: String): ArtistMetadata? {
        val item = lruMap.get(artistId)
        if (item != null && cache[artistId] == null) cache[artistId] = item
        return cache[artistId]
    }

    @Synchronized
    fun put(artistId: String, item: ArtistMetadata) {
        lruMap.put(artistId, item)
        cache[artistId] = item
        HistoryDiskCache.notifyChanged()
    }

    @Synchronized
    fun putFromDisk(item: ArtistMetadata) {
        lruMap.put(item.id, item)
        cache[item.id] = item
    }

    @Synchronized
    fun getAllForDisk(): List<ArtistMetadata> {
        return cache.values.toList()
    }
}

object HistoryAlbumMemoryCache {
    private const val MAX_ITEMS = 300
    private val lruMap = object : LruCache<String, AlbumMetadata>(MAX_ITEMS) {
        override fun entryRemoved(evicted: Boolean, key: String?, oldValue: AlbumMetadata?, newValue: AlbumMetadata?) {
            if (evicted && key != null) cache.remove(key)
        }
    }
    private val cache = mutableStateMapOf<String, AlbumMetadata>()

    @Synchronized
    fun get(albumId: String): AlbumMetadata? {
        val item = lruMap.get(albumId)
        if (item != null && cache[albumId] == null) cache[albumId] = item
        return cache[albumId]
    }

    @Synchronized
    fun put(albumId: String, item: AlbumMetadata) {
        lruMap.put(albumId, item)
        cache[albumId] = item
        HistoryDiskCache.notifyChanged()
    }

    @Synchronized
    fun putFromDisk(item: AlbumMetadata) {
        lruMap.put(item.id, item)
        cache[item.id] = item
    }

    @Synchronized
    fun getAllForDisk(): List<AlbumMetadata> {
        return cache.values.toList()
    }
}
