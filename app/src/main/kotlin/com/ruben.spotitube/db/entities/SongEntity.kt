/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ruben.spotitube.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Immutable
@Entity(
    tableName = "song",
    indices = [
        Index(
            value = ["albumId"]
        )
    ]
)
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val duration: Int = -1, // in seconds
    val thumbnailUrl: String? = null,
    val albumId: String? = null,
    val albumName: String? = null,
    @ColumnInfo(defaultValue = "0")
    val explicit: Boolean = false,
    val year: Int? = null,
    val date: LocalDateTime? = null, // ID3 tag property
    val dateModified: LocalDateTime? = null, // file property
    val totalPlayTime: Long = 0, // in milliseconds
    val dateDownload: LocalDateTime? = LocalDateTime.now(),
    @ColumnInfo(name = "isLocal", defaultValue = "0")
    val isLocal: Boolean = false,
    @ColumnInfo(name = "isLiked", defaultValue = "0")
    val isLiked: Boolean = false,
    val bookmarkedAt: LocalDateTime? = null
)
