package com.ruben.spotitube.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.Entity

@Immutable
@Entity(
    tableName = "historialartist",
    primaryKeys = ["playedAt", "artistId"]
)
data class HistoryArtistEntity(
    val playedAt: Long = System.currentTimeMillis(),
    val artistId: String,
    val name: String? = null,
    val thumbnailUrl: String? = null
)
