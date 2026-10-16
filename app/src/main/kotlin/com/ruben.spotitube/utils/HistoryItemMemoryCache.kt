package com.ruben.spotitube.utils

import android.util.LruCache
import androidx.compose.runtime.mutableStateMapOf
import com.ruben.spotitube.innertube.models.SongItem

object HistoryItemMemoryCache {
    private const val MAX_ITEMS = 500
    private const val FAILED_RETRY_INTERVAL_MS = 3 * 60 * 1000L // 3 minutes

    private val lru = object : LruCache<String, SongItem>(MAX_ITEMS) {
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
        return lru.get(videoId)
    }

    @Synchronized
    fun isFailedRecently(videoId: String): Boolean {
        val failedTime = failedIdsWithTime[videoId] ?: return false
        if (System.currentTimeMillis() - failedTime > FAILED_RETRY_INTERVAL_MS) {
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
        lru.put(videoId, item)
        cache[videoId] = item
    }

    @Synchronized
    fun putAll(items: List<SongItem>) {
        items.forEach { put(it.id, it) }
    }
}
