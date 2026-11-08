package com.ruben.spotitube.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index

@Immutable
@Entity(
    tableName = "historialsong",
    primaryKeys = ["playedAt", "songId"],
    indices = [Index("songId")]
)
data class HistorySongEntity(
    val playedAt: Long = System.currentTimeMillis(),
    val songId: String,
    val duration: Int = -1,
    val playTimeMs: Long = 0L
)
