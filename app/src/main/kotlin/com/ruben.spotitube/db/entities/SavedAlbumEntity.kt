package com.ruben.spotitube.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Immutable
@Entity(tableName = "savedalbum")
data class SavedAlbumEntity(
    @PrimaryKey val id: String,
    val playlistId: String? = null,
    val title: String,
    val artistId: String? = null,
    val artistName: String? = null,
    val songCount: Int = 0,
    val duration: Int = 0,
    val thumbnailUrl: String? = null,
    val themeColor: Int? = null,
    val year: Int? = null,
    @ColumnInfo(defaultValue = "0")
    val explicit: Boolean = false,
    val lastUpdateTime: LocalDateTime = LocalDateTime.now(),
    @ColumnInfo(name = "isLocal", defaultValue = "0")
    val isLocal: Boolean = false,
    @ColumnInfo(name = "isLiked", defaultValue = "1")
    val isLiked: Boolean = true,
    val bookmarkedAt: LocalDateTime? = null,
    val savedAt: LocalDateTime = LocalDateTime.now()
) {
    fun toAlbum(): Album {
        val artistEntity = artistName?.let {
            SavedArtistEntity(
                id = artistId ?: "",
                name = it
            )
        }
        return Album(
            album = this,
            artists = if (artistEntity != null) listOf(artistEntity) else emptyList()
        )
    }
}
