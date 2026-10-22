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
    val lastUpdateTime: LocalDateTime = LocalDateTime.now(),
    val savedAt: LocalDateTime = LocalDateTime.now()
) {
    val isYouTubeArtist: Boolean
        get() = id.startsWith("UC") || id.startsWith("FEmusic_library_privately_owned_artist")

    val isPrivatelyOwnedArtist: Boolean
        get() = id.startsWith("FEmusic_library_privately_owned_artist")

    fun toArtist(): Artist {
        return Artist(
            artist = this,
            songCount = 0
        )
    }

    companion object {
        fun generateArtistId() = "LA" + org.apache.commons.lang3.RandomStringUtils.insecure().next(8, true, false)
    }
}
