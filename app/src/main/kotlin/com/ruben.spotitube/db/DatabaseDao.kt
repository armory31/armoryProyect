/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ruben.spotitube.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import com.ruben.spotitube.constants.AlbumSortType
import com.ruben.spotitube.constants.ArtistSongSortType
import com.ruben.spotitube.constants.ArtistSortType
import com.ruben.spotitube.constants.PlaylistSortType
import com.ruben.spotitube.constants.SongSortType
import com.ruben.spotitube.db.entities.Album
import com.ruben.spotitube.db.entities.AlbumArtistMap
import com.ruben.spotitube.db.entities.AlbumWithSongs
import com.ruben.spotitube.db.entities.Artist
import com.ruben.spotitube.db.entities.Event
import com.ruben.spotitube.db.entities.FormatEntity
import com.ruben.spotitube.db.entities.LyricsEntity
import com.ruben.spotitube.db.entities.PlayCountEntity
import com.ruben.spotitube.db.entities.Playlist
import com.ruben.spotitube.db.entities.PlaylistEntity
import com.ruben.spotitube.db.entities.PlaylistSong
import com.ruben.spotitube.db.entities.PlaylistSongMap
import com.ruben.spotitube.db.entities.PlaylistTagMap
import com.ruben.spotitube.db.entities.RelatedSongMap
import com.ruben.spotitube.db.entities.SearchHistory
import com.ruben.spotitube.db.entities.SetVideoIdEntity
import com.ruben.spotitube.db.entities.Song
import com.ruben.spotitube.db.entities.HistoryAlbumEntity
import com.ruben.spotitube.db.entities.HistoryArtistEntity
import com.ruben.spotitube.db.entities.HistoryPlaylistEntity
import com.ruben.spotitube.db.entities.HistorySongEntity
import com.ruben.spotitube.db.entities.SavedAlbumEntity
import com.ruben.spotitube.db.entities.SavedArtistEntity
import com.ruben.spotitube.db.entities.SavedPlaylistEntity
import com.ruben.spotitube.db.entities.SavedSongEntity
import com.ruben.spotitube.db.entities.SongArtistMap
import com.ruben.spotitube.db.entities.SongWithStats
import com.ruben.spotitube.db.entities.TagEntity
import com.ruben.spotitube.extensions.reversed
import com.ruben.spotitube.extensions.toSQLiteQuery
import com.ruben.spotitube.innertube.pages.AlbumPage
import com.ruben.spotitube.innertube.pages.ArtistPage
import com.ruben.spotitube.models.MediaMetadata
import com.ruben.spotitube.sync.LibrarySummary
import com.ruben.spotitube.ui.utils.resize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.text.Collator
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

@Dao
@RewriteQueriesToDropUnusedColumns
interface DatabaseDao {
    suspend fun clearNonLocalSongs() {}
    suspend fun clearNonLocalArtists() {}
    suspend fun clearNonLocalAlbums() {}
    suspend fun clearNonLocalPlaylists() {}
    suspend fun clearSongArtistMaps() {}
    suspend fun clearSongAlbumMaps() {}

    @Transaction
    suspend fun clearUserData() {}

    fun songsByRowIdAsc(): Flow<List<Song>> = allSavedSongs().map { list -> list.map { it.toSong() } }
    fun songsByNameAsc(): Flow<List<Song>> = allSavedSongs().map { list -> list.map { it.toSong() }.sortedBy { s -> s.song.title } }
    fun songsByPlayTimeAsc(): Flow<List<Song>> = allSavedSongs().map { list -> list.map { it.toSong() } }

    fun allAlbumsByRowIdAsc(): Flow<List<Album>> = allSavedAlbums().map { list -> list.map { it.toAlbum() } }
    fun allArtistsByNameAsc(): Flow<List<Artist>> = allSavedArtists().map { list -> list.map { it.toArtist() } }

    fun editablePlaylistsByCreateDateAsc(): Flow<List<Playlist>> = playlistsWithMetadata().map { list -> list.filter { it.playlist.isEditable } }

