/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ruben.spotitube.models

import com.ruben.spotitube.db.entities.Album
import com.ruben.spotitube.db.entities.AlbumWithSongs
import com.ruben.spotitube.db.entities.SavedAlbumEntity
import com.ruben.spotitube.db.entities.SavedArtistEntity
import com.ruben.spotitube.db.entities.SavedSongEntity
import com.ruben.spotitube.db.entities.Song
import com.ruben.spotitube.innertube.models.AlbumItem
import com.ruben.spotitube.innertube.models.ArtistItem
import com.ruben.spotitube.innertube.models.SongItem
import com.ruben.spotitube.innertube.pages.AlbumPage
import com.ruben.spotitube.ui.utils.resize

// --- Saved Entities Mappers ---

fun SongItem.toSavedSongEntity(): SavedSongEntity = SavedSongEntity(
    id = id,
    title = title,
    artistId = artists.firstOrNull()?.id,
    artistName = artists.joinToString(", ") { artist -> artist.name }.ifEmpty { null },
    duration = duration ?: -1,
    thumbnailUrl = thumbnail.resize(544, 544),
    albumId = album?.id,
    albumName = album?.name,
    explicit = explicit,
)

fun SongItem.toSong(): Song = toSavedSongEntity().toSong()

fun AlbumPage.toAlbumWithSongs(): AlbumWithSongs {
    val savedAlbum = album.toSavedAlbumEntity()
    val songList = songs.map { it.toSong() }
    val savedArtists = album.artists?.map { artist ->
        SavedArtistEntity(
            id = artist.id ?: "",
            name = artist.name,
        )
    } ?: emptyList()
    return AlbumWithSongs(
        album = savedAlbum,
        artists = savedArtists,
        songs = songList,
    )
}

fun AlbumItem.toSavedAlbumEntity(): SavedAlbumEntity = SavedAlbumEntity(
    id = browseId.ifEmpty { id },
    playlistId = playlistId,
    title = title,
    artistId = artists?.firstOrNull()?.id,
    artistName = artists?.joinToString(", ") { artist -> artist.name }?.ifEmpty { null },
    thumbnailUrl = thumbnail.resize(544, 544),
    year = year,
    explicit = explicit,
)

fun AlbumItem.toAlbum(): Album {
    val savedAlbum = SavedAlbumEntity(
        id = browseId.ifEmpty { id },
        playlistId = playlistId,
        title = title,
        artistId = artists?.firstOrNull()?.id,
        artistName = artists?.joinToString(", ") { artist -> artist.name }?.ifEmpty { null },
        thumbnailUrl = thumbnail,
        year = year,
        explicit = explicit,
    )
    val savedArtists = artists?.map { artist ->
        SavedArtistEntity(
            id = artist.id ?: "",
            name = artist.name,
        )
    } ?: emptyList()
    return Album(
        album = savedAlbum,
        artists = savedArtists,
    )
}

fun ArtistItem.toSavedArtistEntity(): SavedArtistEntity = SavedArtistEntity(
    id = id,
    name = title,
    thumbnailUrl = thumbnail?.resize(544, 544),
    channelId = channelId,
)
