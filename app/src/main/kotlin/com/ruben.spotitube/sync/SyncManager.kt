package com.ruben.spotitube.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import coil3.SingletonImageLoader
import com.ruben.spotitube.constants.AlbumSortType
import com.ruben.spotitube.constants.ArtistSortType
import com.ruben.spotitube.db.MusicDatabase
import com.ruben.spotitube.db.entities.AlbumArtistMap
import com.ruben.spotitube.db.entities.AlbumEntity
import com.ruben.spotitube.db.entities.ArtistEntity
import com.ruben.spotitube.db.entities.PlaylistEntity
import com.ruben.spotitube.db.entities.PlaylistSongMap
import com.ruben.spotitube.db.entities.SetVideoIdEntity
import com.ruben.spotitube.db.entities.SongArtistMap
import com.ruben.spotitube.db.entities.SongEntity
import com.ruben.spotitube.innertube.YouTube
import com.ruben.spotitube.sync.worker.SyncWorker
import com.ruben.spotitube.utils.isInternetAvailable
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDateTime
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

    suspend fun handleLogout(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            Timber.i("SyncManager: Iniciando protocolo de cierre de sesion seguro...")
            
            // Paso 1: Validar enlace con Firebase e Internet
            if (!isInternetAvailable(context)) {
                Timber.e("SyncManager: No hay internet. Abortando backup para proteger datos locales.")
                return@withContext false
            }
            
            val sessionValid = authManager.refreshSessionSilently()
            if (!sessionValid) {
                Timber.e("SyncManager: Sesion invalidada. Se requiere re-login antes del backup.")
                return@withContext false
            }

            // Paso 2: Subir copia de la base de datos local
            Timber.d("SyncManager: Paso 2 - Subiendo copia de seguridad local a Firebase...")
            pushLibraryToCloud()
            
            // Paso 3: Borrar datos de caché y Room (Solo si el backup no lanzó excepcion)
            Timber.d("SyncManager: Paso 3 - Limpiando datos locales y cerrando sesion...")
            
            // 1. Firebase Sign Out
            FirebaseAuth.getInstance().signOut()

            // 2. Clear Room Database
            database.clearAllTables()

            // 3. Clear Coil Image Cache (Memory & Disk)
            val imageLoader = SingletonImageLoader.get(context)
            imageLoader.memoryCache?.clear()
            imageLoader.diskCache?.clear()

            Timber.i("SyncManager: Logout completado con exito. Datos locales purgados.")
            true
        } catch (e: Exception) {
            Timber.e(e, "SyncManager: ERROR CRITICO en cierre de sesion. Abortando limpieza para proteger datos locales.")
            false
        }
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
        val isCurrentlyLiked = song.isLiked
        withContext(Dispatchers.IO) {
            database.upsert(song.copy(isLiked = !isCurrentlyLiked))
            Timber.d("[LOCAL_SAVE_DEBUG] Song like state updated in Room: ${song.title}")

            if (!isCurrentlyLiked) {
                database.insert(SetVideoIdEntity(videoId = song.id))
                Timber.d("[LOCAL_SAVE_DEBUG] Song added to favorites table (SetVideoId)")
            } else {
                database.deleteSetVideoId(song.id)
                Timber.d("[LOCAL_SAVE_DEBUG] Song removed from favorites table (SetVideoId)")
            }
        }
        withContext(Dispatchers.IO) {
            YouTube.likeVideo(song.id, !isCurrentlyLiked)
        }
    }

    suspend fun toggleAlbumLike(album: AlbumEntity) {
        val isCurrentlyLiked = album.isLiked
        withContext(Dispatchers.IO) {
            try {
                // Actualización local segura
                val updatedAlbum = album.copy(
                    isLiked = !isCurrentlyLiked,
                    bookmarkedAt = if (!isCurrentlyLiked) LocalDateTime.now() else null
                )
                database.insert(updatedAlbum)
                database.update(updatedAlbum)
                Timber.d("[LOCAL_SAVE_DEBUG] Album like state persisted in Room: ${album.title} (Liked: ${!isCurrentlyLiked})")
            } catch (e: Exception) {
                Timber.e(e, "SyncManager: Error toggling album like locally")
            }
        }
        if (album.playlistId != null) {
            withContext(Dispatchers.IO) {
                YouTube.likePlaylist(album.playlistId, !isCurrentlyLiked)
            }
        }
    }

    suspend fun toggleArtistLike(artist: ArtistEntity) {
        val isCurrentlyLiked = artist.isBookmarked
        withContext(Dispatchers.IO) {
            try {
                // Actualización local segura: insertar si no existe, actualizar si ya existe
                val updatedArtist = artist.copy(
                    isBookmarked = !isCurrentlyLiked,
                    bookmarkedAt = if (!isCurrentlyLiked) LocalDateTime.now() else null
                )
                database.insert(updatedArtist) 
                database.update(updatedArtist)
                Timber.d("[LOCAL_SAVE_DEBUG] Artist bookmark state persisted in Room: ${artist.name} (Subscribed: ${!isCurrentlyLiked})")
            } catch (e: Exception) {
                Timber.e(e, "SyncManager: Error toggling artist like locally")
            }
        }
        withContext(Dispatchers.IO) {
            val channelId = artist.channelId ?: YouTube.getChannelId(artist.id)
            YouTube.subscribeChannel(channelId, !isCurrentlyLiked)
        }
    }

    suspend fun togglePlaylistLike(playlist: PlaylistEntity) {
        withContext(Dispatchers.IO) {
            val pId = playlist.browseId ?: playlist.id
            val existing = database.getPlaylistById(pId)
            
            if (existing != null) {
                database.deletePlaylistById(existing.playlist.id)
                Timber.d("[LOCAL_SAVE_DEBUG] Playlist removed from local library: ${playlist.name}")
            } else {
                // Generar un ID local si el ID actual no es del formato local LP
                val localId = if (playlist.id.startsWith("LP")) playlist.id else PlaylistEntity.generatePlaylistId()
                database.insert(playlist.copy(id = localId))
                Timber.d("[LOCAL_SAVE_DEBUG] Playlist added to local library: ${playlist.name}")
            }
        }
    }

    suspend fun syncLibrary() = withContext(Dispatchers.IO) {
        try {
            Timber.i("SyncManager: Background backup started (Cloud is backup only)")
            pushLibraryToCloud(isMigration = false)
        } catch (e: Exception) {
            if (e !is CancellationException) {
                Timber.e(e, "SyncManager: Critical error in background backup")
            }
        }
    }

    suspend fun pushLibraryToCloud(isMigration: Boolean = false) = withContext(Dispatchers.IO) {
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
                artistId.let { database.insert(SongArtistMap(songId = dto.videoId, artistId = it, position = 0)) }
            } else if (!existingSong.song.isLiked) {
                database.upsert(existingSong.song.copy(isLiked = true))
            }
        }
        library.albums.forEach { aDto ->
            if (database.getAlbumById(aDto.id) == null) {
                database.insert(AlbumEntity(
                    id = aDto.id, 
                    title = aDto.title, 
                    thumbnailUrl = aDto.thumbnailUrl,
                    songCount = aDto.songCount, 
                    duration = aDto.duration
                ))
                database.artistByName(aDto.artistName)?.let {
                    database.insert(AlbumArtistMap(albumId = aDto.id, artistId = it.id, order = 0))
                }
            }
        }
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

    private fun ensureArtistExists(name: String): String {
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
            artistId.let { database.insert(SongArtistMap(songId = sDto.id, artistId = it, position = 0)) }
        }
    }

    suspend fun createPlaylist(name: String, browseId: String? = null): String {
        val id = PlaylistEntity.generatePlaylistId()
        val playlist = PlaylistEntity(
            id = id,
            name = name,
            browseId = browseId,
            createdAt = LocalDateTime.now(),
            isLocal = true,
            isEditable = true
        )
        withContext(Dispatchers.IO) {
            database.insert(playlist)
        }
        Timber.d("[LOCAL_SAVE_DEBUG] New playlist created in Room: $name (ID: $id)")
        return id
    }

    suspend fun addSongToPlaylist(playlistId: String, songId: String) {
        withContext(Dispatchers.IO) {
            database.addSongsToPlaylist(playlistId, listOf(songId))
            Timber.d("[LOCAL_SAVE_DEBUG] Song $songId added to local playlist $playlistId")
        }
    }

    suspend fun addAlbumSongsToPlaylist(playlistId: String, albumId: String) {
        withContext(Dispatchers.IO) {
            val songs = database.albumSongs(albumId).first()
            val songIds = songs.map { it.song.id }
            if (songIds.isNotEmpty()) {
                database.addSongsToPlaylist(playlistId, songIds)
                Timber.d("[LOCAL_SAVE_DEBUG] Added ${songIds.size} songs from album $albumId to playlist $playlistId")
            }
        }
    }

    fun deletePlaylist(playlistId: String) {
        database.deletePlaylistById(playlistId)
    }
}
