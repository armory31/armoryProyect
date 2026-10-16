package com.ruben.spotitube.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
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
    @ColumnInfo(defaultValue = "0")
    val explicit: Boolean = false,
    val year: Int? = null,
    val date: LocalDateTime? = null,
    val dateModified: LocalDateTime? = null,
    val totalPlayTime: Long = 0,
    val dateDownload: LocalDateTime? = LocalDateTime.now(),
    @ColumnInfo(name = "isLocal", defaultValue = "0")
    val isLocal: Boolean = false,
    @ColumnInfo(name = "isLiked", defaultValue = "1")
    val isLiked: Boolean = false,
    val bookmarkedAt: LocalDateTime? = null,
    val savedAt: LocalDateTime = LocalDateTime.now()
) {
    fun toSong(): Song {
        val artistEntity = artistName?.let {
            SavedArtistEntity(
                id = artistId ?: "",
                name = it
            )
        }
        return Song(
            song = this,
            artists = if (artistEntity != null) listOf(artistEntity) else emptyList()
        )
    }
}
