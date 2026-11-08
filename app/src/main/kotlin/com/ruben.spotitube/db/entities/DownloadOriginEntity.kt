package com.ruben.spotitube.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index

@Immutable
@Entity(
    tableName = "download_origin",
    primaryKeys = ["songId", "originType", "originId"],
    indices = [
        Index("originType", "originId"),
        Index("songId")
    ]
)
data class DownloadOriginEntity(
    val songId: String,
    val originType: String,
    val originId: String,
    val position: Int = 0,
    val originTitle: String? = null,
    val originThumbnailUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

object DownloadOriginType {
    const val SONG = "SONG"
    const val ALBUM = "ALBUM"
    const val PLAYLIST = "PLAYLIST"
}

data class DownloadedOriginRow(
    val originId: String,
    val originTitle: String?,
    val originThumbnailUrl: String?,
    val songCount: Int
)
