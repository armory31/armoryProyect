/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.arturo254.opentune.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Immutable
@Entity(tableName = "album")
data class AlbumEntity(
    @PrimaryKey val id: String,
    val playlistId: String? = null,
    val title: String,
    val year: Int? = null,
    val thumbnailUrl: String? = null,
    val themeColor: Int? = null,
    val songCount: Int,
    val duration: Int,
    @ColumnInfo(defaultValue = "0")
    val explicit: Boolean = false,
    val lastUpdateTime: LocalDateTime = LocalDateTime.now(),
    @ColumnInfo(name = "isLocal", defaultValue = "0")
    val isLocal: Boolean = false,
    @ColumnInfo(name = "isLiked", defaultValue = "0")
    val isLiked: Boolean = false,
    val bookmarkedAt: LocalDateTime? = null
)
