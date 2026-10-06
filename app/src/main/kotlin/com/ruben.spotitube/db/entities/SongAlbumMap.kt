/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ruben.spotitube.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "song_album_map",
    primaryKeys = ["songId", "albumId"],
)
data class SongAlbumMap(
    @ColumnInfo(index = true) val songId: String,
    @ColumnInfo(index = true) val albumId: String,
    val index: Int,
)
