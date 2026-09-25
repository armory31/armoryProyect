/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ruben.spotitube.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlist_song_map")
data class PlaylistSongMap(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(index = true)
    val playlistId: String,

    @ColumnInfo(index = true)
    val songId: String,

    val position: Int,

    val setVideoId: String? = null,
)
