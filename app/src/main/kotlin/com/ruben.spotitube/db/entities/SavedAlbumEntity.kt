package com.ruben.spotitube.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Immutable
@Entity(tableName = "savedalbum")
data class SavedAlbumEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artistId: String? = null,
    val artistName: String? = null,
    val songCount: Int = 0,
    val duration: Int = 0,
    val thumbnailUrl: String? = null,
    val year: Int? = null,
    val savedAt: LocalDateTime = LocalDateTime.now()
) {
    fun toAlbum(): Album {
        val albumEntity = AlbumEntity(
            id = id,
            title = title,
            songCount = songCount,
            duration = duration,
            thumbnailUrl = thumbnailUrl,
            year = year,
            isLiked = true
        )
        val artistEntity = artistName?.let {
            ArtistEntity(
                id = artistId ?: "",
                name = it
            )
        }
        return Album(
            album = albumEntity,
            artists = if (artistEntity != null) listOf(artistEntity) else emptyList()
        )
    }
}

