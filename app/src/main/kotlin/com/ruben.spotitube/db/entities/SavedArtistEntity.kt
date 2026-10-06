package com.ruben.spotitube.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Immutable
@Entity(tableName = "savedartist")
data class SavedArtistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val thumbnailUrl: String? = null,
    val channelId: String? = null,
    val savedAt: LocalDateTime = LocalDateTime.now()
) {
    fun toArtist(): Artist {
        val artistEntity = ArtistEntity(
            id = id,
            name = name,
            thumbnailUrl = thumbnailUrl,
            channelId = channelId,
            isBookmarked = true
        )
        return Artist(
            artist = artistEntity,
            songCount = 0
        )
    }
}

