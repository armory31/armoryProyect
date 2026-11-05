/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ruben.spotitube.db

import androidx.room.TypeConverter
import com.ruben.spotitube.db.entities.PlaylistSongItem
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class Converters {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @TypeConverter
    fun fromTimestamp(value: Long?): LocalDateTime? =
        if (value != null) {
            LocalDateTime.ofInstant(Instant.ofEpochMilli(value), ZoneOffset.UTC)
        } else {
            null
        }

    @TypeConverter
    fun dateToTimestamp(date: LocalDateTime?): Long? =
        date?.atZone(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()

    @TypeConverter
    fun playlistSongsToString(value: List<PlaylistSongItem>?): String =
        if (value != null) {
            json.encodeToString(ListSerializer(PlaylistSongItem.serializer()), value)
        } else {
            "[]"
        }

    @TypeConverter
    fun stringToPlaylistSongs(value: String?): List<PlaylistSongItem> =
        if (!value.isNullOrBlank()) {
            runCatching {
                json.decodeFromString(ListSerializer(PlaylistSongItem.serializer()), value)
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
}
