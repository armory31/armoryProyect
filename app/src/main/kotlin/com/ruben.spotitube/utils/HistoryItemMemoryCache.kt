package com.ruben.spotitube.utils

import android.util.LruCache
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateMapOf
import com.ruben.spotitube.innertube.models.SongItem

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
    }

    @Synchronized
    fun putAll(items: List<SongItem>) {
        items.forEach { put(it.id, it) }
    }

    @Synchronized
    fun clear() {
        lruMap.evictAll()
        cache.clear()
        failedIdsWithTime.clear()
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
    }
}
