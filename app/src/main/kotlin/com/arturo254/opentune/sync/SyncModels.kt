package com.arturo254.opentune.sync

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
    val version: Int = 1
)
