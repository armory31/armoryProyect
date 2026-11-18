/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ruben.spotitube.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

@Serializable
data class QuickPicksPoolFile(
    val builtAt: Long,
    val entries: List<QuickPickEntry>,
    val current: List<QuickPickEntry> = emptyList(),
)

object QuickPicksPoolStore {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    private fun getTargetFile(context: Context): File = File(context.filesDir, "quick_picks_pool.json")
    private fun getTmpFile(context: Context): File = File(context.filesDir, "quick_picks_pool.json.tmp")

    private fun loadInternal(context: Context): QuickPicksPoolFile? {
        return runCatching {
            val file = getTargetFile(context)
            if (!file.exists()) return null
            val content = file.readText()
            json.decodeFromString<QuickPicksPoolFile>(content)
        }.getOrNull()
    }

    suspend fun load(context: Context): QuickPicksPoolFile? = withContext(Dispatchers.IO) {
        mutex.withLock {
            loadInternal(context)
        }
    }

    suspend fun save(context: Context, entries: List<QuickPickEntry>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (entries.isEmpty()) return@withLock
            val poolFile = QuickPicksPoolFile(
                builtAt = System.currentTimeMillis(),
                entries = entries,
                current = emptyList(),
            )
            writePoolFile(context, poolFile)
        }
    }

    suspend fun replaceEntries(
        context: Context,
        entries: List<QuickPickEntry>,
        current: List<QuickPickEntry>,
        expectedBuiltAt: Long,
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val currentFile = loadInternal(context) ?: return@withLock
            if (currentFile.builtAt != expectedBuiltAt) return@withLock
            val updated = currentFile.copy(entries = entries, current = current)
            writePoolFile(context, updated)
        }
    }

    private fun writePoolFile(context: Context, poolFile: QuickPicksPoolFile) {
        runCatching {
            val targetFile = getTargetFile(context)
            val tmpFile = getTmpFile(context)

            val content = json.encodeToString(poolFile)
            tmpFile.writeText(content)

            try {
                Files.move(
                    tmpFile.toPath(),
                    targetFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: Exception) {
                if (!tmpFile.renameTo(targetFile)) {
                    Files.move(tmpFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
    }

    fun isStale(builtAt: Long): Boolean {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val cutoffToday = now.toLocalDate().atTime(4, 0).atZone(zone)
        val lastCutoff = if (now.isBefore(cutoffToday)) {
            cutoffToday.minusDays(1)
        } else {
            cutoffToday
        }
        val builtAtZdt = Instant.ofEpochMilli(builtAt).atZone(zone)
        return builtAtZdt.isBefore(lastCutoff)
    }
}