    @Query("SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun checkInPlaylist(playlistId: String, songId: String): Int

    fun songs(
        sortType: SongSortType,
        descending: Boolean,
        filterVideo: Boolean = false,
    ) = when (sortType) {
        SongSortType.CREATE_DATE ->
            if (filterVideo) {
                songsByRowIdAscNoVideo()
            } else {
                songsByRowIdAsc()
            }

        SongSortType.NAME ->
            (if (filterVideo) {
                songsByNameAscNoVideo()
            } else {
                songsByNameAsc()
            }).map { songs ->
                val collator = Collator.getInstance(Locale.getDefault())
                collator.strength = Collator.PRIMARY
                songs.sortedWith(compareBy(collator) { it.song.title })
            }

        SongSortType.ARTIST ->
            (if (filterVideo) {
                songsByRowIdAscNoVideo()
            } else {
                songsByRowIdAsc()
            }).map { songs ->
                val collator = Collator.getInstance(Locale.getDefault())
                collator.strength = Collator.PRIMARY
                songs
                    .sortedWith(
                        compareBy(collator) { song ->
                            song.artists.joinToString("") { artist -> artist.name }
                        },
                    ).groupBy { it.album?.title }
                    .flatMap { (_, songsByAlbum) ->
                        songsByAlbum.sortedBy { album ->
                            album.artists.joinToString(
                                "",
                            ) { artist -> artist.name }
                        }
                    }
            }

        SongSortType.PLAY_TIME -> songsByPlayTimeAsc()
    }.map { it.reversed(descending) }

    fun songsByRowIdAscNoVideo(): Flow<List<Song>> = allSavedSongs().map { list -> list.map { it.toSong() } }
    fun songsByNameAscNoVideo(): Flow<List<Song>> = allSavedSongs().map { list -> list.map { it.toSong() }.sortedBy { s -> s.song.title } }
    fun songsByPlayTimeAscNoVideo(): Flow<List<Song>> = allSavedSongs().map { list -> list.map { it.toSong() } }

    fun albumSongs(albumId: String): Flow<List<Song>> = allSavedSongs().map { list -> list.filter { it.albumId == albumId }.map { it.toSong() } }

    fun artistSongs(
        artistId: String,
        sortType: ArtistSongSortType,
        descending: Boolean,
    ): Flow<List<Song>> = allSavedSongs().map { savedList ->
        val savedMatching = savedList.filter { it.artistId == artistId }.map { it.toSong() }
        val collator = Collator.getInstance(Locale.getDefault())
        collator.strength = Collator.PRIMARY
        when (sortType) {
            ArtistSongSortType.CREATE_DATE -> savedMatching
            ArtistSongSortType.NAME -> savedMatching.sortedWith(compareBy(collator) { it.song.title })
            ArtistSongSortType.PLAY_TIME -> savedMatching.sortedBy { it.song.totalPlayTime }
        }.let { if (descending) it.reversed() else it }
    }.flowOn(Dispatchers.IO)

    fun artistSongsPreview(
        artistId: String,
        previewSize: Int = 3,
    ): Flow<List<Song>> = allSavedSongs().map { savedList ->
        savedList.filter { it.artistId == artistId }.map { it.toSong() }.take(previewSize)
    }.flowOn(Dispatchers.IO)

    fun quickPicks(now: Long = System.currentTimeMillis()): Flow<List<Song>> = allSavedSongs().map { list -> list.map { it.toSong() } }

    fun getRecommendationAlbum(
        now: Long = System.currentTimeMillis(),
        limit: Int = 5,
        offset: Int = 0,
    ): Flow<List<Song>> = allSavedSongs().map { list -> list.map { it.toSong() }.take(limit) }

    fun mostPlayedSongsStats(
        fromTimeStamp: Long,
        limit: Int = 6,
        offset: Int = 0,
        toTimeStamp: Long? = LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli(),
    ): Flow<List<SongWithStats>> = allHistorySongs().map { historyList ->
        val toTime = toTimeStamp ?: Long.MAX_VALUE
        val filtered = historyList.filter { it.playedAt in fromTimeStamp..toTime }
        val grouped = filtered.groupBy { it.songId }
        val sorted = grouped.map { (songId, items) ->
            val totalTime = items.sumOf { it.playTimeMs }
            val playCount = items.size
            songId to Pair(playCount, totalTime)
        }.sortedByDescending { it.second.second }
            .drop(offset)
            .let { if (limit >= 0) it.take(limit) else it }

        sorted.mapNotNull { (songId, stats) ->
            val savedSong = getSavedSong(songId)
            val songObj = savedSong?.toSong()
                ?: getSongById(songId)
                ?: Song(song = SavedSongEntity(id = songId, title = songId), artists = emptyList())

            SongWithStats(
                id = songObj.id,
                title = songObj.title,
                thumbnailUrl = songObj.thumbnailUrl ?: "",
                songCountListened = stats.first,
                timeListened = stats.second
            )
        }
    }.flowOn(Dispatchers.IO)

    fun mostPlayedSongs(
        fromTimeStamp: Long,
        limit: Int = 6,
        offset: Int = 0,
        toTimeStamp: Long? = LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli(),
    ): Flow<List<Song>> = allHistorySongs().map { historyList ->
        val toTime = toTimeStamp ?: Long.MAX_VALUE
        val filtered = historyList.filter { it.playedAt in fromTimeStamp..toTime }
        val grouped = filtered.groupBy { it.songId }
        val sorted = grouped.map { (songId, items) ->
            val totalTime = items.sumOf { it.playTimeMs }
            val playCount = items.size
            songId to Pair(playCount, totalTime)
        }.sortedByDescending { it.second.second }
            .drop(offset)
            .let { if (limit >= 0) it.take(limit) else it }

        sorted.mapNotNull { (songId, _) ->
            val savedSong = getSavedSong(songId)
            savedSong?.toSong()
                ?: getSongById(songId)
                ?: Song(song = SavedSongEntity(id = songId, title = songId), artists = emptyList())
        }
    }.flowOn(Dispatchers.IO)

    fun mostPlayedArtists(
        fromTimeStamp: Long,
        limit: Int = 6,
        offset: Int = 0,
        toTimeStamp: Long? = LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli(),
    ): Flow<List<Artist>> = allHistoryArtists().map { historyList ->
        val toTime = toTimeStamp ?: Long.MAX_VALUE
        val filtered = historyList.filter { it.playedAt in fromTimeStamp..toTime }
        val grouped = filtered.groupBy { it.artistId }
        val sorted = grouped.map { (artistId, items) ->
            artistId to items.size
        }.sortedByDescending { it.second }
            .drop(offset)
            .let { if (limit >= 0) it.take(limit) else it }

        sorted.mapNotNull { (artistId, count) ->
            val savedArtist = getSavedArtist(artistId)
            val artistObj = savedArtist?.toArtist()
                ?: getArtistById(artistId)?.let { Artist(artist = it, songCount = count) }
                ?: Artist(artist = SavedArtistEntity(id = artistId, name = artistId), songCount = count)

            artistObj.copy(songCount = count)
        }
    }.flowOn(Dispatchers.IO)

    fun mostPlayedAlbums(
        fromTimeStamp: Long,
        limit: Int = 6,
        offset: Int = 0,
        toTimeStamp: Long? = LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli(),
    ): Flow<List<Album>> = allHistoryAlbums().map { historyList ->
        val toTime = toTimeStamp ?: Long.MAX_VALUE
        val filtered = historyList.filter { it.playedAt in fromTimeStamp..toTime }
        val grouped = filtered.groupBy { it.albumId }
        val sorted = grouped.map { (albumId, items) ->
            albumId to items.size
        }.sortedByDescending { it.second }
            .drop(offset)
            .let { if (limit >= 0) it.take(limit) else it }

        sorted.mapNotNull { (albumId, count) ->
            val savedAlbum = getSavedAlbum(albumId)
            val albumObj = savedAlbum?.toAlbum()
                ?: getAlbumById(albumId)?.let { Album(album = it, artists = emptyList(), songCountListened = count) }
                ?: Album(album = SavedAlbumEntity(id = albumId, title = albumId, songCount = count, duration = 0), artists = emptyList(), songCountListened = count)

            albumObj.copy(songCountListened = count)
        }
    }.flowOn(Dispatchers.IO)

    fun artistAlbumsPreview(artistId: String, previewSize: Int = 6): Flow<List<Album>> = allSavedAlbums().map { savedList ->
        savedList.filter { it.artistId == artistId }.map { it.toAlbum() }.take(previewSize)
    }.flowOn(Dispatchers.IO)

    @Query("SELECT sum(count) from playCount WHERE song = :songId")
    fun getLifetimePlayCount(songId: String?): Flow<Int>
    @Query("SELECT sum(count) from playCount WHERE song = :songId AND year = :year")
    fun getPlayCountByYear(songId: String?, year: Int): Flow<Int>
    @Query("SELECT count from playCount WHERE song = :songId AND year = :year AND month = :month")
    fun getPlayCountByMonth(songId: String?, year: Int, month: Int): Flow<Int>

    fun forgottenFavorites(now: Long = System.currentTimeMillis()): Flow<List<Song>> = allSavedSongs().map { list -> list.map { it.toSong() } }

    fun recommendedAlbum(
        now: Long = System.currentTimeMillis(),
        limit: Int = 5,
        offset: Int = 0,
    ): Flow<List<Song>> = allSavedSongs().map { list -> list.map { it.toSong() }.take(limit) }

    @Query("SELECT EXISTS(SELECT 1 FROM related_song_map WHERE songId = :songId LIMIT 1)")
    fun hasRelatedSongs(songId: String): Flow<Boolean>

    fun song(songId: String?): Flow<Song?> = allSavedSongs().map { list -> list.firstOrNull { it.id == songId }?.toSong() }

    suspend fun getSongById(songId: String): Song? = getSavedSong(songId)?.toSong()

    fun getSongByIdBlocking(songId: String): Song? = kotlinx.coroutines.runBlocking { getSavedSong(songId)?.toSong() }

    suspend fun getSongsByIds(songIds: List<String>): List<Song> = allSavedSongs().first().filter { it.id in songIds }.map { it.toSong() }

    fun songArtistMap(songId: String): List<SongArtistMap> = emptyList()

    fun allSongs(): Flow<List<Song>> = allSavedSongs().map { list -> list.map { it.toSong() } }

    fun allArtistsByPlayTime(): Flow<List<Artist>> = allSavedArtists().map { list -> list.map { it.toArtist() } }

    @Query("SELECT * FROM set_video_id WHERE videoId = :videoId")
    suspend fun getSetVideoId(videoId: String): SetVideoIdEntity?

    @Query("DELETE FROM set_video_id WHERE videoId = :videoId")
    fun deleteSetVideoId(videoId: String)

    @Transaction
    @Query("SELECT * FROM format WHERE id = :id")
    fun format(id: String?): Flow<FormatEntity?>

    @Transaction
    @Query("SELECT * FROM lyrics WHERE id = :id")
    fun lyrics(id: String?): Flow<LyricsEntity?>

    @Transaction
    @Query("SELECT * FROM lyrics WHERE id = :id LIMIT 1")
    suspend fun getLyricsById(id: String): LyricsEntity?

    fun artistsByRowIdAsc(): Flow<List<Artist>> = allSavedArtists().map { list -> list.map { it.toArtist() } }
    fun artistsByNameAsc(): Flow<List<Artist>> = allSavedArtists().map { list -> list.map { it.toArtist() }.sortedBy { a -> a.artist.name } }
    fun artistsBySongCountAsc(): Flow<List<Artist>> = allSavedArtists().map { list -> list.map { it.toArtist() } }
    fun artistsByPlayTimeAsc(): Flow<List<Artist>> = allSavedArtists().map { list -> list.map { it.toArtist() } }

    fun artists(sortType: ArtistSortType, descending: Boolean) = allSavedArtists().map { list -> list.map { it.toArtist() } }

    fun artist(id: String): Flow<Artist?> = allSavedArtists().map { list -> list.firstOrNull { it.id == id }?.toArtist() }

    fun albumsByRowIdAsc(): Flow<List<Album>> = allSavedAlbums().map { list -> list.map { it.toAlbum() } }
    fun albumsByNameAsc(): Flow<List<Album>> = allSavedAlbums().map { list -> list.map { it.toAlbum() }.sortedBy { a -> a.album.title } }
    fun albumsByYearAsc(): Flow<List<Album>> = allSavedAlbums().map { list -> list.map { it.toAlbum() } }
    fun albumsBySongCountAsc(): Flow<List<Album>> = allSavedAlbums().map { list -> list.map { it.toAlbum() } }
    fun albumsByLengthAsc(): Flow<List<Album>> = allSavedAlbums().map { list -> list.map { it.toAlbum() } }

    fun albumsByPlayTimeAsc(): Flow<List<Album>> = allSavedAlbums().map { list -> list.map { it.toAlbum() } }

    fun albums(
        sortType: AlbumSortType,
        descending: Boolean,
    ) = when (sortType) {
        AlbumSortType.CREATE_DATE -> albumsByRowIdAsc()
        AlbumSortType.NAME ->
            albumsByNameAsc().map { albums ->
                val collator = Collator.getInstance(Locale.getDefault())
                collator.strength = Collator.PRIMARY
                albums.sortedWith(compareBy(collator) { it.album.title })
            }

        AlbumSortType.ARTIST ->
            albumsByRowIdAsc().map { albums ->
                val collator = Collator.getInstance(Locale.getDefault())
                collator.strength = Collator.PRIMARY
                albums.sortedWith(compareBy(collator) { album -> album.artists.joinToString("") { artist -> artist.name } })
            }

        AlbumSortType.YEAR -> albumsByYearAsc()
        AlbumSortType.SONG_COUNT -> albumsBySongCountAsc()
        AlbumSortType.LENGTH -> albumsByLengthAsc()
        AlbumSortType.PLAY_TIME -> albumsByPlayTimeAsc()
    }.map { it.reversed(descending) }

    fun album(id: String): Flow<Album?> = allSavedAlbums().map { list -> list.firstOrNull { it.id == id }?.toAlbum() }

    fun albumsByRowId(ids: Collection<String>): Flow<List<Album>> = allSavedAlbums().map { list -> list.filter { it.id in ids }.map { it.toAlbum() } }
    fun albumsByTitle(ids: Collection<String>): Flow<List<Album>> = allSavedAlbums().map { list -> list.filter { it.id in ids }.map { it.toAlbum() } }
    fun albumsByYear(ids: Collection<String>): Flow<List<Album>> = allSavedAlbums().map { list -> list.filter { it.id in ids }.map { it.toAlbum() } }
    fun albumsBySongCount(ids: Collection<String>): Flow<List<Album>> = allSavedAlbums().map { list -> list.filter { it.id in ids }.map { it.toAlbum() } }
    fun albumsByDuration(ids: Collection<String>): Flow<List<Album>> = allSavedAlbums().map { list -> list.filter { it.id in ids }.map { it.toAlbum() } }
    fun albumsByPlayTime(ids: Collection<String>): Flow<List<Album>> = allSavedAlbums().map { list -> list.filter { it.id in ids }.map { it.toAlbum() } }

    fun albumsByIds(
        ids: Collection<String>,
        sortType: AlbumSortType,
        descending: Boolean,
    ): Flow<List<Album>> = allSavedAlbums().map { list -> list.filter { it.id in ids }.map { it.toAlbum() } }

    fun albumWithSongs(albumId: String): Flow<AlbumWithSongs?> = allSavedAlbums().map { list ->
        val saved = list.firstOrNull { it.id == albumId }
        saved?.let { AlbumWithSongs(album = it.toAlbum().album, songs = emptyList(), artists = emptyList()) }
    }

    fun albumArtistMaps(albumId: String): List<AlbumArtistMap> = emptyList()

    @Query("SELECT * FROM playlist_song_map ORDER BY position ASC")
    fun allPlaylistSongMaps(): Flow<List<PlaylistSongMap>>

    fun playlistsWithMetadata(): Flow<List<Playlist>> = combine(
        allSavedPlaylists(),
        allPlaylistSongMaps(),
        allSavedSongs()
    ) { savedPlaylists, songMaps, savedSongs ->
        val mapsByPlaylistId = songMaps.groupBy { it.playlistId }
        val songMap = savedSongs.associateBy { it.id }

        savedPlaylists.map { saved ->
            val matchingMaps = mapsByPlaylistId[saved.id]
                ?: (saved.browseId?.let { mapsByPlaylistId[it] })
                ?: (mapsByPlaylistId[saved.id.removePrefix("VL")])
                ?: (mapsByPlaylistId["VL" + saved.id.removePrefix("VL")])
                ?: emptyList()

            val count = matchingMaps.size
            val thumbnails = matchingMaps
                .mapNotNull { songMap[it.songId]?.thumbnailUrl }
                .distinct()
                .take(4)

            saved.toPlaylist(
                songCount = count,
                songThumbnails = if (saved.thumbnailUrl != null) listOf(saved.thumbnailUrl) else thumbnails
            )
        }
    }

    fun playlistsByRowIdAsc(): Flow<List<Playlist>> = playlistsWithMetadata()
    fun playlistsByUpdatedDateAsc(): Flow<List<Playlist>> = playlistsWithMetadata()
    fun playlistsByNameAsc(): Flow<List<Playlist>> = playlistsWithMetadata()
    fun playlistsByCustomOrderAsc(): Flow<List<Playlist>> = playlistsWithMetadata()

    @Query("SELECT songId FROM playlist_song_map WHERE playlistId = :playlistId AND songId IN (:songIds)")
    suspend fun playlistDuplicates(playlistId: String, songIds: List<String>): List<String>

    @Query("SELECT MAX(position) FROM playlist_song_map WHERE playlistId = :playlistId")
    suspend fun maxPlaylistSongPosition(playlistId: String): Int?

    @Transaction
    suspend fun addSongsToPlaylist(playlistId: String, songIds: List<String>) {
        val maxPosition = maxPlaylistSongPosition(playlistId) ?: -1
        songIds.forEachIndexed { index, songId ->
            insert(PlaylistSongMap(playlistId = playlistId, songId = songId, position = maxPosition + 1 + index))
        }
    }

    suspend fun setPlaylistCustomOrder(playlistId: String, customOrder: Int) {}

    fun playlists(
        sortType: PlaylistSortType,
        descending: Boolean,
    ) = when (sortType) {
        PlaylistSortType.CREATE_DATE -> playlistsByRowIdAsc()
        PlaylistSortType.NAME ->
            playlistsByNameAsc().map { playlists ->
                val collator = Collator.getInstance(Locale.getDefault())
                collator.strength = Collator.PRIMARY
                playlists.sortedWith(compareBy(collator) { it.playlist.name })
            }

        PlaylistSortType.SONG_COUNT -> playlistsByRowIdAsc()
        PlaylistSortType.LAST_UPDATED -> playlistsByUpdatedDateAsc()
        PlaylistSortType.CUSTOM -> playlistsByCustomOrderAsc()
    }.map { list ->
        if (descending && sortType != PlaylistSortType.CUSTOM) list.asReversed() else list
    }

    fun likedSongsByRowIdAsc(): Flow<List<Song>> = allSavedSongs().map { list -> list.filter { it.isLiked }.map { it.toSong() } }

    fun albumsLikedInternal(): Flow<List<Album>> = allSavedAlbums().map { list -> list.map { it.toAlbum() } }

    fun albumsLiked(sortType: AlbumSortType, descending: Boolean): Flow<List<Album>> =
        albumsLikedInternal().map { albums ->
            val collator = Collator.getInstance(Locale.getDefault())
            collator.strength = Collator.PRIMARY
            when (sortType) {
                AlbumSortType.CREATE_DATE -> albums
                AlbumSortType.NAME -> albums.sortedWith(compareBy(collator) { it.album.title })
                AlbumSortType.ARTIST -> albums.sortedWith(compareBy(collator) { album -> album.artists.joinToString("") { it.name } })
                AlbumSortType.YEAR -> albums.sortedBy { it.album.year }
                AlbumSortType.SONG_COUNT -> albums.sortedBy { it.album.songCount }
                AlbumSortType.LENGTH -> albums.sortedBy { it.album.duration }
                AlbumSortType.PLAY_TIME -> albums
            }.reversed(descending)
        }

    fun artistsBookmarkedInternal(): Flow<List<Artist>> = allSavedArtists().map { list -> list.map { it.toArtist() } }

    fun artistsBookmarked(sortType: ArtistSortType, descending: Boolean): Flow<List<Artist>> =
        artistsBookmarkedInternal().map { artists ->
            val collator = Collator.getInstance(Locale.getDefault())
            collator.strength = Collator.PRIMARY
            when (sortType) {
                ArtistSortType.CREATE_DATE -> artists
                ArtistSortType.NAME -> artists.sortedWith(compareBy(collator) { it.artist.name })
                ArtistSortType.SONG_COUNT -> artists.sortedBy { it.songCount }
                ArtistSortType.PLAY_TIME -> artists
            }.reversed(descending)
        }

    @Query("SELECT id FROM savedartist")
    fun getSavedArtistIds(): Flow<List<String>>

    @Query("SELECT id FROM savedalbum")
    fun getSavedAlbumIds(): Flow<List<String>>

    @Query("SELECT id FROM savedsong WHERE isLiked = 1 UNION SELECT videoId FROM set_video_id")
    fun getSavedSongIds(): Flow<List<String>>

    @Query("SELECT id FROM savedplaylist UNION SELECT browseId FROM savedplaylist WHERE browseId IS NOT NULL")
    fun getSavedPlaylistIds(): Flow<List<String>>

    @Transaction
    @Query("""
        SELECT 
            (SELECT COUNT(*) FROM savedsong) as songCount,
            (SELECT COUNT(*) FROM savedalbum) as albumCount,
            (SELECT COUNT(*) FROM savedartist) as artistCount,
            (SELECT COALESCE(SUM(playTimeMs), 0) FROM historialsong) as totalPlayTimeMillis
    """)
    fun getLibrarySummary(): Flow<LibrarySummary>

    @Query("SELECT * FROM playlist_song_map WHERE playlistId = :playlistId OR playlistId = (SELECT browseId FROM savedplaylist WHERE id = :playlistId) OR playlistId = (SELECT id FROM savedplaylist WHERE browseId = :playlistId) OR playlistId = REPLACE(:playlistId, 'VL', '') OR playlistId = 'VL' || REPLACE(:playlistId, 'VL', '') ORDER BY position ASC")
    fun rawPlaylistSongMaps(playlistId: String): Flow<List<PlaylistSongMap>>

    fun playlistSongs(playlistId: String): Flow<List<PlaylistSong>> = rawPlaylistSongMaps(playlistId).map { mapList ->
        mapList.mapNotNull { pMap ->
            val savedSong = getSavedSong(pMap.songId)
            val songObj = savedSong?.toSong()
                ?: getSongById(pMap.songId)
                ?: Song(song = SavedSongEntity(id = pMap.songId, title = pMap.songId), artists = emptyList())

            PlaylistSong(
                map = pMap,
                song = songObj
            )
        }
    }.flowOn(Dispatchers.IO)

    fun playlist(id: String): Flow<Playlist?> = playlistsWithMetadata().map { list -> list.firstOrNull { it.id == id || it.playlist.browseId == id } }

    suspend fun getPlaylistById(playlistId: String): Playlist? = playlistsWithMetadata().first().firstOrNull { it.id == playlistId || it.playlist.browseId == playlistId }

    fun getPlaylistByIdBlocking(playlistId: String): Playlist? = kotlinx.coroutines.runBlocking { getPlaylistById(playlistId) }

    fun playlistByBrowseId(browseId: String): Flow<Playlist?> = playlistsWithMetadata().map { list -> list.firstOrNull { it.playlist.browseId == browseId } }

    fun rawSearchSongs(
        query: String,
        previewSize: Int = Int.MAX_VALUE,
    ): Flow<List<Song>> = allSavedSongs().map { list -> list.filter { it.title.contains(query, ignoreCase = true) }.map { it.toSong() }.take(previewSize) }

    fun searchSongs(
        query: String,
        previewSize: Int = Int.MAX_VALUE,
    ): Flow<List<Song>> = allSavedSongs().map { savedList ->
        val matchingSaved = savedList.filter {
            it.title.contains(query, ignoreCase = true) || (it.artistName?.contains(query, ignoreCase = true) == true)
        }.map { it.toSong() }.take(previewSize)

        if (matchingSaved.size < previewSize) {
            val remaining = previewSize - matchingSaved.size
            val ytSongs = runCatching {
                com.ruben.spotitube.innertube.YouTube.search(query, com.ruben.spotitube.innertube.YouTube.SearchFilter.FILTER_SONG).getOrNull()?.items
                    ?.filterIsInstance<com.ruben.spotitube.innertube.models.SongItem>()
                    ?.map { item ->
                        Song(
                            song = SavedSongEntity(
                                id = item.id,
                                title = item.title,
                                duration = item.duration ?: -1,
                                thumbnailUrl = item.thumbnail
                            ),
                            artists = item.artists.map { a -> SavedArtistEntity(id = a.id ?: "", name = a.name) }
                        )
                    }?.take(remaining)
            }.getOrNull().orEmpty()

            (matchingSaved + ytSongs).distinctBy { it.id }.take(previewSize)
        } else {
            matchingSaved
        }
    }.flowOn(Dispatchers.IO)

    fun rawSearchArtists(
        query: String,
        previewSize: Int = Int.MAX_VALUE,
    ): Flow<List<Artist>> = allSavedArtists().map { list -> list.filter { it.name.contains(query, ignoreCase = true) }.map { it.toArtist() }.take(previewSize) }

    fun searchArtists(
        query: String,
        previewSize: Int = Int.MAX_VALUE,
    ): Flow<List<Artist>> = allSavedArtists().map { savedList ->
        val matchingSaved = savedList.filter {
            it.name.contains(query, ignoreCase = true)
        }.map { it.toArtist() }.take(previewSize)

        if (matchingSaved.size < previewSize) {
            val remaining = previewSize - matchingSaved.size
            val ytArtists = runCatching {
                com.ruben.spotitube.innertube.YouTube.search(query, com.ruben.spotitube.innertube.YouTube.SearchFilter.FILTER_ARTIST).getOrNull()?.items
                    ?.filterIsInstance<com.ruben.spotitube.innertube.models.ArtistItem>()
                    ?.map { item ->
                        Artist(
                            artist = SavedArtistEntity(
                                id = item.id,
                                name = item.title,
                                thumbnailUrl = item.thumbnail
                            ),
                            songCount = 0
                        )
                    }?.take(remaining)
            }.getOrNull().orEmpty()

            (matchingSaved + ytArtists).distinctBy { it.id }.take(previewSize)
        } else {
            matchingSaved
        }
    }.flowOn(Dispatchers.IO)

    fun rawSearchAlbums(
        query: String,
        previewSize: Int = Int.MAX_VALUE,
    ): Flow<List<Album>> = allSavedAlbums().map { list -> list.filter { it.title.contains(query, ignoreCase = true) }.map { it.toAlbum() }.take(previewSize) }

    fun searchAlbums(
        query: String,
        previewSize: Int = Int.MAX_VALUE,
    ): Flow<List<Album>> = allSavedAlbums().map { savedList ->
        val matchingSaved = savedList.filter {
            it.title.contains(query, ignoreCase = true) || (it.artistName?.contains(query, ignoreCase = true) == true)
        }.map { it.toAlbum() }.take(previewSize)

        if (matchingSaved.size < previewSize) {
            val remaining = previewSize - matchingSaved.size
            val ytAlbums = runCatching {
                com.ruben.spotitube.innertube.YouTube.search(query, com.ruben.spotitube.innertube.YouTube.SearchFilter.FILTER_ALBUM).getOrNull()?.items
                    ?.filterIsInstance<com.ruben.spotitube.innertube.models.AlbumItem>()
                    ?.map { item ->
                        val artistEntity = item.artists?.firstOrNull()?.let { a ->
                            SavedArtistEntity(id = a.id ?: "", name = a.name)
                        }
                        Album(
                            album = SavedAlbumEntity(
                                id = item.id,
                                title = item.title,
                                thumbnailUrl = item.thumbnail,
                                year = item.year,
                                songCount = 0,
                                duration = 0
                            ),
                            artists = if (artistEntity != null) listOf(artistEntity) else emptyList()
                        )
                    }?.take(remaining)
            }.getOrNull().orEmpty()

            (matchingSaved + ytAlbums).distinctBy { it.id }.take(previewSize)
        } else {
            matchingSaved
        }
    }.flowOn(Dispatchers.IO)

    fun searchPlaylists(
        query: String,
        previewSize: Int = Int.MAX_VALUE,
    ): Flow<List<Playlist>> = playlistsWithMetadata().map { list -> list.filter { it.playlist.name.contains(query, ignoreCase = true) }.take(previewSize) }

    fun firstEvent(): Flow<HistorySongEntity?> = allHistorySongs().map { it.lastOrNull() }

    fun clearListenHistory() {}

    @Transaction
    @Query("SELECT * FROM search_history WHERE `query` LIKE :query || '%' ORDER BY id DESC")
    fun searchHistory(query: String = ""): Flow<List<SearchHistory>>

    @Transaction
    @Query("DELETE FROM search_history")
    fun clearSearchHistory()

    fun incrementTotalPlayTime(songId: String, playTime: Long) {}

    @Query("UPDATE playCount SET count = count + 1 WHERE song = :songId AND year = :year AND month = :month")
    suspend fun incrementPlayCount(songId: String, year: Int, month: Int)

    /**
     * Increment by one the play count with today's year and month.
     */
    suspend fun incrementPlayCount(songId: String) {
        val time = LocalDateTime.now().atOffset(ZoneOffset.UTC)
        val oldCount = getPlayCountByMonth(songId, time.year, time.monthValue).first()

        // add new
        if (oldCount <= 0) {
            insert(PlayCountEntity(songId, time.year, time.monthValue, 0))
        }
        incrementPlayCount(songId, time.year, time.monthValue)
    }

    fun getRelatedSongs(songId: String): Flow<List<Song>> = allSavedSongs().map { list -> list.map { it.toSong() } }

    fun relatedSongs(songId: String): List<Song> = emptyList()

    fun artistByName(name: String): SavedArtistEntity? = kotlinx.coroutines.runBlocking {
        allSavedArtists().first().firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { SavedArtistEntity(id = it.id, name = it.name) }
    }

    suspend fun getArtistById(id: String): SavedArtistEntity? {
        val saved = getSavedArtist(id)
        return saved?.let { SavedArtistEntity(id = it.id, name = it.name, thumbnailUrl = it.thumbnailUrl, channelId = it.channelId) }
    }

    suspend fun getAlbumById(id: String): SavedAlbumEntity? {
        val saved = getSavedAlbum(id)
        return saved?.let { SavedAlbumEntity(id = it.id, title = it.title, songCount = it.songCount, duration = it.duration, thumbnailUrl = it.thumbnailUrl, year = it.year) }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(setVideoIdEntity: SetVideoIdEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(searchHistory: SearchHistory)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(map: RelatedSongMap)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(playCountEntity: PlayCountEntity): Long

    @Query("""
        UPDATE savedsong 
        SET title = :title,
            artistId = COALESCE(:artistId, artistId),
            artistName = COALESCE(:artistName, artistName),
            duration = CASE WHEN :duration > 0 THEN :duration ELSE duration END,
            thumbnailUrl = COALESCE(:thumbnailUrl, thumbnailUrl),
            albumId = COALESCE(:albumId, albumId),
            albumName = COALESCE(:albumName, albumName),
            explicit = :explicit,
            year = COALESCE(:year, year)
        WHERE id = :id
    """)
    fun updateSongMetadataOnly(
        id: String,
        title: String,
        artistId: String?,
        artistName: String?,
        duration: Int,
        thumbnailUrl: String?,
        albumId: String?,
        albumName: String?,
        explicit: Boolean,
        year: Int?
    )

    @Query("SELECT * FROM savedsong WHERE id = :songId")
    fun getSavedSongDirect(songId: String): SavedSongEntity?

    @Query("UPDATE savedsong SET isLiked = :isLiked WHERE id = :id")
    fun updateIsLiked(id: String, isLiked: Boolean)

    @Transaction
    fun insertOrUpdateDownload(
        mediaId: String,
        title: String,
        artistName: String?,
        duration: Int,
        thumbnailUrl: String?,
        downloadDate: LocalDateTime = LocalDateTime.now()
    ) {
        val existing = getSavedSongDirect(mediaId)
        if (existing != null) {
            updateDownloadDate(mediaId, downloadDate)
        } else {
            insertSavedSong(
                SavedSongEntity(
                    id = mediaId,
                    title = title,
                    artistName = artistName,
                    duration = duration,
                    thumbnailUrl = thumbnailUrl,
                    dateDownload = downloadDate,
                    isLiked = false
                )
            )
        }
    }

    @Transaction
    fun upsertSongPreservingUsage(song: SavedSongEntity) {
        val existing = getSavedSongDirect(song.id)
        if (existing == null) {
            insertSavedSong(song)
        } else {
            updateSongMetadataOnly(
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
        }
    }

    @Transaction
    fun insert(
        mediaMetadata: MediaMetadata,
    ) {
        upsertSongPreservingUsage(mediaMetadata.toSavedSongEntity())

        if (mediaMetadata.setVideoId != null) {
            insert(
                SetVideoIdEntity(
                    videoId = mediaMetadata.id,
                    setVideoId = mediaMetadata.setVideoId,
                ),
            )
        }
    }

    @Transaction
    fun insert(albumPage: AlbumPage) {
        val albumArtist = albumPage.album.artists?.firstOrNull()
        insertSavedAlbum(
            SavedAlbumEntity(
                id = albumPage.album.browseId,
                title = albumPage.album.title,
                artistId = albumArtist?.id,
                artistName = albumArtist?.name,
                songCount = albumPage.songs.size,
                duration = albumPage.songs.sumOf { song -> song.duration ?: 0 },
                thumbnailUrl = albumPage.album.thumbnail,
                year = albumPage.album.year,
            )
        )
    }

    @Transaction
    fun update(
        artist: SavedArtistEntity,
        artistPage: ArtistPage
    ) {
        insertSavedArtist(
            artist.copy(
                name = artistPage.artist.title,
                thumbnailUrl = artistPage.artist.thumbnail?.resize(544, 544),
                savedAt = LocalDateTime.now()
            )
        )
    }

    @Transaction
    fun update(
        album: SavedAlbumEntity,
        albumPage: AlbumPage
    ) {
        insertSavedAlbum(
            album.copy(
                id = albumPage.album.browseId,
                title = albumPage.album.title,
                year = albumPage.album.year,
                thumbnailUrl = albumPage.album.thumbnail,
                songCount = albumPage.songs.size,
                duration = albumPage.songs.sumOf { song -> song.duration ?: 0 }
            )
        )
    }

    @Upsert
    fun upsert(lyrics: LyricsEntity)

    @Upsert
    fun upsert(format: FormatEntity)

    suspend fun deletePlaylistById(playlistId: String) {
        deleteSavedPlaylist(playlistId)
    }

    @Delete
    fun delete(lyrics: LyricsEntity)

    @Delete
    fun delete(searchHistory: SearchHistory)

    @Query("DELETE FROM historialsong WHERE playedAt = :playedAt AND songId = :songId")
    fun deleteHistorySong(playedAt: Long, songId: String)

    @RawQuery
    fun raw(supportSQLiteQuery: SupportSQLiteQuery): Int

    fun checkpoint() {
        raw("PRAGMA wal_checkpoint(FULL)".toSQLiteQuery())
    }

    @Transaction
    @Query("SELECT * FROM tag ORDER BY name")
    fun allTags(): Flow<List<TagEntity>>

    @Transaction
    @Query("SELECT * FROM tag WHERE id = :tagId")
    fun tag(tagId: String): Flow<TagEntity?>

    @Transaction
    @Query("SELECT * FROM tag WHERE id IN (SELECT tagId FROM playlist_tag_map WHERE playlistId = :playlistId)")
    fun playlistTags(playlistId: String): Flow<List<TagEntity>>

    @Transaction
    @Query("SELECT DISTINCT playlistId FROM playlist_tag_map WHERE tagId IN (:tagIds)")
    fun playlistIdsByTags(tagIds: List<String>): Flow<List<String>>

    @Transaction
    @Query("SELECT COUNT(*) FROM playlist_tag_map WHERE playlistId = :playlistId AND tagId = :tagId")
    fun isPlaylistTagged(playlistId: String, tagId: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(tag: TagEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(map: PlaylistTagMap)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertAllPlaylistTagMaps(maps: List<PlaylistTagMap>)

    @Update
    fun update(tag: TagEntity)

    @Update
    fun update(playlist: PlaylistEntity)

    @Delete
    fun delete(tag: TagEntity)

    @Delete
    fun delete(playlistTagMap: PlaylistTagMap)

    @Query("DELETE FROM playlist_tag_map WHERE playlistId = :playlistId")
    fun removeAllPlaylistTags(playlistId: String)

    @Query("DELETE FROM playlist_tag_map WHERE tagId = :tagId")
    fun removeAllTagPlaylists(tagId: String)

    @Query("DELETE FROM playlist_tag_map WHERE playlistId = :playlistId AND tagId = :tagId")
    fun removePlaylistTag(playlistId: String, tagId: String)

    @Transaction
    fun addTagToPlaylist(playlistId: String, tagId: String) {
        insert(PlaylistTagMap(playlistId = playlistId, tagId = tagId))
    }

    @Transaction
    fun addTagsToPlaylists(
        playlistIds: List<String>,
        tagIds: List<String>,
    ) {
        if (playlistIds.isEmpty() || tagIds.isEmpty()) return
        val maps =
            playlistIds.flatMap { playlistId ->
                tagIds.map { tagId ->
                    PlaylistTagMap(playlistId = playlistId, tagId = tagId)
                }
            }
        insertAllPlaylistTagMaps(maps)
    }

    @Transaction
    suspend fun togglePlaylistTag(playlistId: String, tagId: String) {
        val isTagged = isPlaylistTagged(playlistId, tagId).first()
        if (isTagged > 0) {
            removePlaylistTag(playlistId, tagId)
        } else {
            addTagToPlaylist(playlistId, tagId)
        }
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(map: PlaylistSongMap): Long

    @Update
    fun update(map: PlaylistSongMap)

    @Delete
    fun delete(map: PlaylistSongMap)

    @Query("SELECT * FROM playlist_song_map WHERE playlistId = :playlistId AND position >= :minPosition ORDER BY position")
    fun playlistSongMaps(playlistId: String, minPosition: Int = 0): List<PlaylistSongMap>

    @Query("DELETE FROM playlist_song_map WHERE playlistId = :playlistId")
    fun clearPlaylist(playlistId: String)

    @Transaction
    fun move(playlistId: String, from: Int, to: Int) {
        if (from == to) return
        val start = minOf(from, to)
        val items = playlistSongMaps(playlistId, start).toMutableList()
        val moving = items.firstOrNull { it.position == from } ?: return

        if (from < to) {
            items.filter { it.position in (from + 1)..to }.forEach {
                update(it.copy(position = it.position - 1))
            }
        } else {
            items.filter { it.position in to until from }.forEach {
                update(it.copy(position = it.position + 1))
            }
        }
        update(moving.copy(position = to))
    }

    // --- Saved (Biblioteca) ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSavedSong(song: SavedSongEntity)

    @Query("UPDATE savedsong SET dateDownload = :downloadDate WHERE id = :songId")
    fun updateDownloadDate(songId: String, downloadDate: LocalDateTime = LocalDateTime.now())

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSavedSongs(songs: List<SavedSongEntity>)

    @Query("DELETE FROM savedsong WHERE id = :songId")
    fun deleteSavedSong(songId: String)

    @Query("SELECT * FROM savedsong WHERE id = :songId")
    suspend fun getSavedSong(songId: String): SavedSongEntity?

    @Query("SELECT * FROM savedsong ORDER BY savedAt DESC")
    fun allSavedSongs(): Flow<List<SavedSongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSavedArtist(artist: SavedArtistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSavedArtists(artists: List<SavedArtistEntity>)

    @Query("DELETE FROM savedartist WHERE id = :artistId")
    fun deleteSavedArtist(artistId: String)

    @Query("SELECT * FROM savedartist WHERE id = :artistId")
    suspend fun getSavedArtist(artistId: String): SavedArtistEntity?

    @Query("SELECT * FROM savedartist ORDER BY savedAt DESC")
    fun allSavedArtists(): Flow<List<SavedArtistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSavedAlbum(album: SavedAlbumEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSavedAlbums(albums: List<SavedAlbumEntity>)

    @Query("DELETE FROM savedalbum WHERE id = :albumId")
    fun deleteSavedAlbum(albumId: String)

    @Query("SELECT * FROM savedalbum WHERE id = :albumId")
    suspend fun getSavedAlbum(albumId: String): SavedAlbumEntity?

    @Query("SELECT * FROM savedalbum ORDER BY savedAt DESC")
    fun allSavedAlbums(): Flow<List<SavedAlbumEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSavedPlaylist(playlist: SavedPlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSavedPlaylists(playlists: List<SavedPlaylistEntity>)

    @Query("DELETE FROM savedplaylist WHERE id = :playlistId OR browseId = :playlistId OR id = REPLACE(:playlistId, 'VL', '') OR browseId = REPLACE(:playlistId, 'VL', '') OR id = 'VL' || REPLACE(:playlistId, 'VL', '') OR browseId = 'VL' || REPLACE(:playlistId, 'VL', '')")
    fun deleteSavedPlaylist(playlistId: String)

    @Query("SELECT * FROM savedplaylist WHERE id = :playlistId OR browseId = :playlistId OR id = REPLACE(:playlistId, 'VL', '') OR browseId = REPLACE(:playlistId, 'VL', '') OR id = 'VL' || REPLACE(:playlistId, 'VL', '') OR browseId = 'VL' || REPLACE(:playlistId, 'VL', '') LIMIT 1")
    suspend fun getSavedPlaylist(playlistId: String): SavedPlaylistEntity?

    @Query("SELECT * FROM savedplaylist ORDER BY createdAt DESC")
    fun allSavedPlaylists(): Flow<List<SavedPlaylistEntity>>

    // --- Historial ---
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertHistorySong(historySong: HistorySongEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertHistoryArtist(historyArtist: HistoryArtistEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertHistoryAlbum(historyAlbum: HistoryAlbumEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertHistoryPlaylist(historyPlaylist: HistoryPlaylistEntity)

    @Query("SELECT * FROM historialsong ORDER BY playedAt DESC")
    fun allHistorySongs(): Flow<List<HistorySongEntity>>

    @Query("SELECT * FROM historialartist ORDER BY playedAt DESC")
    fun allHistoryArtists(): Flow<List<HistoryArtistEntity>>

    @Query("SELECT * FROM historialalbum ORDER BY playedAt DESC")
    fun allHistoryAlbums(): Flow<List<HistoryAlbumEntity>>

    @Query("SELECT * FROM historialplaylist ORDER BY playedAt DESC")
    fun allHistoryPlaylists(): Flow<List<HistoryPlaylistEntity>>
}

