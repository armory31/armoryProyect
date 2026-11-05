/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ruben.spotitube.utils

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class SavedArtwork(
    val songId: String,
    val thumbnail: String? = null,
    val artist: String? = null
)

object ArtworkStorage {
    private const val FILENAME = "OpenTune_saved_artworks.json"
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private fun fileFor(context: Context): File = File(context.filesDir, FILENAME)

    fun loadAll(context: Context): List<SavedArtwork> {
        try {
            val f = fileFor(context)
            if (!f.exists()) return emptyList()
            val text = f.readText()
            if (text.isBlank()) return emptyList()
            return json.decodeFromString(text)
        } catch (_: Exception) {
            return emptyList()
        }
    }

    fun findBySongId(context: Context, songId: String): SavedArtwork? = loadAll(context).firstOrNull { it.songId == songId }

    fun clear(context: Context) {
        try {
            val f = fileFor(context)
            if (f.exists()) f.writeText("[]")
        } catch (_: Exception) {
        }
    }
}
