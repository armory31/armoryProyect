/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ruben.spotitube.models

import androidx.compose.runtime.Immutable
import androidx.core.net.toUri
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import com.ruben.spotitube.db.entities.SavedArtistEntity
import com.ruben.spotitube.db.entities.SavedSongEntity
import com.ruben.spotitube.db.entities.Song
import com.ruben.spotitube.innertube.models.SongItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

@Immutable
@Serializable
data class DownloadedSongMetadata(
    val id: String,
    val title: String,
    val artistName: String? = null,
    val thumbnailUrl: String? = null,
    val duration: Int = -1,
    val albumId: String? = null,
    val albumName: String? = null,
    val explicit: Boolean = false
) {
    fun toByteArray(): ByteArray = json.encodeToString(serializer(), this).toByteArray(Charsets.UTF_8)

    fun toSavedSongEntity(): SavedSongEntity = SavedSongEntity(
        id = id,
        title = title,
        artistName = artistName,
        duration = duration,
        thumbnailUrl = thumbnailUrl,
        albumId = albumId,
        albumName = albumName,
        explicit = explicit
    )

    fun toSong(): Song = Song(
        song = toSavedSongEntity(),
        artists = artistName?.let { name ->
            listOf(SavedArtistEntity(id = "", name = name))
        } ?: emptyList()
    )

    companion object {
        fun fromByteArray(bytes: ByteArray): DownloadedSongMetadata? = runCatching {
            json.decodeFromString(serializer(), String(bytes, Charsets.UTF_8))
        }.getOrNull()

        fun fromDownload(download: Download): DownloadedSongMetadata? =
            fromByteArray(download.request.data)

        fun fromSong(song: Song): DownloadedSongMetadata = DownloadedSongMetadata(
            id = song.id,
            title = song.song.title,
            artistName = song.artists.joinToString(", ") { it.name }.ifEmpty { null },
            thumbnailUrl = song.song.thumbnailUrl,
            duration = song.song.duration,
            albumId = song.song.albumId,
            albumName = song.song.albumName,
            explicit = song.song.explicit
        )

        fun fromSongItem(song: SongItem): DownloadedSongMetadata = DownloadedSongMetadata(
            id = song.id,
            title = song.title,
            artistName = song.artists.joinToString(", ") { it.name }.ifEmpty { null },
            thumbnailUrl = song.thumbnail,
            duration = song.duration ?: -1,
            albumId = song.album?.id,
            albumName = song.album?.name,
            explicit = song.explicit
        )

        fun fromMediaMetadata(media: MediaMetadata): DownloadedSongMetadata = DownloadedSongMetadata(
            id = media.id,
            title = media.title,
            artistName = media.artists.joinToString(", ") { it.name }.ifEmpty { null },
            thumbnailUrl = media.thumbnailUrl,
            duration = media.duration,
            albumId = media.album?.id,
            albumName = media.album?.title,
            explicit = media.explicit
        )

        fun buildDownloadRequest(meta: DownloadedSongMetadata): DownloadRequest =
            DownloadRequest.Builder(meta.id, meta.id.toUri())
                .setCustomCacheKey(meta.id)
                .setData(meta.toByteArray())
                .build()

        fun buildDownloadRequest(song: Song): DownloadRequest =
            buildDownloadRequest(fromSong(song))

        fun buildDownloadRequest(song: SavedSongEntity): DownloadRequest =
            buildDownloadRequest(DownloadedSongMetadata(
                id = song.id,
                title = song.title,
                artistName = song.artistName,
                thumbnailUrl = song.thumbnailUrl,
                duration = song.duration,
                albumId = song.albumId,
                albumName = song.albumName,
                explicit = song.explicit
            ))

        fun buildDownloadRequest(song: SongItem): DownloadRequest =
            buildDownloadRequest(fromSongItem(song))

        fun buildDownloadRequest(media: MediaMetadata): DownloadRequest =
            buildDownloadRequest(fromMediaMetadata(media))
    }
}
