@file:OptIn(ExperimentalCoroutinesApi::class)

package com.ruben.spotitube.sync

import com.ruben.spotitube.db.DatabaseDao
import com.ruben.spotitube.db.entities.Album
import com.ruben.spotitube.db.entities.Artist
import com.ruben.spotitube.db.entities.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

@Singleton
class LibraryStateHolder @Inject constructor(
    private val syncManager: SyncManager,
    authManager: ArmoryAuthManager,
    private val databaseDao: DatabaseDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Flow of the raw cloud DTO
    @Suppress("unused")
    val libraryDto: StateFlow<UserLibraryDto?> = authManager.isLoggedIn
        .flatMapLatest { loggedIn ->
            if (loggedIn) {
                flow {
                    while (true) {
                        val dto = runCatching { syncManager.getUserLibrary() }.getOrNull()
                        emit(dto)
                        kotlinx.coroutines.delay(300000.milliseconds) // Poll every 5 mins or use listeners
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
        .distinctUntilChanged()
        .onEach { Timber.d("[LOCAL_SAVE_DEBUG] LibraryStateHolder: Emitted ${it.size} liked songs") }
    
    val likedAlbumIds: Flow<Set<String>> = databaseDao.getLikedAlbumIds()
        .map { it.toSet() }
        .distinctUntilChanged()
        .onEach { Timber.d("[LOCAL_SAVE_DEBUG] LibraryStateHolder: Emitted ${it.size} liked albums") }
    
    val bookmarkedArtistIds: Flow<Set<String>> = databaseDao.getBookmarkedArtistIds()
        .map { it.toSet() }
        .distinctUntilChanged()
        .onEach { Timber.d("[LOCAL_SAVE_DEBUG] LibraryStateHolder: Emitted ${it.size} bookmarked artists") }
    
    val likedPlaylistIds: Flow<Set<String>> = databaseDao.getLikedPlaylistIds()
        .map { it.toSet() }
        .distinctUntilChanged()
        .onEach { Timber.d("[LOCAL_SAVE_DEBUG] LibraryStateHolder: Emitted ${it.size} playlists") }

    // Merged data for UI
    fun getLibrarySongs(): Flow<List<Song>> = databaseDao.allSavedSongs().map { list ->
        list.map { it.toSong() }
    }

    fun getLibraryAlbums(): Flow<List<Album>> = databaseDao.allSavedAlbums().map { list ->
        list.map { it.toAlbum() }
    }

    fun getLibraryArtists(): Flow<List<Artist>> = databaseDao.allSavedArtists().map { list ->
        list.map { it.toArtist() }
    }

    fun isSongLiked(songId: String): Flow<Boolean> = likedSongIds.map { it.contains(songId) }
    fun isAlbumLiked(albumId: String): Flow<Boolean> = likedAlbumIds.map { it.contains(albumId) }
    fun isArtistBookmarked(artistId: String): Flow<Boolean> = bookmarkedArtistIds.map { it.contains(artistId) }
    fun isPlaylistLiked(playlistId: String): Flow<Boolean> = likedPlaylistIds.map { it.contains(playlistId) }
}
