package com.ruben.spotitube.sync

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.firestore.FirebaseFirestore
import com.ruben.spotitube.constants.AccountAvatarUriKey
import com.ruben.spotitube.constants.AccountNameKey
import com.ruben.spotitube.db.MusicDatabase
import com.ruben.spotitube.db.entities.HistoryAlbumEntity
import com.ruben.spotitube.db.entities.HistoryArtistEntity
import com.ruben.spotitube.db.entities.HistoryPlaylistEntity
import com.ruben.spotitube.db.entities.HistorySongEntity
import com.ruben.spotitube.db.entities.PlaylistEntity
import com.ruben.spotitube.db.entities.SavedAlbumEntity
import com.ruben.spotitube.db.entities.SavedArtistEntity
import com.ruben.spotitube.db.entities.SavedPlaylistEntity
import com.ruben.spotitube.db.entities.SavedSongEntity
import com.ruben.spotitube.innertube.YouTube
import com.ruben.spotitube.sync.worker.SyncWorker
import com.ruben.spotitube.utils.HistoryItemMemoryCache
import com.ruben.spotitube.utils.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor(
    private val database: MusicDatabase,
    private val authManager: ArmoryAuthManager,
    @ApplicationContext private val context: Context,
) {
    private var _db: FirebaseFirestore? = null
    private suspend fun getFirestore(): FirebaseFirestore = withContext(Dispatchers.IO) {
        _db ?: FirebaseFirestore.getInstance().also { _db = it }
    }

    fun setupSyncWorker(ctx: Context = context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncWorkRequest = PeriodicWorkRequestBuilder<SyncWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            "SyncWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            syncWorkRequest,
        )
    }

    fun schedulePeriodicSync(ctx: Context = context) {
        setupSyncWorker(ctx)
    }

    suspend fun syncLibrary() = withContext(Dispatchers.IO) {
        try {
            Timber.i("SyncManager: Background backup started (Cloud is backup only)")
            pushPayloadToCloud()
        } catch (e: Exception) {
            if (e !is CancellationException) {
                Timber.e(e, "SyncManager: Critical error in background backup")
            }
        }
    }



    suspend fun pushPayloadToCloud(): Boolean = withContext(Dispatchers.IO) {
        try {
            authManager.refreshSessionSilently()
            val firebaseAuth = authManager.getFirebaseAuth()
            val uid = authManager.currentUserUid.first() ?: firebaseAuth.currentUser?.uid
            if (uid.isNullOrBlank()) {
                Timber.w("SyncManager: Cannot push payload, UID is null or unauthenticated")
                return@withContext false
            }

            val db = getFirestore()
            val session = com.ruben.spotitube.utils.SessionManager(context)
            val name = session.getNombre()
            val avatarFile = java.io.File(context.filesDir, "avatar.jpg")
            val photoBase64 = if (avatarFile.exists()) {
                runCatching {
                    val bytes = avatarFile.readBytes()
                    android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                }.getOrDefault("")
            } else ""

            runCatching {
                db.collection("users").document("${uid}_profile")
                    .set(mapOf("name" to name, "photoBase64" to photoBase64)).await()
            }

            val rawSavedSongsCount = database.allSavedSongs().first().size
            val rawSavedArtistsCount = database.allSavedArtists().first().size
            val rawSavedAlbumsCount = database.allSavedAlbums().first().size
            val rawSavedPlaylistsCount = database.allSavedPlaylists().first().size

            if (rawSavedSongsCount == 0 && rawSavedArtistsCount == 0 &&
                rawSavedAlbumsCount == 0 && rawSavedPlaylistsCount == 0) {
                Timber.w("SyncManager: Room database is completely empty, skipping cloud push to prevent data loss")
                return@withContext true
            }

        val part1Ref = db.collection("users").document("${uid}_part1")
        val previousTotalParts = runCatching { part1Ref.get().await().getLong("totalParts")?.toInt() }.getOrNull()

        val savedSongs = database.allSavedSongs().first().map { s ->
            var duration = s.duration
            if (duration <= 0) {
                val refetched = YouTube.queue(listOf(s.id)).getOrNull()?.firstOrNull()
                if (refetched != null && (refetched.duration ?: 0) > 0) {
                    duration = refetched.duration!!
                }
            }
            val songWithArtists = database.getSongById(s.id)
            val artist = songWithArtists?.artists?.firstOrNull()
            val rawName = s.artistName ?: artist?.name ?: ""
            val cleanArtistName = if (rawName.toLongOrNull() != null || rawName.toIntOrNull() != null) (artist?.name ?: "") else rawName
            FavoriteSongDto(
                videoId = s.id,
                title = s.title,
                artistId = s.artistId ?: artist?.id ?: "",
                artistName = cleanArtistName,
                thumbnailUrl = s.thumbnailUrl,
                albumId = s.albumId,
                albumName = s.albumName,
                duration = maxOf(0L, duration.toLong()),
                savedAt = s.savedAt.toEpochSecond(ZoneOffset.UTC),
            )
        }

        val savedArtists = database.allSavedArtists().first().map { a ->
            FavoriteArtistDto(
                id = a.id,
                name = a.name,
                thumbnailUrl = a.thumbnailUrl,
                channelId = a.channelId,
                savedAt = a.savedAt.toEpochSecond(ZoneOffset.UTC),
            )
        }

        val savedAlbums = database.allSavedAlbums().first().map { al ->
            FavoriteAlbumDto(
                id = al.id,
                title = al.title,
                artistId = al.artistId ?: "",
                artistName = al.artistName ?: "",
                thumbnailUrl = al.thumbnailUrl,
                songCount = al.songCount,
                duration = al.duration,
                savedAt = al.savedAt.toEpochSecond(ZoneOffset.UTC),
            )
        }

        val savedPlaylists = database.allSavedPlaylists().first().map { p ->
            val songs = p.songs.mapIndexed { index, item ->
                PlaylistSongDto(
                    id = item.id,
                    title = item.title,
                    artistName = item.artistName ?: "Unknown",
                    thumbnailUrl = item.thumbnailUrl,
                    duration = item.duration,
                    albumId = item.albumId,
                    albumName = item.albumName,
                    explicit = item.explicit,
                    setVideoId = item.setVideoId,
                    position = index,
                )
            }
            PlaylistDto(
                id = p.id,
                name = p.name,
                browseId = p.browseId,
                isEditable = p.isEditable,
                thumbnailUrl = p.thumbnailUrl,
                createdAt = p.createdAt.toString(),
                songs = songs,
            )
        }

        val historySongs = database.getHistorySongsDirect().map { h ->
            HistorySongDto(songId = h.songId, duration = h.duration, playedAt = h.playedAt, playTimeMs = h.playTimeMs)
        }
        val historyArtists = database.allHistoryArtists().first().map { h ->
            HistoryArtistDto(artistId = h.artistId, name = h.name, thumbnailUrl = h.thumbnailUrl, playedAt = h.playedAt)
        }
        val historyAlbums = database.allHistoryAlbums().first().map { h ->
            HistoryAlbumDto(albumId = h.albumId, artistId = h.artistId, playedsongsJson = h.playedsongsJson, playedAt = h.playedAt)
        }
        val historyPlaylists = database.allHistoryPlaylists().first().map { h ->
            HistoryPlaylistDto(playlistId = h.playlistId, playedsongsJson = h.playedsongsJson, playedAt = h.playedAt)
        }

        val songsList = savedSongs.map {
            val cleanName = if (it.artistName.toLongOrNull() != null || it.artistName.toIntOrNull() != null) "" else it.artistName
            mapOf("videoId" to it.videoId, "title" to it.title, "artistId" to it.artistId, "artistName" to cleanName, "thumbnailUrl" to (it.thumbnailUrl ?: ""), "albumId" to (it.albumId ?: ""), "albumName" to (it.albumName ?: ""), "duration" to it.duration, "savedAt" to it.savedAt)
        }
        val albumsList = savedAlbums.map { mapOf("id" to it.id, "title" to it.title, "artistId" to it.artistId, "artistName" to it.artistName, "thumbnailUrl" to (it.thumbnailUrl ?: ""), "songCount" to it.songCount, "duration" to it.duration, "savedAt" to it.savedAt) }
        val artistsList = savedArtists.map { mapOf("id" to it.id, "name" to it.name, "thumbnailUrl" to (it.thumbnailUrl ?: ""), "channelId" to (it.channelId ?: ""), "savedAt" to it.savedAt) }
        val playlistsList = savedPlaylists.map { p ->
            mapOf(
                "id" to p.id,
                "name" to p.name,
                "browseId" to (p.browseId ?: ""),
                "isEditable" to p.isEditable,
                "thumbnailUrl" to (p.thumbnailUrl ?: ""),
                "createdAt" to (p.createdAt ?: ""),
                "songs" to p.songs.map { mapOf("id" to it.id, "title" to it.title, "artistName" to (it.artistName ?: ""), "thumbnailUrl" to (it.thumbnailUrl ?: ""), "duration" to it.duration, "albumId" to (it.albumId ?: ""), "albumName" to (it.albumName ?: ""), "explicit" to it.explicit, "setVideoId" to (it.setVideoId ?: ""), "position" to it.position) },
            )
        }
        val hSongsList = historySongs.map { mapOf("songId" to it.songId, "duration" to it.duration, "playedAt" to it.playedAt, "playTimeMs" to it.playTimeMs) }
        val hArtistsList = historyArtists.map { mapOf("artistId" to it.artistId, "name" to (it.name ?: ""), "thumbnailUrl" to (it.thumbnailUrl ?: ""), "playedAt" to it.playedAt) }
        val hAlbumsList = historyAlbums.map { mapOf("albumId" to it.albumId, "artistId" to (it.artistId ?: ""), "playedsongsJson" to it.playedsongsJson, "playedAt" to it.playedAt) }
        val hPlaylistsList = historyPlaylists.map { mapOf("playlistId" to it.playlistId, "playedsongsJson" to it.playedsongsJson, "playedAt" to it.playedAt) }

        fun estimateEntrySize(entry: Map<String, Any?>): Int {
            var sz = 50
            for ((k, v) in entry) {
                sz += k.length + (v?.toString()?.length ?: 0) + 10
            }
            return sz
        }

        val MAX_PART_BYTES = 600_000
        val parts = mutableListOf<MutableMap<String, Any>>()

        fun createEmptyPart(): MutableMap<String, Any> {
            return mutableMapOf(
                "savedsong" to mutableListOf<Map<String, Any?>>(),
                "savedalbum" to mutableListOf<Map<String, Any?>>(),
                "savedartist" to mutableListOf<Map<String, Any?>>(),
                "savedplaylist" to mutableListOf<Map<String, Any?>>(),
                "historialsong" to mutableListOf<Map<String, Any?>>(),
                "historialartist" to mutableListOf<Map<String, Any?>>(),
                "historialalbum" to mutableListOf<Map<String, Any?>>(),
                "historialplaylist" to mutableListOf<Map<String, Any?>>(),
            )
        }

        var currentPart = createEmptyPart()
        var currentPartBytes = 0

        fun addEntry(listKey: String, entry: Map<String, Any?>) {
            val entryBytes = estimateEntrySize(entry)
            if (currentPartBytes + entryBytes > MAX_PART_BYTES && currentPartBytes > 0) {
                parts.add(currentPart)
                currentPart = createEmptyPart()
                currentPartBytes = 0
            }
            @Suppress("UNCHECKED_CAST")
            (currentPart[listKey] as MutableList<Map<String, Any?>>).add(entry)
            currentPartBytes += entryBytes
        }

        songsList.forEach { addEntry("savedsong", it) }
        albumsList.forEach { addEntry("savedalbum", it) }
        artistsList.forEach { addEntry("savedartist", it) }
        playlistsList.forEach { addEntry("savedplaylist", it) }
        hSongsList.forEach { addEntry("historialsong", it) }
        hArtistsList.forEach { addEntry("historialartist", it) }
        hAlbumsList.forEach { addEntry("historialalbum", it) }
        hPlaylistsList.forEach { addEntry("historialplaylist", it) }

        parts.add(currentPart)

        val totalParts = parts.size
        val lastSync = System.currentTimeMillis()

        parts.forEach { part ->
            part["lastSync"] = lastSync
            part["totalParts"] = totalParts
        }

        withContext(Dispatchers.IO + NonCancellable) {
                for (i in 2..totalParts) {
                    db.collection("users").document("${uid}_part$i").set(parts[i - 1]).await()
                }
                part1Ref.set(parts[0]).await()

                if (previousTotalParts != null && previousTotalParts > totalParts) {
                    for (k in (totalParts + 1)..previousTotalParts) {
                        runCatching { db.collection("users").document("${uid}_part$k").delete().await() }
                    }
                }
            }
            Timber.i("SyncManager: Cloud push completed in $totalParts part(s)")
            true
        } catch (e: Exception) {
            Timber.w(e, "SyncManager: Cloud push failed")
            false
        }
    }



    private fun jsonToPayload(jsonStr: String): SavedAndHistoryPayloadDto {
        val root = JSONObject(jsonStr)

        val savedSongs = mutableListOf<FavoriteSongDto>()
        val savedSongsArr = root.optJSONArray("savedsong") ?: root.optJSONArray("savedSongs") ?: root.optJSONArray("favorites")
        if (savedSongsArr != null) {
            for (i in 0 until savedSongsArr.length()) {
                val obj = savedSongsArr.getJSONObject(i)
                savedSongs.add(
                    FavoriteSongDto(
                        videoId = obj.optString("videoId"),
                        title = obj.optString("title"),
                        artistId = obj.optString("artistId"),
                        artistName = obj.optString("artistName"),
                        thumbnailUrl = obj.optString("thumbnailUrl").ifEmpty { null },
                        albumId = obj.optString("albumId").ifEmpty { null },
                        albumName = obj.optString("albumName").ifEmpty { null },
                        duration = obj.optLong("duration"),
                        savedAt = obj.optLong("savedAt"),
                    ),
                )
            }
        }

        val savedArtists = mutableListOf<FavoriteArtistDto>()
        val savedArtistsArr = root.optJSONArray("savedartist") ?: root.optJSONArray("savedArtists") ?: root.optJSONArray("artists")
        if (savedArtistsArr != null) {
            for (i in 0 until savedArtistsArr.length()) {
                val obj = savedArtistsArr.getJSONObject(i)
                savedArtists.add(
                    FavoriteArtistDto(
                        id = obj.optString("id"),
                        name = obj.optString("name"),
                        thumbnailUrl = obj.optString("thumbnailUrl").ifEmpty { null },
                        channelId = obj.optString("channelId").ifEmpty { null },
                        savedAt = obj.optLong("savedAt"),
                    ),
                )
            }
        }

        val savedAlbums = mutableListOf<FavoriteAlbumDto>()
        val savedAlbumsArr = root.optJSONArray("savedalbum") ?: root.optJSONArray("savedAlbums") ?: root.optJSONArray("albums")
        if (savedAlbumsArr != null) {
            for (i in 0 until savedAlbumsArr.length()) {
                val obj = savedAlbumsArr.getJSONObject(i)
                savedAlbums.add(
                    FavoriteAlbumDto(
                        id = obj.optString("id"),
                        title = obj.optString("title"),
                        artistId = obj.optString("artistId"),
                        artistName = obj.optString("artistName"),
                        thumbnailUrl = obj.optString("thumbnailUrl").ifEmpty { null },
                        songCount = obj.optInt("songCount"),
                        duration = obj.optInt("duration"),
                        savedAt = obj.optLong("savedAt"),
                    ),
                )
            }
        }

        val savedPlaylists = mutableListOf<PlaylistDto>()
        val savedPlaylistsArr = root.optJSONArray("savedplaylist") ?: root.optJSONArray("savedPlaylists") ?: root.optJSONArray("playlists")
        if (savedPlaylistsArr != null) {
            for (i in 0 until savedPlaylistsArr.length()) {
                val obj = savedPlaylistsArr.getJSONObject(i)
                val songsList = mutableListOf<PlaylistSongDto>()
                val songsArr = obj.optJSONArray("songs")
                if (songsArr != null) {
                    for (j in 0 until songsArr.length()) {
                        val sObj = songsArr.getJSONObject(j)
                        songsList.add(
                            PlaylistSongDto(
                                id = sObj.optString("id"),
                                title = sObj.optString("title"),
                                artistName = sObj.optString("artistName").ifEmpty { null },
                                thumbnailUrl = sObj.optString("thumbnailUrl").ifEmpty { null },
                                duration = sObj.optInt("duration", -1),
                                albumId = sObj.optString("albumId").ifEmpty { null },
                                albumName = sObj.optString("albumName").ifEmpty { null },
                                explicit = sObj.optBoolean("explicit", false),
                                setVideoId = sObj.optString("setVideoId").ifEmpty { null },
                                position = sObj.optInt("position"),
                            ),
                        )
                    }
                }
                savedPlaylists.add(
                    PlaylistDto(
                        id = obj.optString("id"),
                        name = obj.optString("name"),
                        browseId = obj.optString("browseId").ifEmpty { null },
                        isEditable = obj.optBoolean("isEditable"),
                        thumbnailUrl = obj.optString("thumbnailUrl").ifEmpty { null },
                        createdAt = obj.optString("createdAt").ifEmpty { null },
                        songs = songsList,
                    ),
                )
            }
        }

        val historySongs = mutableListOf<HistorySongDto>()
        val historySongsArr = root.optJSONArray("historialsong") ?: root.optJSONArray("historySongs")
        if (historySongsArr != null) {
            for (i in 0 until historySongsArr.length()) {
                val obj = historySongsArr.getJSONObject(i)
                historySongs.add(
                    HistorySongDto(
                        songId = obj.optString("songId"),
                        duration = obj.optInt("duration"),
                        playedAt = obj.optLong("playedAt"),
                        playTimeMs = obj.optLong("playTimeMs"),
                    ),
                )
            }
        }

        val historyArtists = mutableListOf<HistoryArtistDto>()
        val historyArtistsArr = root.optJSONArray("historialartist") ?: root.optJSONArray("historyArtists")
        if (historyArtistsArr != null) {
            for (i in 0 until historyArtistsArr.length()) {
                val obj = historyArtistsArr.getJSONObject(i)
                historyArtists.add(
                    HistoryArtistDto(
                        artistId = obj.optString("artistId"),
                        name = obj.optString("name").ifEmpty { null },
                        thumbnailUrl = obj.optString("thumbnailUrl").ifEmpty { null },
                        playedAt = obj.optLong("playedAt"),
                    ),
                )
            }
        }

        val historyAlbums = mutableListOf<HistoryAlbumDto>()
        val historyAlbumsArr = root.optJSONArray("historialalbum") ?: root.optJSONArray("historyAlbums")
        if (historyAlbumsArr != null) {
            for (i in 0 until historyAlbumsArr.length()) {
                val obj = historyAlbumsArr.getJSONObject(i)
                historyAlbums.add(
                    HistoryAlbumDto(
                        albumId = obj.optString("albumId"),
                        artistId = obj.optString("artistId").ifEmpty { null },
                        playedsongsJson = obj.optString("playedsongsJson", "[]"),
                        playedAt = obj.optLong("playedAt"),
                    ),
                )
            }
        }

        val historyPlaylists = mutableListOf<HistoryPlaylistDto>()
        val historyPlaylistsArr = root.optJSONArray("historialplaylist") ?: root.optJSONArray("historyPlaylists")
        if (historyPlaylistsArr != null) {
            for (i in 0 until historyPlaylistsArr.length()) {
                val obj = historyPlaylistsArr.getJSONObject(i)
                historyPlaylists.add(
                    HistoryPlaylistDto(
                        playlistId = obj.optString("playlistId"),
                        playedsongsJson = obj.optString("playedsongsJson", "[]"),
                        playedAt = obj.optLong("playedAt"),
                    ),
                )
            }
        }

        return SavedAndHistoryPayloadDto(
            savedSongs = savedSongs,
            savedArtists = savedArtists,
            savedAlbums = savedAlbums,
            savedPlaylists = savedPlaylists,
            historySongs = historySongs,
            historyArtists = historyArtists,
            historyAlbums = historyAlbums,
            historyPlaylists = historyPlaylists,
        )
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun pushLibraryToCloud(isMigration: Boolean = false) = withContext(Dispatchers.IO) {
        pushPayloadToCloud()
    }



    suspend fun getUserLibrary(): UserLibraryDto = withContext(Dispatchers.IO) {
        authManager.refreshSessionSilently()
        val firebaseAuth = authManager.getFirebaseAuth()
        val uid = authManager.currentUserUid.first() ?: firebaseAuth.currentUser?.uid
            ?: throw IllegalStateException("Sesión expirada. Por favor, vuelve a iniciar sesión.")
        val db = getFirestore()

        val part1Doc = runCatching { db.collection("users").document("${uid}_part1").get().await() }.getOrNull()
        if (part1Doc == null || !part1Doc.exists()) throw IllegalStateException("No hay copias guardadas en la nube.")

        val chunkData = part1Doc.getString("chunkData")
        val jsonString = if (!chunkData.isNullOrBlank()) {
            val stringBuilder = StringBuilder(chunkData)
            val totalParts = part1Doc.getLong("totalParts")?.toInt() ?: 1
            for (i in 2..totalParts) {
                val partDoc = db.collection("users").document("${uid}_part$i").get().await()
                stringBuilder.append(partDoc.getString("chunkData") ?: "")
            }
            stringBuilder.toString()
        } else {
            val totalParts = part1Doc.getLong("totalParts")?.toInt() ?: 1
            val combinedMap = mutableMapOf<String, Any?>()
            val listKeys = listOf("savedsong", "savedalbum", "savedartist", "savedplaylist", "historialsong", "historialartist", "historialalbum", "historialplaylist")
            
            for (key in listKeys) {
                combinedMap[key] = mutableListOf<Any?>()
            }

            for (i in 1..totalParts) {
                val doc = if (i == 1) part1Doc else db.collection("users").document("${uid}_part$i").get().await()
                val data = doc?.data ?: continue
                for (key in listKeys) {
                    val partList = data[key] as? List<*>
                    if (!partList.isNullOrEmpty()) {
                        @Suppress("UNCHECKED_CAST")
                        (combinedMap[key] as MutableList<Any?>).addAll(partList)
                    }
                }
                if (data.containsKey("lastSync")) combinedMap["lastSync"] = data["lastSync"]
                if (data.containsKey("totalParts")) combinedMap["totalParts"] = data["totalParts"]
            }

            JSONObject(combinedMap as Map<*, *>).toString()
        }

        if (jsonString.isBlank()) throw IllegalStateException("No hay datos en la nube.")

        val payload = jsonToPayload(jsonString)
        UserLibraryDto(
            favorites = payload.savedSongs,
            albums = payload.savedAlbums,
            artists = payload.savedArtists,
            playlists = payload.savedPlaylists,
            historySongs = payload.historySongs,
            historyArtists = payload.historyArtists,
            historyAlbums = payload.historyAlbums,
            historyPlaylists = payload.historyPlaylists,
            metadata = payload.metadata,
        )
    }

    suspend fun applyLibraryToLocal(library: UserLibraryDto) = withContext(Dispatchers.IO) {
        library.favorites.forEach { sDto ->
            val savedAtSec = if (sDto.savedAt > 1_000_000_000_000L) sDto.savedAt / 1000 else sDto.savedAt
            database.insertSavedSong(
                SavedSongEntity(
                    id = sDto.videoId,
                    title = sDto.title,
                    artistId = sDto.artistId.ifEmpty { null },
                    artistName = sDto.artistName.ifEmpty { null },
                    duration = sDto.duration.toInt(),
                    thumbnailUrl = sDto.thumbnailUrl,
                    albumId = sDto.albumId,
                    albumName = sDto.albumName,
                    savedAt = LocalDateTime.ofEpochSecond(savedAtSec, 0, ZoneOffset.UTC),
                )
            )
        }
        library.artists.forEach { aDto ->
            val savedAtSec = if (aDto.savedAt > 1_000_000_000_000L) aDto.savedAt / 1000 else aDto.savedAt
            database.insertSavedArtist(
                SavedArtistEntity(
                    id = aDto.id,
                    name = aDto.name,
                    thumbnailUrl = aDto.thumbnailUrl,
                    channelId = aDto.channelId,
                    savedAt = LocalDateTime.ofEpochSecond(savedAtSec, 0, ZoneOffset.UTC),
                )
            )
        }
        library.albums.forEach { alDto ->
            val savedAtSec = if (alDto.savedAt > 1_000_000_000_000L) alDto.savedAt / 1000 else alDto.savedAt
            database.insertSavedAlbum(
                SavedAlbumEntity(
                    id = alDto.id,
                    title = alDto.title,
                    artistId = alDto.artistId.ifEmpty { null },
                    artistName = alDto.artistName.ifEmpty { null },
                    songCount = alDto.songCount,
                    duration = alDto.duration,
                    thumbnailUrl = alDto.thumbnailUrl,
                    savedAt = LocalDateTime.ofEpochSecond(savedAtSec, 0, ZoneOffset.UTC),
                )
            )
        }
        library.playlists.forEach { pDto ->
            val songItems = pDto.songs.sortedBy { it.position }.map { sDto ->
                com.ruben.spotitube.db.entities.PlaylistSongItem(
                    id = sDto.id,
                    title = sDto.title,
                    artistName = sDto.artistName?.ifEmpty { null },
                    thumbnailUrl = sDto.thumbnailUrl,
                    duration = sDto.duration,
                    albumId = sDto.albumId?.ifEmpty { null },
                    albumName = sDto.albumName?.ifEmpty { null },
                    explicit = sDto.explicit,
                    setVideoId = sDto.setVideoId?.ifEmpty { null },
                )
            }
            val parsedCreatedAt = runCatching { LocalDateTime.parse(pDto.createdAt) }.getOrNull()
            database.insertSavedPlaylist(
                SavedPlaylistEntity(
                    id = pDto.id,
                    name = pDto.name,
                    browseId = pDto.browseId,
                    isEditable = pDto.isEditable,
                    thumbnailUrl = pDto.thumbnailUrl,
                    songs = songItems,
                    createdAt = parsedCreatedAt ?: LocalDateTime.now(),
                )
            )
        }
        database.withTransaction {
            library.historySongs.forEach { hDto ->
                database.insertHistorySong(
                    com.ruben.spotitube.db.entities.HistorySongEntity(
                        playedAt = hDto.playedAt,
                        songId = hDto.songId,
                        duration = hDto.duration,
                        playTimeMs = hDto.playTimeMs,
                    ),
                )
            }
            library.historyArtists.forEach { hDto ->
                database.insertHistoryArtist(
                    com.ruben.spotitube.db.entities.HistoryArtistEntity(
                        playedAt = hDto.playedAt,
                        artistId = hDto.artistId,
                        name = hDto.name,
                        thumbnailUrl = hDto.thumbnailUrl,
                    ),
                )
            }
            library.historyAlbums.forEach { hDto ->
                database.insertHistoryAlbum(
                    com.ruben.spotitube.db.entities.HistoryAlbumEntity(
                        playedAt = hDto.playedAt,
                        albumId = hDto.albumId,
                        artistId = hDto.artistId,
                        playedsongsJson = hDto.playedsongsJson,
                    ),
                )
            }
            library.historyPlaylists.forEach { hDto ->
                database.insertHistoryPlaylist(
                    com.ruben.spotitube.db.entities.HistoryPlaylistEntity(
                        playedAt = hDto.playedAt,
                        playlistId = hDto.playlistId,
                        playedsongsJson = hDto.playedsongsJson,
                    ),
                )
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun handleLogout(ctx: Context = context): Boolean = withContext(Dispatchers.IO) {
        try {
            val pushSuccess = pushPayloadToCloud()
            if (!pushSuccess) return@withContext false

            authManager.clearAccountInfo()
            database.clearAllTables()
            HistoryItemMemoryCache.clear()
            java.io.File(context.filesDir, "avatar.jpg").delete()
            context.dataStore.edit {
                it.remove(AccountNameKey)
                it.remove(AccountAvatarUriKey)
            }
            true
        } catch (e: Exception) {
            Timber.e(e, "SyncManager: Error in handleLogout")
            false
        }
    }

    suspend fun restoreProfile() = withContext(Dispatchers.IO) {
        try {
            val firebaseAuth = authManager.getFirebaseAuth()
            val uid = authManager.currentUserUid.first() ?: firebaseAuth.currentUser?.uid
            if (uid.isNullOrBlank()) return@withContext

            val db = getFirestore()
            val doc = db.collection("users").document("${uid}_profile").get().await()
            if (doc.exists()) {
                val name = doc.getString("name") ?: ""
                val photoBase64 = doc.getString("photoBase64") ?: ""

                val session = com.ruben.spotitube.utils.SessionManager(context)
                if (name.isNotBlank()) {
                    session.saveNombre(name)
                    context.dataStore.edit { it[AccountNameKey] = name }
                }

                if (photoBase64.isNotBlank()) {
                    val bytes = android.util.Base64.decode(photoBase64, android.util.Base64.NO_WRAP)
                    val avatarFile = java.io.File(context.filesDir, "avatar.jpg")
                    avatarFile.writeBytes(bytes)
                    val uriStr = android.net.Uri.fromFile(avatarFile).toString()
                    context.dataStore.edit { it[AccountAvatarUriKey] = uriStr }
                    session.saveAvatarUri(uriStr)
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "SyncManager: Error restoring profile")
        }
    }

    suspend fun toggleSongSaved(song: SavedSongEntity) = withContext(Dispatchers.IO) {
        val existingSaved = database.getSavedSong(song.id)
        if (existingSaved != null) {
            database.deleteSavedSong(song.id)
        } else {
            database.insertSavedSong(
                SavedSongEntity(
                    id = song.id,
                    title = song.title,
                    artistId = song.artistId,
                    artistName = song.artistName,
                    duration = song.duration,
                    thumbnailUrl = song.thumbnailUrl,
                    albumId = song.albumId,
                    albumName = song.albumName,
                    explicit = song.explicit,
                    year = song.year
                )
            )
        }
    }

    suspend fun setSaved(songs: List<SavedSongEntity>, saved: Boolean) = withContext(Dispatchers.IO) {
        database.withTransaction {
            if (saved) {
                songs.forEach { database.insertSavedSong(it) }
            } else {
                songs.forEach { database.deleteSavedSong(it.id) }
            }
        }
    }

    suspend fun toggleAlbumSaved(album: SavedAlbumEntity) = withContext(Dispatchers.IO) {
        val existingSaved = database.getSavedAlbum(album.id)
        if (existingSaved != null) {
            database.deleteSavedAlbum(album.id)
        } else {
            database.insertSavedAlbum(album)
        }
    }

    suspend fun toggleAlbumSaved(
        albumId: String,
        title: String,
        artistId: String? = null,
        artistName: String? = null,
        songCount: Int = 0,
        duration: Int = 0,
        thumbnailUrl: String? = null,
        year: Int? = null,
    ) = toggleAlbumSaved(
        SavedAlbumEntity(
            id = albumId,
            title = title,
            artistId = artistId,
            artistName = artistName,
            songCount = songCount,
            duration = duration,
            thumbnailUrl = thumbnailUrl,
            year = year,
        )
    )

    suspend fun toggleArtistSaved(artist: SavedArtistEntity) = withContext(Dispatchers.IO) {
        val existingSaved = database.getSavedArtist(artist.id)
        if (existingSaved != null) {
            database.deleteSavedArtist(artist.id)
        } else {
            database.insertSavedArtist(
                SavedArtistEntity(
                    id = artist.id,
                    name = artist.name,
                    thumbnailUrl = artist.thumbnailUrl,
                    channelId = artist.channelId,
                ),
            )
        }
    }

    suspend fun togglePlaylistSaved(playlist: PlaylistEntity) = withContext(Dispatchers.IO) {
        val pId = playlist.browseId ?: playlist.id
        val existingSaved = database.getSavedPlaylist(pId) ?: database.getSavedPlaylist(playlist.id)

        if (existingSaved != null) {
            database.deleteSavedPlaylist(pId)
            database.deleteSavedPlaylist(playlist.id)
            if (existingSaved.browseId != null) {
                database.deleteSavedPlaylist(existingSaved.browseId)
            }
        } else {
            database.insertSavedPlaylist(
                SavedPlaylistEntity(
                    id = playlist.id,
                    name = playlist.name,
                    browseId = playlist.browseId,
                    isEditable = playlist.isEditable,
                    thumbnailUrl = playlist.thumbnailUrl,
                ),
            )
        }
    }

    suspend fun createPlaylist(name: String, browseId: String? = null): String {
        val id = SavedPlaylistEntity.generatePlaylistId()
        val playlist = SavedPlaylistEntity(
            id = id,
            name = name,
            browseId = browseId,
            createdAt = LocalDateTime.now(),
            isEditable = true,
        )
        withContext(Dispatchers.IO) {
            database.insertSavedPlaylist(playlist)
        }
        Timber.d("[LOCAL_SAVE_DEBUG] New playlist created in Room: $name (ID: $id)")
        return id
    }

    @Suppress("unused")
    suspend fun addSongToPlaylist(playlistId: String, songId: String) {
        withContext(Dispatchers.IO) {
            val resolvedMap = com.ruben.spotitube.utils.HistoryMetadataLoader.resolve(listOf(songId), timeoutMs = 5000L)
            val item = resolvedMap[songId]?.let { songItem ->
                com.ruben.spotitube.db.entities.PlaylistSongItem(
                    id = songItem.id,
                    title = songItem.title,
                    artistName = songItem.artists.joinToString(", ") { it.name }.ifEmpty { null },
                    thumbnailUrl = songItem.thumbnail,
                    duration = songItem.duration ?: -1,
                    albumId = songItem.album?.id,
                    albumName = songItem.album?.name,
                    explicit = songItem.explicit
                )
            }
            if (item != null) {
                database.addSongsToPlaylist(playlistId, listOf(item))
                Timber.d("[LOCAL_SAVE_DEBUG] Song $songId added to local playlist $playlistId")
            }
        }
    }

    suspend fun addAlbumSongsToPlaylist(playlistId: String, albumId: String) {
        withContext(Dispatchers.IO) {
            val albumPage = com.ruben.spotitube.innertube.YouTube.album(albumId).getOrNull() ?: return@withContext
            val items = albumPage.songs.map { song ->
                com.ruben.spotitube.db.entities.PlaylistSongItem(
                    id = song.id,
                    title = song.title,
                    artistName = song.artists.joinToString(", ") { it.name }.ifEmpty { null },
                    thumbnailUrl = song.thumbnail,
                    duration = song.duration ?: -1,
                    albumId = song.album?.id ?: albumId,
                    albumName = song.album?.name ?: albumPage.album.title,
                    explicit = song.explicit
                )
            }
            if (items.isNotEmpty()) {
                database.addSongsToPlaylist(playlistId, items)
                Timber.d("[LOCAL_SAVE_DEBUG] Added ${items.size} songs from album $albumId to playlist $playlistId")
            }
        }
    }

    suspend fun deletePlaylist(playlistId: String) = withContext(Dispatchers.IO) {
        val cleanId = playlistId.removePrefix("VL")
        database.deleteSavedPlaylist(playlistId)
        database.deleteSavedPlaylist(cleanId)
        database.deleteSavedPlaylist("VL$cleanId")
        database.deletePlaylistById(playlistId)
        database.deletePlaylistById(cleanId)
    }
}
