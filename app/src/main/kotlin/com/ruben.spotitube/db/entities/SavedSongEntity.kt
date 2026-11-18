package com.ruben.spotitube.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ruben.spotitube.db.normalizeForSearch
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
    val savedAt: LocalDateTime = LocalDateTime.now()
) {
    @ColumnInfo(defaultValue = "''")
    var titleNormalized: String = normalizeForSearch(title)

    @ColumnInfo(defaultValue = "''")
    var artistNormalized: String = normalizeForSearch(artistName)

    fun toSong(): Song {
        val artistsList = artistName?.split(", ")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.mapIndexed { index, name ->
                SavedArtistEntity(
                    id = if (index == 0) artistId ?: "" else "",
                    name = name
                )
            }.orEmpty()
        return Song(
            song = this,
            artists = artistsList
        )
    }
}
