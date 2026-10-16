/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ruben.spotitube.utils

import com.ruben.spotitube.innertube.YouTube
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Collections

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
}
