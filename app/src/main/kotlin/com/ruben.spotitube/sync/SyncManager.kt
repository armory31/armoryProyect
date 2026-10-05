package com.ruben.spotitube.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.firestore.FirebaseFirestore
import com.ruben.spotitube.constants.AlbumSortType
import com.ruben.spotitube.constants.ArtistSortType
import com.ruben.spotitube.db.MusicDatabase
import com.ruben.spotitube.db.entities.AlbumEntity
import com.ruben.spotitube.db.entities.ArtistEntity
import com.ruben.spotitube.db.entities.HistoryAlbumEntity
import com.ruben.spotitube.db.entities.HistoryArtistEntity
import com.ruben.spotitube.db.entities.HistoryPlaylistEntity
import com.ruben.spotitube.db.entities.HistorySongEntity
import com.ruben.spotitube.db.entities.PlaylistEntity
import com.ruben.spotitube.db.entities.PlaylistSongMap
import com.ruben.spotitube.db.entities.SavedAlbumEntity
import com.ruben.spotitube.db.entities.SavedArtistEntity
import com.ruben.spotitube.db.entities.SavedPlaylistEntity
import com.ruben.spotitube.db.entities.SavedSongEntity
import com.ruben.spotitube.db.entities.SongArtistMap
import com.ruben.spotitube.db.entities.SongEntity
import com.ruben.spotitube.innertube.YouTube
import com.ruben.spotitube.models.toMediaMetadata
import com.ruben.spotitube.sync.worker.SyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
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

    companion object {
        private const val CHUNK_MAX_BYTES = 800_000
    }

    suspend fun pushPayloadToCloud() = withContext(Dispatchers.IO) {
        authManager.refreshSessionSilently()
        val firebaseAuth = authManager.getFirebaseAuth()
        val uid = authManager.currentUserUid.first() ?: firebaseAuth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            Timber.w("SyncManager: Cannot push payload, UID is null or unauthenticated")
            return@withContext
        }
        val db = getFirestore()

        val rawSavedSongs = database.allSavedSongs().first()
        val savedSongs = if (rawSavedSongs.isNotEmpty()) {
            rawSavedSongs.map { s ->
                var duration = s.duration
                if (duration <= 0) {
                    val refetched = YouTube.queue(listOf(s.id)).getOrNull()?.firstOrNull()?.toMediaMetadata()
                    if (refetched != null && refetched.duration > 0) {
                        duration = refetched.duration
                        database.insert(refetched)
                        database.insertSavedSong(s.copy(duration = duration))
                    }
                }
                val songWithArtists = database.getSongById(s.id)
                val artist = songWithArtists?.artists?.firstOrNull() ?: database.songArtistMap(s.id).firstOrNull()?.let { database.getArtistById(it.artistId) }
                FavoriteSongDto(
                    videoId = s.id,
                    title = s.title,
                    artistId = s.artistId ?: artist?.id ?: "",
                    artistName = s.artistName ?: artist?.name ?: "",
                    thumbnailUrl = s.thumbnailUrl,
                    albumId = s.albumId,
                    albumName = s.albumName,
                    duration = maxOf(0L, duration.toLong()),
                    savedAt = s.savedAt.toEpochSecond(ZoneOffset.UTC),
                )
            }
        } else {
            database.likedSongsByRowIdAsc().first().map { s ->
                var duration = s.song.duration
                if (duration <= 0) {
                    val refetched = YouTube.queue(listOf(s.song.id)).getOrNull()?.firstOrNull()?.toMediaMetadata()
                    if (refetched != null && refetched.duration > 0) {
                        duration = refetched.duration
                        database.insert(refetched)
                    }
                }
                val artist = s.artists.firstOrNull()
                FavoriteSongDto(
                    videoId = s.song.id,
                    title = s.song.title,
                    artistId = artist?.id ?: "",
                    artistName = artist?.name ?: "",
                    thumbnailUrl = s.song.thumbnailUrl,
                    albumId = s.song.albumId,
                    albumName = s.song.albumName,
                    duration = maxOf(0L, duration.toLong()),
                    savedAt = System.currentTimeMillis(),
                )
            }
        }

        val rawSavedArtists = database.allSavedArtists().first()
        val savedArtists = if (rawSavedArtists.isNotEmpty()) {
            rawSavedArtists.map { a ->
                FavoriteArtistDto(
                    id = a.id,
                    name = a.name,
                    thumbnailUrl = a.thumbnailUrl,
                    channelId = a.channelId,
                    savedAt = a.savedAt.toEpochSecond(ZoneOffset.UTC),
                )
            }
        } else {
            database.artistsBookmarked(ArtistSortType.CREATE_DATE, false).first().map { ar ->
                FavoriteArtistDto(
                    id = ar.artist.id,
                    name = ar.artist.name,
                    thumbnailUrl = ar.artist.thumbnailUrl,
                    channelId = ar.artist.channelId,
                    savedAt = System.currentTimeMillis(),
                )
            }
        }

        val rawSavedAlbums = database.allSavedAlbums().first()
        val savedAlbums = if (rawSavedAlbums.isNotEmpty()) {
            rawSavedAlbums.map { al ->
                val albumArtist = database.albumArtistMaps(al.id).firstOrNull()?.let { database.getArtistById(it.artistId) }
                FavoriteAlbumDto(
                    id = al.id,
                    title = al.title,
                    artistId = al.artistId ?: albumArtist?.id ?: "",
                    artistName = al.artistName ?: albumArtist?.name ?: "",
                    thumbnailUrl = al.thumbnailUrl,
                    songCount = al.songCount,
                    duration = al.duration,
                    savedAt = al.savedAt.toEpochSecond(ZoneOffset.UTC),
                )
            }
        } else {
            database.albumsLiked(AlbumSortType.CREATE_DATE, false).first().map { a ->
                val artist = a.artists.firstOrNull()
                FavoriteAlbumDto(
                    id = a.album.id,
                    title = a.album.title,
                    artistId = artist?.id ?: "",
                    artistName = artist?.name ?: "",
                    thumbnailUrl = a.album.thumbnailUrl,
                    songCount = a.album.songCount,
                    duration = a.album.duration,
                    savedAt = System.currentTimeMillis(),
                )
            }
        }

        val rawSavedPlaylists = database.allSavedPlaylists().first()
        val savedPlaylists = if (rawSavedPlaylists.isNotEmpty()) {
            rawSavedPlaylists.map { p ->
                val songs = database.playlistSongs(p.id).first().map { ps ->
                    PlaylistSongDto(
                        id = ps.song.song.id,
                        title = ps.song.song.title,
                        artistName = ps.song.artists.firstOrNull()?.name ?: "Unknown",
                        thumbnailUrl = ps.song.song.thumbnailUrl,
                        position = ps.map.position,
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
        } else {
            database.playlistsByRowIdAsc().first().map { p ->
                val songs = database.playlistSongs(p.playlist.id).first().map { ps ->
                    PlaylistSongDto(
                        id = ps.song.song.id,
                        title = ps.song.song.title,
                        artistName = ps.song.artists.firstOrNull()?.name ?: "Unknown",
                        thumbnailUrl = ps.song.song.thumbnailUrl,
                        position = ps.map.position,
                    )
                }
                PlaylistDto(
                    id = p.playlist.id,
                    name = p.playlist.name,
                    browseId = p.playlist.browseId,
                    isEditable = p.playlist.isEditable,
                    thumbnailUrl = p.playlist.thumbnailUrl ?: p.thumbnails.firstOrNull(),
                    createdAt = p.playlist.createdAt?.toString(),
                    songs = songs,
                )
            }
        }

        val historySongs = database.allHistorySongs().first().map { h ->
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

        val payload = SavedAndHistoryPayloadDto(
            savedSongs = savedSongs,
            savedArtists = savedArtists,
            savedAlbums = savedAlbums,
            savedPlaylists = savedPlaylists,
            historySongs = historySongs,
            historyArtists = historyArtists,
            historyAlbums = historyAlbums,
            historyPlaylists = historyPlaylists,
            metadata = SyncMetadataDto(lastSync = System.currentTimeMillis()),
        )

        val jsonString = payloadToJson(payload)
        val bytes = jsonString.toByteArray(Charsets.UTF_8)
        val totalParts = kotlin.math.ceil(bytes.size.toDouble() / CHUNK_MAX_BYTES).toInt().coerceAtLeast(1)

        val firestoreMap = mapOf(
            "savedsong" to savedSongs.map { mapOf("videoId" to it.videoId, "title" to it.title, "artistId" to it.artistId, "artistName" to it.artistName, "thumbnailUrl" to (it.thumbnailUrl ?: ""), "albumId" to (it.albumId ?: ""), "albumName" to (it.albumName ?: ""), "duration" to it.duration, "savedAt" to it.savedAt) },
            "savedalbum" to savedAlbums.map { mapOf("id" to it.id, "title" to it.title, "artistId" to it.artistId, "artistName" to it.artistName, "thumbnailUrl" to (it.thumbnailUrl ?: ""), "songCount" to it.songCount, "duration" to it.duration, "savedAt" to it.savedAt) },
            "savedartist" to savedArtists.map { mapOf("id" to it.id, "name" to it.name, "thumbnailUrl" to (it.thumbnailUrl ?: ""), "channelId" to (it.channelId ?: ""), "savedAt" to it.savedAt) },
            "savedplaylist" to savedPlaylists.map { p ->
                mapOf(
                    "id" to p.id,
                    "name" to p.name,
                    "browseId" to (p.browseId ?: ""),
                    "isEditable" to p.isEditable,
                    "thumbnailUrl" to (p.thumbnailUrl ?: ""),
                    "createdAt" to (p.createdAt ?: ""),
                    "songs" to p.songs.map { mapOf("id" to it.id, "title" to it.title, "artistName" to it.artistName, "thumbnailUrl" to (it.thumbnailUrl ?: ""), "position" to it.position) },
                )
            },
            "historialsong" to historySongs.map { mapOf("songId" to it.songId, "duration" to it.duration, "playedAt" to it.playedAt, "playTimeMs" to it.playTimeMs) },
            "historialartist" to historyArtists.map { mapOf("artistId" to it.artistId, "name" to (it.name ?: ""), "thumbnailUrl" to (it.thumbnailUrl ?: ""), "playedAt" to it.playedAt) },
            "historialalbum" to historyAlbums.map { mapOf("albumId" to it.albumId, "artistId" to (it.artistId ?: ""), "playedsongsJson" to it.playedsongsJson, "playedAt" to it.playedAt) },
            "historialplaylist" to historyPlaylists.map { mapOf("playlistId" to it.playlistId, "playedsongsJson" to it.playedsongsJson, "playedAt" to it.playedAt) },
            "lastSync" to System.currentTimeMillis(),
            "totalParts" to totalParts,
        )

        try {
            withContext(Dispatchers.IO + NonCancellable) {
                db.collection("users").document(uid).set(firestoreMap).await()

                var offset = 0
                for (i in 1..totalParts) {
                    val length = minOf(CHUNK_MAX_BYTES, bytes.size - offset)
                    val chunkBytes = bytes.copyOfRange(offset, offset + length)
                    val chunkStr = String(chunkBytes, Charsets.UTF_8)

                    val partMap = mapOf(
                        "chunkData" to chunkStr,
                        "partIndex" to i,
                        "totalParts" to totalParts,
                    )
                    db.collection("users").document("${uid}_part$i").set(partMap).await()
                    offset += length
                }
            }
            Timber.i("SyncManager: Cloud push completed in $totalParts part(s)")
        } catch (e: Exception) {
            Timber.w(e, "SyncManager: Cloud push failed (permission or network error)")
        }
    }

    suspend fun applyPayloadFromCloud() = withContext(Dispatchers.IO) {
        try {
            authManager.refreshSessionSilently()
            val firebaseAuth = authManager.getFirebaseAuth()
            val uid = authManager.currentUserUid.first() ?: firebaseAuth.currentUser?.uid ?: return@withContext
            val db = getFirestore()

            val headerDoc = db.collection("users").document(uid).get().await()
            val totalParts = headerDoc.getLong("totalParts")?.toInt() ?: 1

            val stringBuilder = StringBuilder()
            for (i in 1..totalParts) {
                val partDoc = db.collection("users").document("${uid}_part$i").get().await()
                val chunkData = partDoc.getString("chunkData") ?: ""
                stringBuilder.append(chunkData)
            }

            val unifiedJson = stringBuilder.toString()
            if (unifiedJson.isBlank()) return@withContext

            val payload = jsonToPayload(unifiedJson)

            payload.savedSongs.forEach { sDto ->
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
                        savedAt = LocalDateTime.ofEpochSecond(sDto.savedAt, 0, ZoneOffset.UTC),
                    ),
                )
            }
            payload.savedArtists.forEach { aDto ->
                database.insertSavedArtist(
                    SavedArtistEntity(
                        id = aDto.id,
                        name = aDto.name,
                        thumbnailUrl = aDto.thumbnailUrl,
                        channelId = aDto.channelId,
                        savedAt = LocalDateTime.ofEpochSecond(aDto.savedAt, 0, ZoneOffset.UTC),
                    ),
                )
            }
            payload.savedAlbums.forEach { alDto ->
                database.insertSavedAlbum(
                    SavedAlbumEntity(
                        id = alDto.id,
                        title = alDto.title,
                        artistId = alDto.artistId.ifEmpty { null },
                        artistName = alDto.artistName.ifEmpty { null },
                        songCount = alDto.songCount,
                        duration = alDto.duration,
                        thumbnailUrl = alDto.thumbnailUrl,
                        savedAt = LocalDateTime.ofEpochSecond(alDto.savedAt, 0, ZoneOffset.UTC),
                    ),
                )
            }
            payload.savedPlaylists.forEach { pDto ->
                database.insertSavedPlaylist(
                    SavedPlaylistEntity(
                        id = pDto.id,
                        name = pDto.name,
                        browseId = pDto.browseId,
                        isEditable = pDto.isEditable,
                        thumbnailUrl = pDto.thumbnailUrl,
                    ),
                )
                pDto.songs.forEach { sDto ->
                    if (database.checkInPlaylist(pDto.id, sDto.id) == 0) {
                        database.insert(PlaylistSongMap(playlistId = pDto.id, songId = sDto.id, position = sDto.position))
                    }
                }
            }

            payload.historySongs.forEach { hDto ->
                database.insertHistorySong(
                    HistorySongEntity(
                        playedAt = hDto.playedAt,
                        songId = hDto.songId,
                        duration = hDto.duration,
                        playTimeMs = hDto.playTimeMs,
                    ),
                )
            }
            payload.historyArtists.forEach { hDto ->
                database.insertHistoryArtist(
                    HistoryArtistEntity(
                        playedAt = hDto.playedAt,
                        artistId = hDto.artistId,
                        name = hDto.name,
                        thumbnailUrl = hDto.thumbnailUrl,
                    ),
                )
            }
            payload.historyAlbums.forEach { hDto ->
                database.insertHistoryAlbum(
                    HistoryAlbumEntity(
                        playedAt = hDto.playedAt,
                        albumId = hDto.albumId,
                        artistId = hDto.artistId,
                        playedsongsJson = hDto.playedsongsJson,
                    ),
                )
            }
            payload.historyPlaylists.forEach { hDto ->
                database.insertHistoryPlaylist(
                    HistoryPlaylistEntity(
                        playedAt = hDto.playedAt,
                        playlistId = hDto.playlistId,
                        playedsongsJson = hDto.playedsongsJson,
                    ),
                )
            }

            Timber.i("SyncManager: Applied unified payload from $totalParts part(s)")
        } catch (e: Exception) {
            Timber.w(e, "SyncManager: Cloud restore failed (permission or network error)")
        }
    }

    private fun payloadToJson(payload: SavedAndHistoryPayloadDto): String {
        val root = JSONObject()

        val savedSongsArr = JSONArray()
        payload.savedSongs.forEach { s ->
            val obj = JSONObject()
            obj.put("videoId", s.videoId)
            obj.put("title", s.title)
            obj.put("artistId", s.artistId)
            obj.put("artistName", s.artistName)
            obj.put("thumbnailUrl", s.thumbnailUrl ?: "")
            obj.put("albumId", s.albumId ?: "")
            obj.put("albumName", s.albumName ?: "")
            obj.put("duration", s.duration)
            obj.put("savedAt", s.savedAt)
            savedSongsArr.put(obj)
        }
        root.put("savedsong", savedSongsArr)

        val savedArtistsArr = JSONArray()
        payload.savedArtists.forEach { a ->
            val obj = JSONObject()
            obj.put("id", a.id)
            obj.put("name", a.name)
            obj.put("thumbnailUrl", a.thumbnailUrl ?: "")
            obj.put("channelId", a.channelId ?: "")
            obj.put("savedAt", a.savedAt)
            savedArtistsArr.put(obj)
        }
        root.put("savedartist", savedArtistsArr)

        val savedAlbumsArr = JSONArray()
        payload.savedAlbums.forEach { al ->
            val obj = JSONObject()
            obj.put("id", al.id)
            obj.put("title", al.title)
            obj.put("artistId", al.artistId)
            obj.put("artistName", al.artistName)
            obj.put("thumbnailUrl", al.thumbnailUrl ?: "")
            obj.put("songCount", al.songCount)
            obj.put("duration", al.duration)
            obj.put("savedAt", al.savedAt)
            savedAlbumsArr.put(obj)
        }
        root.put("savedalbum", savedAlbumsArr)

        val savedPlaylistsArr = JSONArray()
        payload.savedPlaylists.forEach { p ->
            val obj = JSONObject()
            obj.put("id", p.id)
            obj.put("name", p.name)
            obj.put("browseId", p.browseId ?: "")
            obj.put("isEditable", p.isEditable)
            obj.put("thumbnailUrl", p.thumbnailUrl ?: "")
            obj.put("createdAt", p.createdAt ?: "")

            val songsArr = JSONArray()
            p.songs.forEach { ps ->
                val songObj = JSONObject()
                songObj.put("id", ps.id)
                songObj.put("title", ps.title)
                songObj.put("artistName", ps.artistName)
                songObj.put("thumbnailUrl", ps.thumbnailUrl ?: "")
                songObj.put("position", ps.position)
                songsArr.put(songObj)
            }
            obj.put("songs", songsArr)
            savedPlaylistsArr.put(obj)
        }
        root.put("savedplaylist", savedPlaylistsArr)

        val historySongsArr = JSONArray()
        payload.historySongs.forEach { hs ->
            val obj = JSONObject()
            obj.put("songId", hs.songId)
            obj.put("duration", hs.duration)
            obj.put("playedAt", hs.playedAt)
            obj.put("playTimeMs", hs.playTimeMs)
            historySongsArr.put(obj)
        }
        root.put("historialsong", historySongsArr)

        val historyArtistsArr = JSONArray()
        payload.historyArtists.forEach { ha ->
            val obj = JSONObject()
            obj.put("artistId", ha.artistId)
            obj.put("name", ha.name ?: "")
            obj.put("thumbnailUrl", ha.thumbnailUrl ?: "")
            obj.put("playedAt", ha.playedAt)
            historyArtistsArr.put(obj)
        }
        root.put("historialartist", historyArtistsArr)

        val historyAlbumsArr = JSONArray()
        payload.historyAlbums.forEach { hal ->
            val obj = JSONObject()
            obj.put("albumId", hal.albumId)
            obj.put("artistId", hal.artistId ?: "")
            obj.put("playedsongsJson", hal.playedsongsJson)
            obj.put("playedAt", hal.playedAt)
            historyAlbumsArr.put(obj)
        }
        root.put("historialalbum", historyAlbumsArr)

        val historyPlaylistsArr = JSONArray()
        payload.historyPlaylists.forEach { hp ->
            val obj = JSONObject()
            obj.put("playlistId", hp.playlistId)
            obj.put("playedsongsJson", hp.playedsongsJson)
            obj.put("playedAt", hp.playedAt)
            historyPlaylistsArr.put(obj)
        }
        root.put("historialplaylist", historyPlaylistsArr)

        return root.toString()
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
                                artistName = sObj.optString("artistName"),
                                thumbnailUrl = sObj.optString("thumbnailUrl").ifEmpty { null },
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

    @Suppress("unused")
    suspend fun restoreLibraryFromCloud() = withContext(Dispatchers.IO) {
        applyPayloadFromCloud()
    }

    suspend fun getUserLibrary(): UserLibraryDto? = withContext(Dispatchers.IO) {
        try {
            authManager.refreshSessionSilently()
            val firebaseAuth = authManager.getFirebaseAuth()
            val uid = authManager.currentUserUid.first() ?: firebaseAuth.currentUser?.uid ?: return@withContext null
            val db = getFirestore()

            val headerDoc = db.collection("users").document(uid).get().await()
            val totalParts = headerDoc.getLong("totalParts")?.toInt() ?: 1

            val stringBuilder = StringBuilder()
            for (i in 1..totalParts) {
                val partDoc = db.collection("users").document("${uid}_part$i").get().await()
                val chunkData = partDoc.getString("chunkData") ?: ""
                stringBuilder.append(chunkData)
            }

            val unifiedJson = stringBuilder.toString()
            if (unifiedJson.isBlank()) return@withContext null

            val payload = jsonToPayload(unifiedJson)
            UserLibraryDto(
                favorites = payload.savedSongs,
                albums = payload.savedAlbums,
                artists = payload.savedArtists,
                playlists = payload.savedPlaylists,
                metadata = payload.metadata,
            )
        } catch (e: Exception) {
            Timber.w(e, "SyncManager: getUserLibrary failed (permission or network error)")
            null
        }
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun applyLibraryToLocal(library: UserLibraryDto? = null) = withContext(Dispatchers.IO) {
        applyPayloadFromCloud()
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun saveUserLibrary(library: UserLibraryDto? = null) = withContext(Dispatchers.IO) {
        pushPayloadToCloud()
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun handleLogout(ctx: Context = context): Boolean = withContext(Dispatchers.IO) {
        try {
            pushPayloadToCloud()
            authManager.clearAccountInfo()
            true
        } catch (e: Exception) {
            Timber.e(e, "SyncManager: Error in handleLogout")
            false
        }
    }

    suspend fun toggleLike(song: SongEntity) = withContext(Dispatchers.IO) {
        val existingSaved = database.getSavedSong(song.id)
        val isCurrentlyLiked = existingSaved != null || song.isLiked
        if (isCurrentlyLiked) {
            database.deleteSavedSong(song.id)
            database.update(song.copy(isLiked = false))
        } else {
            val songWithArtists = database.getSongById(song.id)
            val artist = songWithArtists?.artists?.firstOrNull() ?: database.songArtistMap(song.id).firstOrNull()?.let { database.getArtistById(it.artistId) }
            database.insertSavedSong(
                SavedSongEntity(
                    id = song.id,
                    title = song.title,
                    artistId = artist?.id,
                    artistName = artist?.name,
                    duration = song.duration,
                    thumbnailUrl = song.thumbnailUrl,
                    albumId = song.albumId,
                    albumName = song.albumName,
                ),
            )
            database.update(song.copy(isLiked = true))
        }
    }

    suspend fun toggleAlbumLike(album: AlbumEntity) = withContext(Dispatchers.IO) {
        val existingSaved = database.getSavedAlbum(album.id)
        val isCurrentlyLiked = existingSaved != null || album.isLiked
        if (isCurrentlyLiked) {
            database.deleteSavedAlbum(album.id)
            database.update(album.copy(isLiked = false))
        } else {
            val albumArtist = database.albumArtistMaps(album.id).firstOrNull()?.let { database.getArtistById(it.artistId) }
            database.insertSavedAlbum(
                SavedAlbumEntity(
                    id = album.id,
                    title = album.title,
                    artistId = albumArtist?.id,
                    artistName = albumArtist?.name,
                    songCount = album.songCount,
                    duration = album.duration,
                    thumbnailUrl = album.thumbnailUrl,
                    year = album.year,
                ),
            )
            database.update(album.copy(isLiked = true))
        }
    }

    suspend fun toggleArtistLike(artist: ArtistEntity) = withContext(Dispatchers.IO) {
        val existingSaved = database.getSavedArtist(artist.id)
        val isCurrentlyLiked = existingSaved != null || artist.isBookmarked
        if (isCurrentlyLiked) {
            database.deleteSavedArtist(artist.id)
            database.update(artist.copy(isBookmarked = false))
        } else {
            database.insertSavedArtist(
                SavedArtistEntity(
                    id = artist.id,
                    name = artist.name,
                    thumbnailUrl = artist.thumbnailUrl,
                    channelId = artist.channelId,
                ),
            )
            database.update(artist.copy(isBookmarked = true))
        }
    }

    suspend fun togglePlaylistLike(playlist: PlaylistEntity) = withContext(Dispatchers.IO) {
        val pId = playlist.browseId ?: playlist.id
        val existingSaved = database.getSavedPlaylist(pId) ?: database.getSavedPlaylist(playlist.id)
        val existingLegacy = database.getPlaylistById(pId) ?: database.getPlaylistById(playlist.id)

        if (existingSaved != null || existingLegacy != null) {
            database.deleteSavedPlaylist(pId)
            database.deleteSavedPlaylist(playlist.id)
            database.deletePlaylistById(pId)
            database.deletePlaylistById(playlist.id)
        } else {
            val localId = if (playlist.id.startsWith("LP")) playlist.id else PlaylistEntity.generatePlaylistId()
            val newEntity = playlist.copy(id = localId)
            database.insertSavedPlaylist(
                SavedPlaylistEntity(
                    id = newEntity.id,
                    name = newEntity.name,
                    browseId = newEntity.browseId,
                    isEditable = newEntity.isEditable,
                    thumbnailUrl = newEntity.thumbnailUrl,
                ),
            )
            database.insert(newEntity)
        }
    }

    private fun ensureArtistExists(name: String): String {
        val existing = database.artistByName(name)
        if (existing != null) return existing.id
        val newId = ArtistEntity.generateArtistId()
        database.insert(ArtistEntity(id = newId, name = name))
        return newId
    }

    @Suppress("unused")
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
            isEditable = true,
        )
        withContext(Dispatchers.IO) {
            database.insert(playlist)
        }
        Timber.d("[LOCAL_SAVE_DEBUG] New playlist created in Room: $name (ID: $id)")
        return id
    }

    @Suppress("unused")
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

    suspend fun deletePlaylist(playlistId: String) = withContext(Dispatchers.IO) {
        database.deleteSavedPlaylist(playlistId)
        database.deletePlaylistById(playlistId)
    }
}
