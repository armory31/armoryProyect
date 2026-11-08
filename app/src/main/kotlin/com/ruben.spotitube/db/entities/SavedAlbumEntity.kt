package com.ruben.spotitube.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ruben.spotitube.db.normalizeForSearch
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
    val savedAt: LocalDateTime = LocalDateTime.now()
) {
    @ColumnInfo(defaultValue = "''")
    var titleNormalized: String = normalizeForSearch(title)

    @ColumnInfo(defaultValue = "''")
    var artistNormalized: String = normalizeForSearch(artistName)

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
