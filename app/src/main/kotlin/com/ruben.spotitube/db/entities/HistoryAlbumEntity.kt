package com.ruben.spotitube.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.Entity

@Immutable
@Entity(
    tableName = "historialalbum",
    primaryKeys = ["playedAt", "albumId"]
)
data class HistoryAlbumEntity(
    val playedAt: Long = System.currentTimeMillis(),
    val albumId: String,
    val artistId: String? = null,
    val playedsongsJson: String = "[]"
)
