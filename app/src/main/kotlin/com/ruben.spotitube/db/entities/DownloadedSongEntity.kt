package com.ruben.spotitube.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Immutable
@Entity(tableName = "downloaded_song")
data class DownloadedSongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artistName: String? = null,
    val thumbnailUrl: String? = null,
    val duration: Int = -1,
    val albumId: String? = null,
    val albumName: String? = null,
    @ColumnInfo(defaultValue = "0")
    val explicit: Boolean = false,
    val downloadedAt: Long = System.currentTimeMillis()
) {
    fun toSavedSongEntity(): SavedSongEntity = SavedSongEntity(
        id = id,
        title = title,
        artistId = null,
        artistName = artistName,
        duration = duration,
        thumbnailUrl = thumbnailUrl,
        albumId = albumId,
        albumName = albumName,
        explicit = explicit
    )

    fun toSong(): Song {
        val artistEntity = artistName?.let {
            SavedArtistEntity(
                id = "",
                name = it
            )
        }
        return Song(
            song = toSavedSongEntity(),
            artists = if (artistEntity != null) listOf(artistEntity) else emptyList()
        )
    }
}
