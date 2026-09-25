/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ruben.spotitube.utils

// import com.ruben.spotitube.innertube.utils.parseCookieString
import android.content.Context
import com.ruben.spotitube.constants.SelectedYtmPlaylistsKey
import com.ruben.spotitube.constants.YtmSyncKey
import com.ruben.spotitube.db.MusicDatabase
import com.ruben.spotitube.db.entities.ArtistEntity
import com.ruben.spotitube.db.entities.PlaylistEntity
import com.ruben.spotitube.db.entities.PlaylistSongMap
// import com.ruben.spotitube.db.entities.SongEntity
import com.ruben.spotitube.innertube.YouTube
import com.ruben.spotitube.innertube.models.AlbumItem
import com.ruben.spotitube.innertube.models.ArtistItem
import com.ruben.spotitube.innertube.models.PlaylistItem
import com.ruben.spotitube.innertube.models.SongItem
import com.ruben.spotitube.innertube.utils.completed
import com.ruben.spotitube.models.toMediaMetadata
import com.ruben.spotitube.sync.FavoriteAlbumDto
import com.ruben.spotitube.sync.FavoriteArtistDto
import com.ruben.spotitube.sync.FavoriteSongDto
import com.ruben.spotitube.sync.LibraryStateHolder
import com.ruben.spotitube.sync.SyncManager
import com.ruben.spotitube.sync.UserLibraryDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncUtils @Inject constructor(
    private val database: MusicDatabase,
    @ApplicationContext private val context: Context,
    private val syncManager: SyncManager,
    private val libraryStateHolder: LibraryStateHolder
) {
    private val syncScope = CoroutineScope(Dispatchers.IO)
    private val syncEnabled = MutableStateFlow(true)
    private val syncGeneration = AtomicLong(0L)
    
    private val syncMutex = Mutex()
    private val playlistSyncMutex = Mutex()
    private val dbWriteSemaphore = Semaphore(2)
    private val isSyncing = AtomicBoolean(false)

    init {
        syncScope.launch {
            context.dataStore.data
                .map { it[YtmSyncKey] ?: true }
                .distinctUntilChanged()
                .collect { enabled ->
                    syncEnabled.value = enabled
                    if (!enabled) {
                        syncGeneration.incrementAndGet()
                    }
                }
        }
    }
    
    suspend fun performFullSync() = withContext(Dispatchers.IO) {
        if (!isSyncing.compareAndSet(false, true)) {
            Timber.d("Sync already in progress, skipping")
            return@withContext
        }
        
        try {
            syncMutex.withLock {

                
                supervisorScope {
                    syncLikedSongs()
                    syncLibrarySongs()

                    listOf(
                        async { syncLikedAlbums() },
                        async { syncArtistsSubscriptions() },
                    ).awaitAll()
                    
                    syncSavedPlaylists()
                    syncAutoSyncPlaylists()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error during full sync")
        } finally {
            isSyncing.set(false)
        }
    }
    
    suspend fun cleanupDuplicatePlaylists() = withContext(Dispatchers.IO) {
        try {
            val allPlaylists = database.playlistsByNameAsc().first()
            val browseIdGroups = allPlaylists
                .filter { it.playlist.browseId != null }
                .groupBy { it.playlist.browseId }
            
            for ((browseId, playlists) in browseIdGroups) {
                if (playlists.size > 1) {
                    Timber.w("Found ${playlists.size} duplicate playlists for browseId: $browseId")
                    val toKeep = playlists.maxByOrNull { it.songCount }
                        ?: playlists.first()
                    
                    playlists.filter { it.id != toKeep.id }.forEach { duplicate ->
                        Timber.d("Removing duplicate playlist: ${duplicate.playlist.name} (${duplicate.id})")
                        database.clearPlaylist(duplicate.id)
                        database.delete(duplicate.playlist)
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error cleaning up duplicate playlists")
        }
    }
    

    private fun isSyncStillEnabled(gen: Long): Boolean {
        return syncEnabled.value && syncGeneration.get() == gen
    }

    suspend fun syncLikedSongs() = coroutineScope {
        /**if (!isLoggedIn()) {
            Timber.w("Skipping syncLikedSongs - user not logged in")
            return@coroutineScope
        }*/
        /*if (!isYtmSyncEnabled()) {
            Timber.w("Skipping syncLikedSongs - sync disabled")
            return@coroutineScope
        }*/
        val gen = syncGeneration.get()
        YouTube.playlist("LM").completed().onSuccess { page ->
            if (!isSyncStillEnabled(gen)) return@onSuccess
            val remoteSongs = page.songs.orEmpty()
            if (remoteSongs.isEmpty()) {
                Timber.w("syncLikedSongs: Remote playlist is empty")
                return@onSuccess
            }

            // Sync with Firebase
            val library = syncManager.getUserLibrary() ?: UserLibraryDto()
            val currentFavorites = library.favorites.toMutableList()
            var modified = false

            remoteSongs.forEach { song ->
                if (currentFavorites.none { it.videoId == song.id }) {
                    currentFavorites.add(FavoriteSongDto(
                        videoId = song.id,
                        title = song.title,
                        artistName = song.artists.firstOrNull()?.name ?: "Unknown",
                        thumbnailUrl = song.thumbnail,
                        albumId = song.album?.id,
                        albumName = song.album?.name,
                        duration = song.duration?.toLong() ?: 0L,
                        savedAt = System.currentTimeMillis()
                    ))
                    modified = true
                }
            }
            if (modified) {
                library.favorites = currentFavorites
                syncManager.saveUserLibrary(library)
            }

            remoteSongs.forEach { song ->
                launch {
                    if (!isSyncStillEnabled(gen)) return@launch
                    dbWriteSemaphore.withPermit {
                        if (!isSyncStillEnabled(gen)) return@withPermit
                        val dbSong = database.song(song.id).firstOrNull()
                        database.withTransaction {
                            if (!isSyncStillEnabled(gen)) return@withTransaction
                            if (dbSong == null) {
                                insert(song.toMediaMetadata())
                            }
                        }
                    }
                }
            }
        }.onFailure { e ->
            Timber.e(e, "syncLikedSongs: Failed to sync liked songs")
        }
    }

    suspend fun syncLibrarySongs() = coroutineScope {
        /**if (!isLoggedIn()) {
            Timber.w("Skipping syncLibrarySongs - user not logged in")
            return@coroutineScope
        }*/
        /*if (!isYtmSyncEnabled()) {
            Timber.w("Skipping syncLibrarySongs - sync disabled")
            return@coroutineScope
        }*/
        val gen = syncGeneration.get()
        YouTube.library("FEmusic_liked_videos").completed().onSuccess { page ->
            if (!isSyncStillEnabled(gen)) return@onSuccess
            val remoteSongs = page.items.filterIsInstance<SongItem>().reversed()
            if (remoteSongs.isEmpty()) {
                Timber.w("syncLibrarySongs: Remote library is empty")
                return@onSuccess
            }

            remoteSongs.forEach { song ->
                launch {
                    if (!isSyncStillEnabled(gen)) return@launch
                    dbWriteSemaphore.withPermit {
                        if (!isSyncStillEnabled(gen)) return@withPermit
                        val dbSong = database.song(song.id).firstOrNull()
                        database.withTransaction {
                            if (!isSyncStillEnabled(gen)) return@withTransaction
                            if (dbSong == null) {
                                insert(song.toMediaMetadata())
                            }
                        }
                    }
                }
            }
        }.onFailure { e ->
            Timber.e(e, "syncLibrarySongs: Failed to sync library songs")
        }
    }

    suspend fun syncLikedAlbums() = coroutineScope {
        /**if (!isLoggedIn()) {
            Timber.w("Skipping syncLikedAlbums - user not logged in")
            return@coroutineScope
        }*/
        /*if (!isYtmSyncEnabled()) {
            Timber.w("Skipping syncLikedAlbums - sync disabled")
            return@coroutineScope
        }*/
        val gen = syncGeneration.get()
        YouTube.library("FEmusic_liked_albums").completed().onSuccess { page ->
            if (!isSyncStillEnabled(gen)) return@onSuccess
            val remoteAlbums = page.items.filterIsInstance<AlbumItem>().reversed()
            if (remoteAlbums.isEmpty()) {
                Timber.w("syncLikedAlbums: No liked albums found")
                return@onSuccess
            }

            // Sync with Firebase
            val library = syncManager.getUserLibrary() ?: UserLibraryDto()
            val currentAlbums = library.albums.toMutableList()
            var modified = false
            remoteAlbums.forEach { album ->
                if (currentAlbums.none { it.id == album.id }) {
                    currentAlbums.add(FavoriteAlbumDto(
                        id = album.id,
                        title = album.title,
                        artistName = album.artists?.firstOrNull()?.name ?: "Unknown",
                        thumbnailUrl = album.thumbnail,
                        savedAt = System.currentTimeMillis()
                    ))
                    modified = true
                }
            }
            if (modified) {
                library.albums = currentAlbums
                syncManager.saveUserLibrary(library)
            }

            remoteAlbums.forEach { album ->
                launch {
                    if (!isSyncStillEnabled(gen)) return@launch
                    dbWriteSemaphore.withPermit {
                        if (!isSyncStillEnabled(gen)) return@withPermit
                        val dbAlbum = database.album(album.id).firstOrNull()
                        YouTube.album(album.browseId).onSuccess { albumPage ->
                            if (!isSyncStillEnabled(gen)) return@onSuccess
                            if (dbAlbum == null) {
                                try {
                                    database.insert(albumPage)
                                } catch (e: Exception) {
                                    Timber.w("syncLikedAlbums: Failed to insert album ${album.id}", e)
                                }
                            }
                        }.onFailure { e ->
                            Timber.w("syncLikedAlbums: Failed to fetch album ${album.id}", e)
                        }
                    }
                }
            }
        }.onFailure { e ->
            Timber.e(e, "syncLikedAlbums: Failed to sync liked albums")
        }
    }

    suspend fun syncArtistsSubscriptions() = coroutineScope {
        /*if (!isYtmSyncEnabled()) {
            Timber.w("Skipping syncArtistsSubscriptions - sync disabled")
            return@coroutineScope
        }*/
        val gen = syncGeneration.get()
        YouTube.library("FEmusic_library_corpus_artists").completed().onSuccess { page ->
            if (!isSyncStillEnabled(gen)) return@onSuccess
            val remoteArtists = page.items.filterIsInstance<ArtistItem>()
            if (remoteArtists.isEmpty()) {
                Timber.w("syncArtistsSubscriptions: No artist subscriptions found")
                return@onSuccess
            }

            // Sync with Firebase
            val library = syncManager.getUserLibrary() ?: UserLibraryDto()
            val currentArtists = library.artists.toMutableList()
            var modified = false
            remoteArtists.forEach { artist ->
                if (currentArtists.none { it.id == artist.id }) {
                    currentArtists.add(FavoriteArtistDto(
                        id = artist.id,
                        name = artist.title,
                        thumbnailUrl = artist.thumbnail,
                        channelId = artist.channelId,
                        savedAt = System.currentTimeMillis()
                    ))
                    modified = true
                }
            }
            if (modified) {
                library.artists = currentArtists
                syncManager.saveUserLibrary(library)
            }

            remoteArtists.forEach { artist ->
                launch {
                    if (!isSyncStillEnabled(gen)) return@launch
                    dbWriteSemaphore.withPermit {
                        if (!isSyncStillEnabled(gen)) return@withPermit
                        val dbArtist = database.artist(artist.id).firstOrNull()
                        database.withTransaction {
                            if (!isSyncStillEnabled(gen)) return@withTransaction
                            if (dbArtist == null) {
                                insert(
                                    ArtistEntity(
                                        id = artist.id,
                                        name = artist.title,
                                        thumbnailUrl = artist.thumbnail,
                                        channelId = artist.channelId,
                                    )
                                )
                            } else {
                                val existing = dbArtist.artist
                                if (existing.name != artist.title || existing.thumbnailUrl != artist.thumbnail || existing.channelId != artist.channelId) {
                                    update(
                                        existing.copy(
                                            name = artist.title,
                                            thumbnailUrl = artist.thumbnail,
                                            channelId = artist.channelId,
                                            lastUpdateTime = LocalDateTime.now()
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }.onFailure { e ->
            Timber.e(e, "syncArtistsSubscriptions: Failed to sync artist subscriptions")
        }
    }

    suspend fun syncSavedPlaylists() = playlistSyncMutex.withLock {
        /*if (!isYtmSyncEnabled()) {
            Timber.w("Skipping syncSavedPlaylists - sync disabled")
            return@withLock
        }*/
        val gen = syncGeneration.get()
        
        YouTube.library("FEmusic_liked_playlists").completed().onSuccess { page ->
            if (!isSyncStillEnabled(gen)) return@onSuccess
            val remotePlaylists = page.items.filterIsInstance<PlaylistItem>()
                .filterNot { it.id == "LM" || it.id == "SE" }
                .reversed()

            if (remotePlaylists.isEmpty()) {
                Timber.w("syncSavedPlaylists: No playlists found")
                return@onSuccess
            }

            val selectedCsv = context.dataStore[SelectedYtmPlaylistsKey] ?: ""
            val selectedIds = selectedCsv.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

            val localPlaylists = database.playlistsByNameAsc().first()
            if (!isSyncStillEnabled(gen)) return@onSuccess

            val now = LocalDateTime.now()
            val remoteLikedIds = remotePlaylists.map { it.id }.toSet()

            localPlaylists
                .asSequence()
                .map { it.playlist }
                .filter { it.browseId != null }
                .filter { it.browseId !in remoteLikedIds }
                .forEach { database.update(it.copy(lastUpdateTime = now)) }

            val localPlaylistIdByBrowseId = HashMap<String, String>(remotePlaylists.size)
            for (playlist in remotePlaylists) {
                if (!isSyncStillEnabled(gen)) return@onSuccess
                try {
                    val existingPlaylist = database.playlistByBrowseId(playlist.id).firstOrNull()
                    if (existingPlaylist == null) {
                        val playlistEntity = PlaylistEntity(
                            name = playlist.title,
                            browseId = playlist.id,
                            thumbnailUrl = playlist.thumbnail,
                            isEditable = playlist.isEditable,
                            remoteSongCount = playlist.songCountText?.let { Regex("""\d+""").find(it)?.value?.toIntOrNull() },
                            playEndpointParams = playlist.playEndpoint?.params,
                            shuffleEndpointParams = playlist.shuffleEndpoint?.params,
                            radioEndpointParams = playlist.radioEndpoint?.params
                        )
                        database.insert(playlistEntity)
                        localPlaylistIdByBrowseId[playlist.id] = playlistEntity.id
                        Timber.d("syncSavedPlaylists: Created new playlist ${playlist.title} (${playlist.id})")
                    } else {
                        val baseEntity = existingPlaylist.playlist
                        val likedEntity = baseEntity.copy(lastUpdateTime = now)
                        database.update(likedEntity, playlist)
                        localPlaylistIdByBrowseId[playlist.id] = likedEntity.id
                        Timber.d("syncSavedPlaylists: Updated existing playlist ${playlist.title} (${playlist.id})")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "syncSavedPlaylists: Failed to upsert playlist ${playlist.title}")
                }
            }

            val playlistsToSync =
                if (selectedIds.isNotEmpty()) remotePlaylists.filter { it.id in selectedIds } else remotePlaylists
            if (selectedIds.isNotEmpty() && playlistsToSync.isEmpty()) {
                Timber.w("syncSavedPlaylists: Selected playlists not found in remote library; skipping song sync (selected=${selectedIds.size}, remote=${remotePlaylists.size})")
            }

            for (playlist in playlistsToSync) {
                if (!isSyncStillEnabled(gen)) return@onSuccess
                try {
                    val playlistId = localPlaylistIdByBrowseId[playlist.id]
                        ?: database.playlistByBrowseId(playlist.id).firstOrNull()?.playlist?.id
                        ?: continue
                    syncPlaylist(playlist.id, playlistId)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to sync playlist ${playlist.title}")
                }
            }
        }.onFailure { e ->
            Timber.e(e, "syncSavedPlaylists: Failed to fetch playlists from YouTube")
        }
    }

    suspend fun syncAutoSyncPlaylists() = coroutineScope {
        /*if (!isYtmSyncEnabled()) {
            Timber.w("Skipping syncAutoSyncPlaylists - sync disabled")
            return@coroutineScope
        }*/
        val gen = syncGeneration.get()
        val autoSyncPlaylists = try {
            database.playlistsByNameAsc().first()
                .filter { it.playlist.isAutoSync && it.playlist.browseId != null }
        } catch (e: Exception) {
            Timber.e(e, "syncAutoSyncPlaylists: Failed to fetch auto-sync playlists")
            return@coroutineScope
        }

        Timber.d("syncAutoSyncPlaylists: Found ${autoSyncPlaylists.size} playlists to sync")

        autoSyncPlaylists.forEach { playlist ->
            launch {
                if (!isSyncStillEnabled(gen)) return@launch
                try {
                    dbWriteSemaphore.withPermit {
                        if (!isSyncStillEnabled(gen)) return@withPermit
                        val browseId = playlist.playlist.browseId ?: run {
                            Timber.w("syncAutoSyncPlaylists: browseId is null for playlist ${playlist.playlist.name}")
                            return@withPermit
                        }
                        syncPlaylist(browseId, playlist.playlist.id)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to sync playlist ${playlist.playlist.name}")
                }
            }
        }
    }

    suspend fun syncPlaylistNow(browseId: String, playlistId: String) = playlistSyncMutex.withLock {
        syncPlaylist(browseId, playlistId)
    }

    private suspend fun syncPlaylist(browseId: String, playlistId: String) = coroutineScope {
        /*if (!isYtmSyncEnabled()) {
            Timber.w("syncPlaylist: Skipping - sync disabled")
            return@coroutineScope
        }*/
        val gen = syncGeneration.get()
        if (!isSyncStillEnabled(gen)) return@coroutineScope
        Timber.d("syncPlaylist: Starting sync for browseId=$browseId, playlistId=$playlistId")
        
        YouTube.playlist(browseId).completed().onSuccess { page ->
            if (!isSyncStillEnabled(gen)) return@onSuccess
            val songs = page.songs.orEmpty().map(SongItem::toMediaMetadata)
            Timber.d("syncPlaylist: Fetched ${songs.size} songs from remote")

            if (songs.isEmpty()) {
                Timber.w("syncPlaylist: Remote playlist is empty, skipping sync")
                return@onSuccess
            }

            val remoteIds = songs.mapNotNull { it.id }
            if (remoteIds.isEmpty()) {
                Timber.w("syncPlaylist: No valid song IDs found, skipping sync")
                return@onSuccess
            }

            val localIds = try {
                database.playlistSongs(playlistId).first()
                    .sortedBy { it.map.position }
                    .map { it.song.id }
            } catch (e: Exception) {
                Timber.w("syncPlaylist: Failed to fetch local songs", e)
                emptyList()
            }

            if (remoteIds == localIds) {
                Timber.d("syncPlaylist: Local and remote are in sync, no changes needed")
                return@onSuccess
            }

            Timber.d("syncPlaylist: Updating local playlist (remote: ${remoteIds.size}, local: ${localIds.size})")

            try {
                database.withTransaction {
                    if (!isSyncStillEnabled(gen)) return@withTransaction
                    database.clearPlaylist(playlistId)
                    songs.forEachIndexed { idx, song ->
                        if (!isSyncStillEnabled(gen)) return@withTransaction
                        if (song.id == null) return@forEachIndexed
                        if (database.song(song.id).firstOrNull() == null) {
                            database.insert(song)
                        }
                        database.insert(
                            PlaylistSongMap(
                                songId = song.id,
                                playlistId = playlistId,
                                position = idx,
                                setVideoId = song.setVideoId
                            )
                        )
                    }
                }
                Timber.d("syncPlaylist: Successfully synced playlist")
            } catch (e: Exception) {
                Timber.e(e, "syncPlaylist: Error during database transaction")
            }
        }.onFailure { e ->
            Timber.e(e, "syncPlaylist: Failed to fetch playlist from YouTube")
        }
    }
}

internal fun likedSongTimestamp(baseTimestamp: LocalDateTime, index: Int): LocalDateTime {
    return baseTimestamp.minusSeconds(index.toLong())
}
