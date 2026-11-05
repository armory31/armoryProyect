/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ruben.spotitube.models

import androidx.compose.runtime.Immutable
import com.ruben.spotitube.db.entities.SavedSongEntity
import com.ruben.spotitube.db.entities.Song
import com.ruben.spotitube.innertube.models.SongItem
import com.ruben.spotitube.ui.utils.resize
import java.io.Serializable

@Immutable
data class MediaMetadata(
    val id: String,
    val title: String,
    val artists: List<Artist>,
    val duration: Int,
    val thumbnailUrl: String? = null,
    val album: Album? = null,
    val setVideoId: String? = null,
    val explicit: Boolean = false,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L

        const val UNKNOWN_DURATION = -1
    }

    data class Artist(
        val id: String?,
        val name: String,
        val thumbnailUrl: String? = null,
    ) : Serializable {
        companion object {
            private const val serialVersionUID = 1L
        }
    }

    data class Album(
        val id: String,
        val title: String,
    ) : Serializable {
        companion object {
            private const val serialVersionUID = 1L
        }
    }

    fun toSavedSongEntity() =
        SavedSongEntity(
            id = id,
            title = title,
            artistId = artists.firstOrNull()?.id,
            artistName = artists.joinToString(", ") { artist -> artist.name }.ifEmpty { artists.firstOrNull()?.name },
            duration = duration,
            thumbnailUrl = thumbnailUrl,
            albumId = album?.id,
            albumName = album?.title,
            explicit = explicit,
        )
}

private fun resolveAlbum(
    album: com.ruben.spotitube.db.entities.Album?,
    albumId: String?,
    albumName: String?
): MediaMetadata.Album? =
    album?.let {
        MediaMetadata.Album(id = it.id, title = it.title)
    } ?: albumId?.let {
        MediaMetadata.Album(id = it, title = albumName.orEmpty())
    }


fun Song.toMediaMetadata() =
    MediaMetadata(
        id = song.id,
        title = song.title,
        artists = artists.map {
            MediaMetadata.Artist(
                id = it.id,
                name = it.name,
                thumbnailUrl = it.thumbnailUrl,
            )
        },
        duration = song.duration,
        thumbnailUrl = song.thumbnailUrl,
        album =
        album?.let {
            MediaMetadata.Album(
                id = it.id,
                title = it.title,
            )
        } ?: song.albumId?.let { albumId ->
            MediaMetadata.Album(
                id = albumId,
                title = song.albumName.orEmpty(),
            )
        },
    )

fun SongItem.toMediaMetadata() =
    MediaMetadata(
        id = id,
        title = title,
        artists =
        artists.map {
            MediaMetadata.Artist(
                id = it.id,
                name = it.name,
                thumbnailUrl = null,
            )
        },
        duration = duration ?: MediaMetadata.UNKNOWN_DURATION,
        thumbnailUrl = thumbnail.resize(544, 544),
        album =
        album?.let {
            MediaMetadata.Album(
                id = it.id,
                title = it.name,
            )
        },
        explicit = explicit,
        setVideoId = setVideoId
    )
