package com.arturo254.opentune.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import coil3.SingletonImageLoader
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.arturo254.opentune.db.DatabaseDao
import com.arturo254.opentune.db.MusicDatabase
import com.arturo254.opentune.constants.AlbumSortType
import com.arturo254.opentune.constants.ArtistSortType
import com.arturo254.opentune.db.entities.AlbumEntity
import com.arturo254.opentune.db.entities.ArtistEntity
import com.arturo254.opentune.db.entities.AlbumArtistMap
import com.arturo254.opentune.db.entities.PlaylistEntity
import com.arturo254.opentune.db.entities.PlaylistSongMap
import com.arturo254.opentune.db.entities.SongAlbumMap
import com.arturo254.opentune.db.entities.SongArtistMap
import com.arturo254.opentune.db.entities.SongEntity
import com.arturo254.opentune.db.entities.SetVideoIdEntity
import com.arturo254.opentune.sync.worker.SyncWorker
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor(
    private val database: MusicDatabase,
    private val authManager: ArmoryAuthManager
) {
    private var _db: FirebaseFirestore? = null
    private suspend fun getFirestore(): FirebaseFirestore = withContext(Dispatchers.IO) {
        _db ?: FirebaseFirestore.getInstance().also { _db = it }
    }

    fun schedulePeriodicSync(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .setRequiresCharging(true)
            .setRequiresDeviceIdle(true)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(12, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setInitialDelay(10, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "armory_cloud_sync",
            ExistingPeriodicWorkPolicy.UPDATE,
            syncRequest
        )
    }

    suspend fun handleLogout(context: Context) = withContext(Dispatchers.IO) {
        // 1. Firebase Sign Out
        FirebaseAuth.getInstance().signOut()

        // 2. Clear Room Database
        database.clearAllTables()

        // 3. Clear Coil Image Cache (Memory & Disk)
        val imageLoader = SingletonImageLoader.get(context)
        imageLoader.memoryCache?.clear()
        imageLoader.diskCache?.clear()

        Timber.i("SyncManager: Logout handled and local data purged")
    }

    suspend fun getUserLibrary(): UserLibraryDto? {
        authManager.refreshSessionSilently()
        val uid = authManager.currentUserUid.first() ?: return null
        val db = getFirestore()
        return try {
            val snapshot = db.collection("users").document(uid).get().await()
            snapshot.toObject(UserLibraryDto::class.java)
        } catch (e: Exception) {
            if (e !is CancellationException) {
                Timber.e(e, "SyncManager: Error fetching library")
            }
            null
        }
    }

    suspend fun saveUserLibrary(library: UserLibraryDto) {
        authManager.refreshSessionSilently()
        val uid = authManager.currentUserUid.first() ?: return
        val db = getFirestore()
        withContext(Dispatchers.IO + NonCancellable) {
            try {
                db.collection("users").document(uid).set(library).await()
                Timber.d("SyncManager: Library saved to cloud")
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Timber.e(e, "SyncManager: Error saving library")
                }
            }
        }
    }

    suspend fun toggleLike(song: SongEntity) {
        val uid = authManager.currentUserUid.first() ?: return
        val db = getFirestore()
        val userDocRef = db.collection("users").document(uid)

        val library = getUserLibrary() ?: UserLibraryDto()
        val existingFavorite = library.favorites.find { it.videoId == song.id }
        val isCurrentlyLiked = existingFavorite != null
        
        withContext(Dispatchers.IO) {
            database.upsert(song.copy(isLiked = !isCurrentlyLiked))

            try {
                if (isCurrentlyLiked) {
                    database.deleteSetVideoId(song.id)
                    userDocRef.update("favorites", FieldValue.arrayRemove(existingFavorite)).await()
                } else {
                    database.insert(SetVideoIdEntity(videoId = song.id))
                    val artistName = database.getSongById(song.id)?.artists?.firstOrNull()?.name ?: "Unknown Artist"
                    val newFavorite = FavoriteSongDto(
                        videoId = song.id,
                        title = song.title,
                        artistName = artistName,
                        thumbnailUrl = song.thumbnailUrl,
                        albumId = song.albumId,
                        albumName = song.albumName,
                        duration = song.duration.toLong(),
                        savedAt = System.currentTimeMillis()
                    )
                    userDocRef.set(mapOf("favorites" to FieldValue.arrayUnion(newFavorite)), SetOptions.merge()).await()
                }
                Timber.d("SyncManager: Like toggled atomically")
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Timber.e(e, "SyncManager: Error toggling like atomically")
                }
            }
        }
        
        // Sync with YTM (Side effect)
        withContext(Dispatchers.IO) {
            com.arturo254.opentune.innertube.YouTube.likeVideo(song.id, !isCurrentlyLiked)
        }
    }

    suspend fun toggleAlbumLike(album: AlbumEntity) {
        val uid = authManager.currentUserUid.first() ?: return
        val db = getFirestore()
        val userDocRef = db.collection("users").document(uid)

        val library = getUserLibrary() ?: UserLibraryDto()
        val existingAlbum = library.albums.find { it.id == album.id }
        val isCurrentlyLiked = existingAlbum != null

        try {
            // Actualización local
            database.update(album.copy(isLiked = !isCurrentlyLiked))

            if (isCurrentlyLiked) {
                userDocRef.update("albums", FieldValue.arrayRemove(existingAlbum)).await()
            } else {
                val artistName = database.album(album.id).first()?.artists?.firstOrNull()?.name ?: "Unknown Artist"
                val newAlbum = FavoriteAlbumDto(
                    id = album.id,
                    title = album.title,
                    artistName = artistName,
                    thumbnailUrl = album.thumbnailUrl,
                    songCount = album.songCount,
                    duration = album.duration,
                    savedAt = System.currentTimeMillis()
                )
                userDocRef.set(mapOf("albums" to FieldValue.arrayUnion(newAlbum)), SetOptions.merge()).await()
            }
            Timber.d("SyncManager: Album like toggled atomically")
        } catch (e: Exception) {
            Timber.e(e, "SyncManager: Error toggling album like atomically")
        }

        if (album.playlistId != null) {
            withContext(Dispatchers.IO) {
                com.arturo254.opentune.innertube.YouTube.likePlaylist(album.playlistId, !isCurrentlyLiked)
            }
        }
    }

    suspend fun toggleArtistLike(artist: ArtistEntity) {
        val uid = authManager.currentUserUid.first() ?: return
        val db = getFirestore()
        val userDocRef = db.collection("users").document(uid)

        val library = getUserLibrary() ?: UserLibraryDto()
        val existingArtist = library.artists.find { it.id == artist.id }
        val isCurrentlyLiked = existingArtist != null

        try {
            // Actualización local
            database.update(artist.copy(isBookmarked = !isCurrentlyLiked))

            if (isCurrentlyLiked) {
                userDocRef.update("artists", FieldValue.arrayRemove(existingArtist)).await()
            } else {
                val newArtist = FavoriteArtistDto(
                    id = artist.id,
                    name = artist.name,
                    thumbnailUrl = artist.thumbnailUrl,
                    channelId = artist.channelId,
                    savedAt = System.currentTimeMillis()
                )
                userDocRef.set(mapOf("artists" to FieldValue.arrayUnion(newArtist)), SetOptions.merge()).await()
            }
            Timber.d("SyncManager: Artist like toggled atomically")
        } catch (e: Exception) {
            Timber.e(e, "SyncManager: Error toggling artist like atomically")
        }

        withContext(Dispatchers.IO) {
            val channelId = artist.channelId ?: com.arturo254.opentune.innertube.YouTube.getChannelId(artist.id)
            com.arturo254.opentune.innertube.YouTube.subscribeChannel(channelId, !isCurrentlyLiked)
        }
    }

    suspend fun togglePlaylistLike(playlist: PlaylistEntity) {
        val library = getUserLibrary() ?: UserLibraryDto()
        val playlistId = playlist.browseId ?: playlist.id
        val isCurrentlyLiked = library.playlists.any { it.id == playlistId }

        if (isCurrentlyLiked) {
            library.playlists = library.playlists.filter { it.id != playlistId }
        } else {
            library.playlists = library.playlists + PlaylistDto(
                id = playlistId,
                name = playlist.name,
                thumbnailUrl = playlist.thumbnailUrl,
                createdAt = playlist.createdAt?.toString()
            )
        }

        saveUserLibrary(library)
    }

    suspend fun syncLibrary() = withContext(Dispatchers.IO) {
        val uid = authManager.currentUserUid.first() ?: return@withContext
        val db = getFirestore()
        val userDocRef = db.collection("users").document(uid)

        try {
            val snapshot = userDocRef.get().await()
            val library = snapshot.toObject(UserLibraryDto::class.java)

            if (library?.metadata?.migratedToAtomic == true) {
                Timber.i("SyncManager: Atomic sync started")
                applyLibraryToLocal(library)
            } else {
                pushLibraryToCloud(isMigration = true)
            }
        } catch (e: Exception) {
            if (e !is CancellationException) {
                Timber.e(e, "SyncManager: Critical error in syncLibrary")
            }
        }
    }

    suspend fun pushLibraryToCloud(isMigration: Boolean = false) = withContext(Dispatchers.IO) {
        // Silent refresh before any upload to ensure token is valid
        authManager.refreshSessionSilently()
        
        val uid = authManager.currentUserUid.first() ?: return@withContext
        val db = getFirestore()
        
        val likedSongs = database.likedSongsByRowIdAsc().first()
        val playlists = database.playlistsByRowIdAsc().first().filter { it.playlist.browseId == null || it.playlist.isAutoSync }
        val likedAlbums = database.albumsLiked(AlbumSortType.CREATE_DATE, false).first()
        val bookmarkedArtists = database.artistsBookmarked(ArtistSortType.CREATE_DATE, false).first()

        val libraryDto = UserLibraryDto(
            favorites = likedSongs.map { s ->
                FavoriteSongDto(
                    videoId = s.song.id,
                    title = s.song.title,
                    artistName = s.artists.firstOrNull()?.name ?: "Unknown Artist",
                    thumbnailUrl = s.song.thumbnailUrl,
                    albumId = s.song.albumId,
                    albumName = s.song.albumName,
                    duration = s.song.duration.toLong(),
                    savedAt = System.currentTimeMillis()
                )
            },
            playlists = playlists.map { p ->
                val songs = database.playlistSongs(p.playlist.id).first().map { ps ->
                    PlaylistSongDto(
                        id = ps.song.song.id,
                        title = ps.song.song.title,
                        artistName = ps.song.artists.firstOrNull()?.name ?: "Unknown",
                        thumbnailUrl = ps.song.song.thumbnailUrl,
                        position = ps.map.position
                    )
                }
                PlaylistDto(
                    id = p.playlist.id,
                    name = p.playlist.name,
                    thumbnailUrl = p.playlist.thumbnailUrl ?: p.thumbnails.firstOrNull(),
                    createdAt = p.playlist.createdAt?.toString(),
                    songs = songs
                )
            },
            albums = likedAlbums.map { a ->
                FavoriteAlbumDto(
                    id = a.album.id,
                    title = a.album.title,
                    artistName = a.artists.firstOrNull()?.name ?: "Unknown Artist",
                    thumbnailUrl = a.album.thumbnailUrl,
                    songCount = a.album.songCount,
                    duration = a.album.duration,
                    savedAt = System.currentTimeMillis()
                )
            },
            artists = bookmarkedArtists.map { ar ->
                FavoriteArtistDto(
                    id = ar.artist.id,
                    name = ar.artist.name,
                    thumbnailUrl = ar.artist.thumbnailUrl,
                    channelId = ar.artist.channelId,
                    savedAt = System.currentTimeMillis()
                )
            },
            metadata = SyncMetadataDto(
                lastSync = System.currentTimeMillis(),
                migratedToAtomic = true
            )
        )

        withContext(Dispatchers.IO + NonCancellable) {
            db.collection("users").document(uid).set(libraryDto).await()
        }
        Timber.i("SyncManager: Cloud push completed")
    }

    suspend fun applyLibraryToLocal(library: UserLibraryDto) = withContext(Dispatchers.IO) {
        Timber.d("SyncManager: Applying cloud data to local")
        
        // Ensure all favorite songs exist locally
        library.favorites.forEach { dto ->
            database.insert(SetVideoIdEntity(videoId = dto.videoId))
            val existingSong = database.getSongById(dto.videoId)
            if (existingSong == null) {
                val artistId = ensureArtistExists(dto.artistName)
                database.insert(SongEntity(
                    id = dto.videoId, 
                    title = dto.title, 
                    thumbnailUrl = dto.thumbnailUrl,
                    duration = dto.duration.toInt(),
                    albumId = dto.albumId,
                    albumName = dto.albumName,
                    isLiked = true
                ))
                artistId?.let { database.insert(SongArtistMap(songId = dto.videoId, artistId = it, position = 0)) }
            } else if (!existingSong.song.isLiked) {
                database.upsert(existingSong.song.copy(isLiked = true))
            }
        }

        // Ensure all favorite albums exist locally
        library.albums.forEach { aDto ->
            if (database.getAlbumById(aDto.id) == null) {
                database.insert(AlbumEntity(
                    id = aDto.id, 
                    title = aDto.title, 
                    thumbnailUrl = aDto.thumbnailUrl,
                    songCount = aDto.songCount, 
                    duration = aDto.duration
                ))
                // Try to map artist if possible
                database.artistByName(aDto.artistName)?.let {
                    database.insert(AlbumArtistMap(albumId = aDto.id, artistId = it.id, order = 0))
                }
            }
        }

        // Ensure all bookmarked artists exist locally
        library.artists.forEach { arDto ->
            if (database.getArtistById(arDto.id) == null) {
                database.insert(ArtistEntity(
                    id = arDto.id, 
                    name = arDto.name, 
                    thumbnailUrl = arDto.thumbnailUrl,
                    channelId = arDto.channelId
                ))
            }
        }

        // Playlists
        library.playlists.forEach { pDto ->
            if (database.getPlaylistById(pDto.id) == null) {
                database.insert(PlaylistEntity(
                    id = pDto.id, 
                    name = pDto.name, 
                    thumbnailUrl = pDto.thumbnailUrl,
                    createdAt = pDto.createdAt?.let { LocalDateTime.parse(it) } ?: LocalDateTime.now()
                ))
            }
            pDto.songs.forEach { sDto ->
                if (database.checkInPlaylist(pDto.id, sDto.id) == 0) {
                    ensureSongExists(sDto)
                    database.insert(PlaylistSongMap(playlistId = pDto.id, songId = sDto.id, position = sDto.position))
                }
            }
        }
    }

    private suspend fun ensureArtistExists(name: String): String? {
        val existing = database.artistByName(name)
        if (existing != null) return existing.id
        val newId = ArtistEntity.generateArtistId()
        database.insert(ArtistEntity(id = newId, name = name))
        return newId
    }

    private suspend fun ensureSongExists(sDto: PlaylistSongDto) {
        if (database.getSongById(sDto.id) == null) {
            val artistId = ensureArtistExists(sDto.artistName)
            database.insert(SongEntity(id = sDto.id, title = sDto.title, thumbnailUrl = sDto.thumbnailUrl))
            artistId?.let { database.insert(SongArtistMap(songId = sDto.id, artistId = it, position = 0)) }
        }
    }

    suspend fun createPlaylist(name: String, browseId: String? = null): String {
        val id = PlaylistEntity.generatePlaylistId()
        val playlist = PlaylistEntity(
            id = id,
            name = name,
            browseId = browseId,
            createdAt = LocalDateTime.now()
        )
        database.insert(playlist)
        
        // Sync to cloud
        val library = getUserLibrary() ?: UserLibraryDto()
        library.playlists = library.playlists + PlaylistDto(
            id = id,
            name = name,
            createdAt = playlist.createdAt.toString()
        )
        saveUserLibrary(library)
        
        return id
    }

    suspend fun addSongToPlaylist(playlistId: String, songId: String) {
        val library = getUserLibrary() ?: return
        val playlist = library.playlists.find { it.id == playlistId } ?: return
        val song = database.getSongById(songId) ?: return
        
        val newSongDto = PlaylistSongDto(
            id = songId,
            title = song.song.title,
            artistName = song.artists.firstOrNull()?.name ?: "Unknown",
            thumbnailUrl = song.song.thumbnailUrl,
            position = playlist.songs.size
        )
        
        val updatedPlaylist = playlist.copy(songs = playlist.songs + newSongDto)
        library.playlists = library.playlists.map { if (it.id == playlistId) updatedPlaylist else it }
        saveUserLibrary(library)
        
        // Local update
        database.addSongsToPlaylist(playlistId, listOf(songId))
    }

    suspend fun removeSongFromPlaylist(playlistId: String, songId: String) {
        val library = getUserLibrary() ?: return
        val playlist = library.playlists.find { it.id == playlistId } ?: return
        
        val updatedSongs = playlist.songs.filter { it.id != songId }
            .mapIndexed { index, song -> song.copy(position = index) }
            
        val updatedPlaylist = playlist.copy(songs = updatedSongs)
        library.playlists = library.playlists.map { if (it.id == playlistId) updatedPlaylist else it }
        saveUserLibrary(library)
        
        // Local update - we'd ideally have a delete method for mapping
        // For now, full sync might be easier or adding a specific DAO method
    }

    suspend fun deletePlaylist(playlistId: String) {
        val library = getUserLibrary() ?: return
        library.playlists = library.playlists.filter { it.id != playlistId }
        saveUserLibrary(library)
        
        database.deletePlaylistById(playlistId)
    }

}
