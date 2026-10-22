package com.ruben.spotitube.db.entities

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class PlaylistSongItem(
    val id: String,
    val title: String,
    val artistName: String? = null,
    val thumbnailUrl: String? = null,
    val duration: Int = -1,
    val albumId: String? = null,
    val albumName: String? = null,
    val explicit: Boolean = false,
    val setVideoId: String? = null
)
