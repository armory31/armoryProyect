@file:OptIn(ExperimentalCoroutinesApi::class)

package com.arturo254.opentune.sync

import com.arturo254.opentune.constants.AlbumSortType
import com.arturo254.opentune.constants.ArtistSortType
import com.arturo254.opentune.db.DatabaseDao
import com.arturo254.opentune.db.entities.Album
import com.arturo254.opentune.db.entities.Artist
import com.arturo254.opentune.db.entities.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber
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

    // Reactive sets of IDs for quick lookup (Local-First: Reading directly from local DB Room)
    val likedSongIds: Flow<Set<String>> = databaseDao.getLikedSongIds()
        .map { it.toSet() }
        .onEach { Timber.d("[LOCAL_SAVE_DEBUG] LibraryStateHolder: Emitted ${it.size} liked songs") }
    
    val likedAlbumIds: Flow<Set<String>> = databaseDao.getLikedAlbumIds()
        .map { it.toSet() }
        .onEach { Timber.d("[LOCAL_SAVE_DEBUG] LibraryStateHolder: Emitted ${it.size} liked albums") }
    
    val bookmarkedArtistIds: Flow<Set<String>> = databaseDao.getBookmarkedArtistIds()
        .map { it.toSet() }
        .onEach { Timber.d("[LOCAL_SAVE_DEBUG] LibraryStateHolder: Emitted ${it.size} bookmarked artists") }
    
    val likedPlaylistIds: Flow<Set<String>> = databaseDao.getLikedPlaylistIds()
        .map { it.toSet() }
        .onEach { Timber.d("[LOCAL_SAVE_DEBUG] LibraryStateHolder: Emitted ${it.size} playlists") }

    // Merged data for UI
    fun getLibrarySongs(): Flow<List<Song>> = databaseDao.likedSongsByRowIdAsc()

    fun getLibraryAlbums(): Flow<List<Album>> = databaseDao.albumsLiked(AlbumSortType.CREATE_DATE, false)

    fun getLibraryArtists(): Flow<List<Artist>> = databaseDao.artistsBookmarked(ArtistSortType.CREATE_DATE, false)

    fun isSongLiked(songId: String): Flow<Boolean> = likedSongIds.map { it.contains(songId) }
    fun isAlbumLiked(albumId: String): Flow<Boolean> = likedAlbumIds.map { it.contains(albumId) }
    fun isArtistBookmarked(artistId: String): Flow<Boolean> = bookmarkedArtistIds.map { it.contains(artistId) }
    fun isPlaylistLiked(playlistId: String): Flow<Boolean> = likedPlaylistIds.map { it.contains(playlistId) }
}
