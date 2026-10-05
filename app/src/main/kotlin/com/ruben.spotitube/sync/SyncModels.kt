package com.ruben.spotitube.sync

import com.google.firebase.firestore.PropertyName

/**
 * Representa el documento atómico completo en Firestore para un usuario en users/{uid}
 */
data class UserLibraryDto(
    @get:PropertyName("favorites") @set:PropertyName("favorites") 
    var favorites: List<FavoriteSongDto> = emptyList(),
    
    @get:PropertyName("albums") @set:PropertyName("albums") 
    var albums: List<FavoriteAlbumDto> = emptyList(),
    
    @get:PropertyName("artists") @set:PropertyName("artists") 
    var artists: List<FavoriteArtistDto> = emptyList(),
    
    @get:PropertyName("playlists") @set:PropertyName("playlists") 
    var playlists: List<PlaylistDto> = emptyList(),
    
    @get:PropertyName("metadata") @set:PropertyName("metadata") 
    var metadata: SyncMetadataDto = SyncMetadataDto()
)

data class FavoriteSongDto(
    val videoId: String = "",
    val title: String = "",
    val artistId: String = "",
    val artistName: String = "",
    val thumbnailUrl: String? = null,
    val albumId: String? = null,
    val albumName: String? = null,
    val duration: Long = 0,
    val savedAt: Long = 0
)

data class FavoriteAlbumDto(
    val id: String = "",
    val title: String = "",
    val artistId: String = "",
    val artistName: String = "",
    val thumbnailUrl: String? = null,
    val songCount: Int = 0,
    val duration: Int = 0,
    val savedAt: Long = 0
)

data class FavoriteArtistDto(
    val id: String = "",
    val name: String = "",
    val thumbnailUrl: String? = null,
    val channelId: String? = null,
    val savedAt: Long = 0
)

data class PlaylistDto(
    val id: String = "",
    val name: String = "",
    val browseId: String? = null,
    val isEditable: Boolean = false,
    val thumbnailUrl: String? = null,
    val createdAt: String? = null,
    val songs: List<PlaylistSongDto> = emptyList()
)

data class PlaylistSongDto(
    val id: String = "",
    val title: String = "",
    val artistName: String = "",
    val thumbnailUrl: String? = null,
    val position: Int = 0
)

data class SyncMetadataDto(
    val lastSync: Long = 0,
    val migratedToAtomic: Boolean = false,
    val totalParts: Int = 1,
    val version: Int = 1
)

data class HistorySongDto(
    val songId: String = "",
    val duration: Int = 0,
    val playedAt: Long = 0L,
    val playTimeMs: Long = 0L
)

data class HistoryArtistDto(
    val artistId: String = "",
    val name: String? = null,
    val thumbnailUrl: String? = null,
    val playedAt: Long = 0L
)

data class HistoryAlbumDto(
    val albumId: String = "",
    val artistId: String? = null,
    val playedsongsJson: String = "[]",
    val playedAt: Long = 0L
)

data class HistoryPlaylistDto(
    val playlistId: String = "",
    val playedsongsJson: String = "[]",
    val playedAt: Long = 0L
)

data class SavedAndHistoryPayloadDto(
    val savedSongs: List<FavoriteSongDto> = emptyList(),
    val savedArtists: List<FavoriteArtistDto> = emptyList(),
    val savedAlbums: List<FavoriteAlbumDto> = emptyList(),
    val savedPlaylists: List<PlaylistDto> = emptyList(),
    val historySongs: List<HistorySongDto> = emptyList(),
    val historyArtists: List<HistoryArtistDto> = emptyList(),
    val historyAlbums: List<HistoryAlbumDto> = emptyList(),
    val historyPlaylists: List<HistoryPlaylistDto> = emptyList(),
    val metadata: SyncMetadataDto = SyncMetadataDto()
)

@Suppress("unused")
data class SyncPartDto(
    @get:PropertyName("chunkData") @set:PropertyName("chunkData")
    var chunkData: String = "",
    
    @get:PropertyName("partIndex") @set:PropertyName("partIndex")
    var partIndex: Int = 0,
    
    @get:PropertyName("totalParts") @set:PropertyName("totalParts")
    var totalParts: Int = 1
)

data class LibrarySummary(
    val songCount: Int = 0,
    val albumCount: Int = 0,
    val artistCount: Int = 0,
    val totalPlayTimeMillis: Long = 0
)
