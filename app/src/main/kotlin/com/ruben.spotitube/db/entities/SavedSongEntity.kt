package com.ruben.spotitube.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Immutable
@Entity(tableName = "savedsong")
data class SavedSongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artistId: String? = null,
    val artistName: String? = null,
    val duration: Int = -1,
    val thumbnailUrl: String? = null,
    val albumId: String? = null,
    val albumName: String? = null,
    val savedAt: LocalDateTime = LocalDateTime.now()
) {
    fun toSong(): Song {
        val songEntity = SongEntity(
            id = id,
            title = title,
            duration = duration,
            thumbnailUrl = thumbnailUrl,
            albumId = albumId,
            albumName = albumName,
            isLiked = true
        )
        val artistEntity = artistName?.let {
            ArtistEntity(
                id = artistId ?: "",
                name = it
            )
        }
        return Song(
            song = songEntity,
            artists = if (artistEntity != null) listOf(artistEntity) else emptyList()
        )
    }
}

