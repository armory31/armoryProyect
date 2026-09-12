@file:OptIn(ExperimentalCoroutinesApi::class)

package com.arturo254.opentune.sync

import com.arturo254.opentune.constants.AlbumSortType
import com.arturo254.opentune.constants.SongSortType
import com.arturo254.opentune.db.DatabaseDao
import com.arturo254.opentune.db.entities.Album
import com.arturo254.opentune.db.entities.Artist
import com.arturo254.opentune.db.entities.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryStateHolder @Inject constructor(
    private val syncManager: SyncManager,
    private val authManager: ArmoryAuthManager,
    private val databaseDao: DatabaseDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Flow of the raw cloud DTO
    val libraryDto: StateFlow<UserLibraryDto?> = authManager.isLoggedIn
        .flatMapLatest { loggedIn ->
            if (loggedIn) {
                flow<UserLibraryDto?> {
                    while (true) {
                        emit(syncManager.getUserLibrary())
                        kotlinx.coroutines.delay(300000) // Poll every 5 mins or use listeners
                    }
                }
            } else {
                flowOf<UserLibraryDto?>(null)
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    // Reactive sets of IDs for quick lookup
    val likedSongIds: Flow<Set<String>> = libraryDto.map { it?.favorites?.map { s -> s.videoId }?.toSet() ?: emptySet() }
    val likedAlbumIds: Flow<Set<String>> = libraryDto.map { it?.albums?.map { a -> a.id }?.toSet() ?: emptySet() }
    val bookmarkedArtistIds: Flow<Set<String>> = libraryDto.map { it?.artists?.map { ar -> ar.id }?.toSet() ?: emptySet() }
    val likedPlaylistIds: Flow<Set<String>> = libraryDto.map { it?.playlists?.map { p -> p.id }?.toSet() ?: emptySet() }

    // Merged data for UI
    fun getLibrarySongs(): Flow<List<Song>> = likedSongIds.flatMapLatest { ids ->
        databaseDao.songs(SongSortType.CREATE_DATE, false).map { songs ->
            songs.filter { it.song.id in ids }
        }
    }

    fun getLibraryAlbums(): Flow<List<Album>> = likedAlbumIds.flatMapLatest { ids ->
        databaseDao.albums(AlbumSortType.CREATE_DATE, false).map { albums ->
            albums.filter { it.album.id in ids }
        }
    }

    fun getLibraryArtists(): Flow<List<Artist>> = bookmarkedArtistIds.flatMapLatest { ids ->
        databaseDao.allArtistsByNameAsc().map { artists ->
            artists.filter { it.artist.id in ids }
        }
    }

    fun isSongLiked(songId: String): Flow<Boolean> = likedSongIds.map { it.contains(songId) }
    fun isAlbumLiked(albumId: String): Flow<Boolean> = likedAlbumIds.map { it.contains(albumId) }
    fun isArtistBookmarked(artistId: String): Flow<Boolean> = bookmarkedArtistIds.map { it.contains(artistId) }
    fun isPlaylistLiked(playlistId: String): Flow<Boolean> = likedPlaylistIds.map { it.contains(playlistId) }
}
