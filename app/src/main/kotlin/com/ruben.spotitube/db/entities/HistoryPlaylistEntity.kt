package com.ruben.spotitube.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.Entity

@Immutable
@Entity(
    tableName = "historialplaylist",
    primaryKeys = ["playedAt", "playlistId"]
)
data class HistoryPlaylistEntity(
    val playedAt: Long = System.currentTimeMillis(),
    val playlistId: String,
    val playedsongsJson: String = "[]"
)
