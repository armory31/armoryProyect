package com.ruben.spotitube.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import org.apache.commons.lang3.RandomStringUtils
import java.time.LocalDateTime

@Immutable
@Entity(tableName = "savedplaylist")
data class SavedPlaylistEntity(
    @PrimaryKey val id: String = generatePlaylistId(),
    val name: String,
    val browseId: String? = null,
    @ColumnInfo(defaultValue = "1")
    val isEditable: Boolean = true,
    val thumbnailUrl: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val songs: List<PlaylistSongItem> = emptyList()
) {
    fun toPlaylist(
        songCount: Int = songs.size,
        songThumbnails: List<String?> = if (thumbnailUrl != null) {
            listOf(thumbnailUrl)
        } else {
            songs.take(4).map { it.thumbnailUrl }
        }
    ): Playlist {
        val playlistEntity = PlaylistEntity(
            id = id,
            name = name,
            browseId = browseId,
            createdAt = createdAt,
            isEditable = isEditable,
            thumbnailUrl = thumbnailUrl
        )
        return Playlist(
            playlist = playlistEntity,
            songCount = songCount,
            songThumbnails = songThumbnails
        )
    }

    companion object {
        fun generatePlaylistId() = "LP" + RandomStringUtils.insecure().next(8, true, false)
    }
}
