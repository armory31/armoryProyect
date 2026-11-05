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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryStateHolder @Inject constructor(
    private val databaseDao: DatabaseDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val savedSongIds: StateFlow<Set<String>> = databaseDao.getSavedSongIds()
        .map { it.toSet() }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val savedAlbumIds: StateFlow<Set<String>> = databaseDao.getSavedAlbumIds()
        .map { it.toSet() }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val savedArtistIds: StateFlow<Set<String>> = databaseDao.getSavedArtistIds()
        .map { it.toSet() }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val savedPlaylistIds: StateFlow<Set<String>> = databaseDao.getSavedPlaylistIds()
        .map { it.toSet() }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun getLibrarySongs(): Flow<List<Song>> = databaseDao.allSavedSongs().map { list ->
        list.map { it.toSong() }
    }

    fun getLibraryAlbums(): Flow<List<Album>> = databaseDao.allSavedAlbums().map { entities ->
        entities.mapNotNull { it.toAlbum() }
    }

    fun getLibraryArtists(): Flow<List<Artist>> = databaseDao.allSavedArtists().map { list ->
        list.map { it.toArtist() }
    }

    fun isSavedSong(songId: String): Flow<Boolean> = savedSongIds.map { it.contains(songId) }
    fun isSavedAlbum(albumId: String): Flow<Boolean> = savedAlbumIds.map { it.contains(albumId) }
    fun isSavedArtist(artistId: String): Flow<Boolean> = savedArtistIds.map { it.contains(artistId) }
    fun isSavedPlaylist(playlistId: String): Flow<Boolean> = savedPlaylistIds.map { set ->
        val cleanId = playlistId.removePrefix("VL")
        set.contains(playlistId) || set.contains(cleanId) || set.contains("VL$cleanId")
    }
}
