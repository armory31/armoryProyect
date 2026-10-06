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
import androidx.room.RoomWarnings
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
import com.ruben.spotitube.db.entities.AlbumEntity
import com.ruben.spotitube.db.entities.AlbumWithSongs
import com.ruben.spotitube.db.entities.Artist
import com.ruben.spotitube.db.entities.ArtistEntity
import com.ruben.spotitube.db.entities.Event
import com.ruben.spotitube.db.entities.EventWithSong
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
import com.ruben.spotitube.db.entities.SongAlbumMap
import com.ruben.spotitube.db.entities.HistoryAlbumEntity
import com.ruben.spotitube.db.entities.HistoryArtistEntity
import com.ruben.spotitube.db.entities.HistoryPlaylistEntity
import com.ruben.spotitube.db.entities.HistorySongEntity
import com.ruben.spotitube.db.entities.SavedAlbumEntity
import com.ruben.spotitube.db.entities.SavedArtistEntity
import com.ruben.spotitube.db.entities.SavedPlaylistEntity
import com.ruben.spotitube.db.entities.SavedSongEntity
import com.ruben.spotitube.db.entities.SongArtistMap
import com.ruben.spotitube.db.entities.SongEntity
import com.ruben.spotitube.db.entities.SongWithStats
import com.ruben.spotitube.db.entities.TagEntity
import com.ruben.spotitube.extensions.reversed
import com.ruben.spotitube.extensions.toSQLiteQuery
import com.ruben.spotitube.innertube.models.PlaylistItem
import com.ruben.spotitube.innertube.models.SongItem
import com.ruben.spotitube.innertube.pages.AlbumPage
import com.ruben.spotitube.innertube.pages.ArtistPage
import com.ruben.spotitube.models.MediaMetadata
import com.ruben.spotitube.models.toMediaMetadata
import com.ruben.spotitube.sync.LibrarySummary
import com.ruben.spotitube.ui.utils.resize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
    @Query("DELETE FROM song WHERE isLocal = 0")
    suspend fun clearNonLocalSongs()

    @Query("DELETE FROM artist WHERE isLocal = 0")
    suspend fun clearNonLocalArtists()

    @Query("DELETE FROM album WHERE isLocal = 0")
    suspend fun clearNonLocalAlbums()

    @Query("DELETE FROM playlist WHERE isLocal = 0")
    suspend fun clearNonLocalPlaylists()

    @Query("DELETE FROM song_artist_map")
    suspend fun clearSongArtistMaps()

    @Query("DELETE FROM song_album_map")
    suspend fun clearSongAlbumMaps()

    @Transaction
    suspend fun clearUserData() {
        clearNonLocalSongs()
        clearNonLocalArtists()
        clearNonLocalAlbums()
        clearNonLocalPlaylists()
        clearSongArtistMaps()
        clearSongAlbumMaps()
    }

    @Transaction
    @Query("SELECT * FROM song ORDER BY rowId ASC")
    fun songsByRowIdAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song ORDER BY title ASC")
    fun songsByNameAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song ORDER BY totalPlayTime ASC")
    fun songsByPlayTimeAsc(): Flow<List<Song>>

    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Transaction
    @Query("SELECT * FROM album ORDER BY rowId ASC")
    fun allAlbumsByRowIdAsc(): Flow<List<Album>>

    @Transaction
    @Query("SELECT *, (SELECT COUNT(1) FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE artistId = artist.id) AS songCount, 0 AS timeListened FROM artist ORDER BY name ASC")
    fun allArtistsByNameAsc(): Flow<List<Artist>>

    @Transaction
    @Query("""
        SELECT p.*, 
            COALESCE((SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = p.id OR (p.browseId IS NOT NULL AND playlistId = p.browseId)), 0) AS songCount
        FROM playlist p 
        WHERE p.browseId IS NULL OR p.isAutoSync = 1 
        ORDER BY p.createdAt ASC
    """)
    fun editablePlaylistsByCreateDateAsc(): Flow<List<Playlist>>

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

    @Transaction
    @Query(
        """
        SELECT song.*
        FROM song
        LEFT JOIN set_video_id ON set_video_id.videoId = song.id
        WHERE set_video_id.setVideoId IS NULL
        ORDER BY song.id
        """,
    )
    fun songsByRowIdAscNoVideo(): Flow<List<Song>>

    @Transaction
    @Query(
        """
        SELECT song.*
        FROM song
        LEFT JOIN set_video_id ON set_video_id.videoId = song.id
        WHERE set_video_id.setVideoId IS NULL
        ORDER BY title
        """,
    )
    fun songsByNameAscNoVideo(): Flow<List<Song>>

    @Transaction
    @Query(
        """
        SELECT song.*
        FROM song
        LEFT JOIN set_video_id ON set_video_id.videoId = song.id
        WHERE set_video_id.setVideoId IS NULL
        ORDER BY totalPlayTime
        """,
    )
    fun songsByPlayTimeAscNoVideo(): Flow<List<Song>>

    @Transaction
    @Query("SELECT song.* FROM song JOIN song_album_map ON song.id = song_album_map.songId WHERE song_album_map.albumId = :albumId")
    fun albumSongs(albumId: String): Flow<List<Song>>

    @Transaction
    @Query(
        "SELECT song.* FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE artistId = :artistId",
    )
    fun artistSongsByRowIdAsc(artistId: String): Flow<List<Song>>

    @Transaction
    @Query(
        "SELECT song.* FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE artistId = :artistId ORDER BY title",
    )
    fun artistSongsByNameAsc(artistId: String): Flow<List<Song>>

    @Transaction
    @Query(
        "SELECT song.* FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE artistId = :artistId ORDER BY totalPlayTime",
    )
    fun artistSongsByPlayTimeAsc(artistId: String): Flow<List<Song>>

    fun artistSongs(
        artistId: String,
        sortType: ArtistSongSortType,
        descending: Boolean,
    ) = when (sortType) {
        ArtistSongSortType.CREATE_DATE -> artistSongsByRowIdAsc(artistId)
        ArtistSongSortType.NAME ->
            artistSongsByNameAsc(artistId).map { artistSongs ->
                val collator = Collator.getInstance(Locale.getDefault())
                collator.strength = Collator.PRIMARY
                artistSongs.sortedWith(compareBy(collator) { it.song.title })
            }

        ArtistSongSortType.PLAY_TIME -> artistSongsByPlayTimeAsc(artistId)
    }.map { it.reversed(descending) }

    @Transaction
    @Query(
        "SELECT song.* FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE artistId = :artistId LIMIT :previewSize",
    )
    fun artistSongsPreview(
        artistId: String,
        previewSize: Int = 3,
    ): Flow<List<Song>>

    @Transaction
    @Query(
        """
        SELECT song.*
        FROM (SELECT *, COUNT(1) AS referredCount
              FROM related_song_map
              GROUP BY relatedSongId) map
                 JOIN song ON song.id = map.relatedSongId
        WHERE songId IN (SELECT songId
                         FROM (SELECT songId
                               FROM event
                               ORDER BY ROWID DESC
                               LIMIT 5)
                         UNION
                         SELECT songId
                         FROM (SELECT songId
                               FROM event
                               WHERE timestamp > :now - 86400000 * 7
                               GROUP BY songId
                               ORDER BY SUM(playTime) DESC
                               LIMIT 5)
                         UNION
                         SELECT id
                         FROM (SELECT id
                               FROM song
                               ORDER BY totalPlayTime DESC
                               LIMIT 10))
        ORDER BY referredCount DESC
        LIMIT 100
    """,
    )
    fun quickPicks(now: Long = System.currentTimeMillis()): Flow<List<Song>>

    @Transaction
    @Query(
        """
        SELECT
            song.*
        FROM
            event
        JOIN
            song ON event.songId = song.id
        WHERE
            event.timestamp > (:now - 86400000 * 7 * 2)
        GROUP BY
            song.albumId
        HAVING
            song.albumId IS NOT NULL
        ORDER BY
            sum(event.playTime) DESC
        LIMIT :limit
        OFFSET :offset
        
        """,
    )
    fun getRecommendationAlbum(
        now: Long = System.currentTimeMillis(),
        limit: Int = 5,
        offset: Int = 0,
    ): Flow<List<Song>>

    @Transaction
    @Query(
        """
             SELECT song.id, song.title, song.thumbnailUrl,
               (SELECT COUNT(1)
                FROM event
                WHERE songId = song.id
                  AND timestamp > :fromTimeStamp AND timestamp <= :toTimeStamp) AS songCountListened,
               (SELECT SUM(event.playTime)
                FROM event
                WHERE songId = song.id
                  AND timestamp > :fromTimeStamp AND timestamp <= :toTimeStamp) AS timeListened
        FROM song
        JOIN (SELECT songId
                     FROM event
                     WHERE timestamp > :fromTimeStamp
                     AND timestamp <= :toTimeStamp
                     GROUP BY songId
                     ORDER BY SUM(playTime) DESC
                     LIMIT :limit)
        ON song.id = songId
        LIMIT :limit
        OFFSET :offset
    """,
    )
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
            .take(limit)

        sorted.mapNotNull { (songId, stats) ->
            val savedSong = getSavedSong(songId)
            val songObj = savedSong?.toSong()
                ?: getSongById(songId)
                ?: Song(song = SongEntity(id = songId, title = songId), artists = emptyList())

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
            .take(limit)

        sorted.mapNotNull { (songId, _) ->
            val savedSong = getSavedSong(songId)
            savedSong?.toSong()
                ?: getSongById(songId)
                ?: Song(song = SongEntity(id = songId, title = songId), artists = emptyList())
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
            .take(limit)

        sorted.mapNotNull { (artistId, count) ->
            val savedArtist = getSavedArtist(artistId)
            val artistObj = savedArtist?.toArtist()
                ?: getArtistById(artistId)?.let { Artist(artist = it, songCount = count) }
                ?: Artist(artist = ArtistEntity(id = artistId, name = artistId), songCount = count)

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
            .take(limit)

        sorted.mapNotNull { (albumId, count) ->
            val savedAlbum = getSavedAlbum(albumId)
            val albumObj = savedAlbum?.toAlbum()
                ?: getAlbumById(albumId)?.let { Album(album = it, artists = emptyList(), songCountListened = count) }
                ?: Album(album = AlbumEntity(id = albumId, title = albumId, songCount = count, duration = 0), artists = emptyList(), songCountListened = count)

            albumObj.copy(songCountListened = count)
        }
    }.flowOn(Dispatchers.IO)

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("""
        SELECT album.*, count(song.dateDownload) downloadCount
        FROM album_artist_map 
            JOIN album ON album_artist_map.albumId = album.id
            JOIN song ON album_artist_map.albumId = song.albumId
        WHERE artistId = :artistId
        GROUP BY album.id
        LIMIT :previewSize
    """)
    fun artistAlbumsPreview(artistId: String, previewSize: Int = 6): Flow<List<Album>>

    @Query("SELECT sum(count) from playCount WHERE song = :songId")
    fun getLifetimePlayCount(songId: String?): Flow<Int>
    @Query("SELECT sum(count) from playCount WHERE song = :songId AND year = :year")
    fun getPlayCountByYear(songId: String?, year: Int): Flow<Int>
    @Query("SELECT count from playCount WHERE song = :songId AND year = :year AND month = :month")
    fun getPlayCountByMonth(songId: String?, year: Int, month: Int): Flow<Int>

    @Transaction
    @Query(
        """
        SELECT song.*
        FROM (SELECT n.songId      AS eid,
                     SUM(playTime) AS oldPlayTime,
                     newPlayTime
              FROM event
                       JOIN
                   (SELECT songId, SUM(playTime) AS newPlayTime
                    FROM event
                    WHERE timestamp > (:now - 86400000 * 30 * 1)
                    GROUP BY songId
                    ORDER BY newPlayTime) as n
                   ON event.songId = n.songId
              WHERE timestamp < (:now - 86400000 * 30 * 1)
              GROUP BY n.songId
              ORDER BY oldPlayTime) AS t
                 JOIN song on song.id = t.eid
        WHERE 0.2 * t.oldPlayTime > t.newPlayTime
        LIMIT 100
    """
    )
    fun forgottenFavorites(now: Long = System.currentTimeMillis()): Flow<List<Song>>

    @Transaction
    @Query(
        """
        SELECT song.*
        FROM event
                 JOIN
             song ON event.songId = song.id
        WHERE event.timestamp > (:now - 86400000 * 7 * 2)
        GROUP BY song.albumId
        HAVING song.albumId IS NOT NULL
        ORDER BY sum(event.playTime) DESC
        LIMIT :limit
        OFFSET :offset
        """,
    )
    fun recommendedAlbum(
        now: Long = System.currentTimeMillis(),
        limit: Int = 5,
        offset: Int = 0,
    ): Flow<List<Song>>

    @Query("SELECT EXISTS(SELECT 1 FROM related_song_map WHERE songId = :songId LIMIT 1)")
    fun hasRelatedSongs(songId: String): Flow<Boolean>

    @Transaction
    @Query("SELECT * FROM song WHERE id = :songId")
    fun song(songId: String?): Flow<Song?>

    @Transaction
    @Query("SELECT * FROM song WHERE id = :songId LIMIT 1")
    suspend fun getSongById(songId: String): Song?

    @Transaction
    @Query("SELECT * FROM song WHERE id = :songId LIMIT 1")
    fun getSongByIdBlocking(songId: String): Song?

    @Transaction
    @Query("SELECT * FROM song WHERE id IN (:songIds)")
    suspend fun getSongsByIds(songIds: List<String>): List<Song>

    
    @Transaction
    @Query("SELECT * FROM song_artist_map WHERE songId = :songId")
    fun songArtistMap(songId: String): List<SongArtistMap>

    @Transaction
    @Query("SELECT * FROM song")
    fun allSongs(): Flow<List<Song>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query(
        """
        SELECT DISTINCT artist.*,
               (SELECT COUNT(1)
                FROM song_artist_map
                         JOIN event ON song_artist_map.songId = event.songId
                WHERE artistId = artist.id) AS songCount
        FROM artist
                 LEFT JOIN(SELECT artistId, SUM(songTotalPlayTime) AS totalPlayTime
                      FROM song_artist_map
                               JOIN (SELECT songId, SUM(playTime) AS songTotalPlayTime
                                     FROM event
                                     GROUP BY songId) AS e
                                    ON song_artist_map.songId = e.songId
                      GROUP BY artistId
                      ORDER BY totalPlayTime DESC) AS artistTotalPlayTime
                     ON artist.id = artistId
                     ORDER BY 
                      CASE 
                        WHEN artistTotalPlayTime.artistId IS NULL THEN 1 
                        ELSE 0 
                      END, 
                      artistTotalPlayTime.totalPlayTime DESC
    """,
    )
    fun allArtistsByPlayTime(): Flow<List<Artist>>

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

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT *, (SELECT COUNT(1) FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE artistId = artist.id) AS songCount FROM artist ORDER BY rowId")
    fun artistsByRowIdAsc(): Flow<List<Artist>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT *, (SELECT COUNT(1) FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE artistId = artist.id) AS songCount FROM artist ORDER BY name")
    fun artistsByNameAsc(): Flow<List<Artist>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT *, (SELECT COUNT(1) FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE artistId = artist.id) AS songCount FROM artist ORDER BY songCount")
    fun artistsBySongCountAsc(): Flow<List<Artist>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query(
        """
        SELECT artist.*,
               (SELECT COUNT(1)
                FROM song_artist_map
                         JOIN song ON song_artist_map.songId = song.id
                WHERE artistId = artist.id) AS songCount
        FROM artist
                 JOIN(SELECT artistId, SUM(totalPlayTime) AS totalPlayTime
                      FROM song_artist_map
                               JOIN song
                                    ON song_artist_map.songId = song.id
                      GROUP BY artistId
                      ORDER BY totalPlayTime)
                     ON artist.id = artistId
    """
    )
    fun artistsByPlayTimeAsc(): Flow<List<Artist>>

    fun artists(sortType: ArtistSortType, descending: Boolean) =
        when (sortType) {
            ArtistSortType.CREATE_DATE -> artistsByRowIdAsc()
            ArtistSortType.NAME -> artistsByNameAsc()
            ArtistSortType.SONG_COUNT -> artistsBySongCountAsc()
            ArtistSortType.PLAY_TIME -> artistsByPlayTimeAsc()
        }.map { artists ->
            artists
                .filter { it.artist.isYouTubeArtist || it.artist.isLocal }
                .reversed(descending)
        }

    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT *, (SELECT COUNT(1) FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE artistId = artist.id) AS songCount FROM artist WHERE id = :id")
    fun artist(id: String): Flow<Artist?>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT * FROM album ORDER BY rowId")
    fun albumsByRowIdAsc(): Flow<List<Album>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT * FROM album ORDER BY title")
    fun albumsByNameAsc(): Flow<List<Album>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT * FROM album ORDER BY year")
    fun albumsByYearAsc(): Flow<List<Album>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT * FROM album ORDER BY songCount")
    fun albumsBySongCountAsc(): Flow<List<Album>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT * FROM album ORDER BY duration")
    fun albumsByLengthAsc(): Flow<List<Album>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query(
        """
        SELECT album.*
        FROM album
                 JOIN song
                      ON song.albumId = album.id
        GROUP BY album.id
        ORDER BY SUM(song.totalPlayTime)
    """,
    )
    fun albumsByPlayTimeAsc(): Flow<List<Album>>

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

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT * FROM album WHERE id = :id")
    fun album(id: String): Flow<Album?>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT * FROM album WHERE id IN (:ids) ORDER BY rowId")
    fun albumsByRowId(ids: Collection<String>): Flow<List<Album>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT * FROM album WHERE id IN (:ids) ORDER BY title")
    fun albumsByTitle(ids: Collection<String>): Flow<List<Album>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT * FROM album WHERE id IN (:ids) ORDER BY year")
    fun albumsByYear(ids: Collection<String>): Flow<List<Album>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT * FROM album WHERE id IN (:ids) ORDER BY songCount")
    fun albumsBySongCount(ids: Collection<String>): Flow<List<Album>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT * FROM album WHERE id IN (:ids) ORDER BY duration")
    fun albumsByDuration(ids: Collection<String>): Flow<List<Album>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query(
        """
        SELECT album.*
        FROM album
        LEFT JOIN song ON song.albumId = album.id
        WHERE album.id IN (:ids)
        GROUP BY album.id
        ORDER BY COALESCE(SUM(song.totalPlayTime), 0)
        """
    )
    fun albumsByPlayTime(ids: Collection<String>): Flow<List<Album>>

    fun albumsByIds(
        ids: Collection<String>,
        sortType: AlbumSortType,
        descending: Boolean,
    ): Flow<List<Album>> {
        if (ids.isEmpty()) return kotlinx.coroutines.flow.flowOf(emptyList())
        return when (sortType) {
            AlbumSortType.CREATE_DATE -> albumsByRowId(ids)
            AlbumSortType.NAME ->
                albumsByTitle(ids).map { albums ->
                    val collator = Collator.getInstance(Locale.getDefault())
                    collator.strength = Collator.PRIMARY
                    albums.sortedWith(compareBy(collator) { it.album.title })
                }
            AlbumSortType.ARTIST ->
                albumsByRowId(ids).map { albums ->
                    val collator = Collator.getInstance(Locale.getDefault())
                    collator.strength = Collator.PRIMARY
                    albums.sortedWith(compareBy(collator) { album -> album.artists.joinToString("") { artist -> artist.name } })
                }
            AlbumSortType.YEAR -> albumsByYear(ids)
            AlbumSortType.SONG_COUNT -> albumsBySongCount(ids)
            AlbumSortType.LENGTH -> albumsByDuration(ids)
            AlbumSortType.PLAY_TIME -> albumsByPlayTime(ids)
        }.map { it.reversed(descending) }
    }

    @Transaction
    @Query("SELECT * FROM album WHERE id = :albumId")
    fun albumWithSongs(albumId: String): Flow<AlbumWithSongs?>

    @Transaction
    @Query("SELECT * FROM album_artist_map WHERE albumId = :albumId")
    fun albumArtistMaps(albumId: String): List<AlbumArtistMap>

    @Transaction
    @Query("""
        SELECT p.*, 
            COALESCE((SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = p.id OR (p.browseId IS NOT NULL AND playlistId = p.browseId)), 0) AS songCount
        FROM playlist p 
        ORDER BY p.rowId
    """)
    fun playlistsByRowIdAsc(): Flow<List<Playlist>>

    @Transaction
    @Query("""
        SELECT p.*, 
            COALESCE((SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = p.id OR (p.browseId IS NOT NULL AND playlistId = p.browseId)), 0) AS songCount
        FROM playlist p 
        ORDER BY p.lastUpdateTime
    """)
    fun playlistsByUpdatedDateAsc(): Flow<List<Playlist>>

    @Transaction
    @Query("""
        SELECT p.*, 
            COALESCE((SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = p.id OR (p.browseId IS NOT NULL AND playlistId = p.browseId)), 0) AS songCount
        FROM playlist p 
        ORDER BY p.name
    """)
    fun playlistsByNameAsc(): Flow<List<Playlist>>

    @Transaction
    @Query("""
        SELECT p.*, 
            COALESCE((SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = p.id OR (p.browseId IS NOT NULL AND playlistId = p.browseId)), 0) AS songCount
        FROM playlist p 
        ORDER BY p.customOrder ASC
    """)
    fun playlistsByCustomOrderAsc(): Flow<List<Playlist>>

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

    @Query("UPDATE playlist SET customOrder = :customOrder WHERE id = :playlistId")
    suspend fun setPlaylistCustomOrder(playlistId: String, customOrder: Int)

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

    @Transaction
    @Query("SELECT * FROM song WHERE isLiked = 1 OR id IN (SELECT id FROM savedsong) OR id IN (SELECT videoId FROM set_video_id)")
    fun likedSongsByRowIdAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT *, 0 AS songCountListened, 0 AS timeListened FROM album WHERE isLiked = 1 OR id IN (SELECT id FROM savedalbum)")
    fun albumsLikedInternal(): Flow<List<Album>>

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

    @Transaction
    @Query("SELECT *, (SELECT COUNT(1) FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE artistId = artist.id) AS songCount, 0 AS timeListened FROM artist WHERE isBookmarked = 1 OR id IN (SELECT id FROM savedartist)")
    fun artistsBookmarkedInternal(): Flow<List<Artist>>

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

    @Query("SELECT id FROM savedsong UNION SELECT videoId FROM set_video_id")
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

    @Query("SELECT * FROM playlist_song_map WHERE playlistId = :playlistId OR playlistId = (SELECT browseId FROM playlist WHERE id = :playlistId) OR playlistId = (SELECT id FROM playlist WHERE browseId = :playlistId) OR playlistId = (SELECT browseId FROM savedplaylist WHERE id = :playlistId) OR playlistId = (SELECT id FROM savedplaylist WHERE browseId = :playlistId) OR playlistId = REPLACE(:playlistId, 'VL', '') OR playlistId = 'VL' || REPLACE(:playlistId, 'VL', '') ORDER BY position ASC")
    fun rawPlaylistSongMaps(playlistId: String): Flow<List<PlaylistSongMap>>

    fun playlistSongs(playlistId: String): Flow<List<PlaylistSong>> = rawPlaylistSongMaps(playlistId).map { mapList ->
        mapList.mapNotNull { pMap ->
            val savedSong = getSavedSong(pMap.songId)
            val songObj = savedSong?.toSong()
                ?: getSongById(pMap.songId)
                ?: Song(song = SongEntity(id = pMap.songId, title = pMap.songId), artists = emptyList())

            PlaylistSong(
                map = pMap,
                song = songObj
            )
        }
    }.flowOn(Dispatchers.IO)

    @Transaction
    @Query("""
        SELECT p.*, 
            COALESCE((SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = p.id OR (p.browseId IS NOT NULL AND playlistId = p.browseId)), 0) AS songCount
        FROM playlist p 
        WHERE p.id = :id
    """)
    fun playlist(id: String): Flow<Playlist?>

    @Transaction
    @Query("""
        SELECT p.*, 
            COALESCE((SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = p.id OR (p.browseId IS NOT NULL AND playlistId = p.browseId)), 0) AS songCount
        FROM playlist p 
        WHERE p.id = :playlistId 
        LIMIT 1
    """)
    suspend fun getPlaylistById(playlistId: String): Playlist?

    @Transaction
    @Query("""
        SELECT p.*, 
            COALESCE((SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = p.id OR (p.browseId IS NOT NULL AND playlistId = p.browseId)), 0) AS songCount
        FROM playlist p 
        WHERE p.id = :playlistId 
        LIMIT 1
    """)
    fun getPlaylistByIdBlocking(playlistId: String): Playlist?

    @Transaction
    @Query("""
        SELECT p.*, 
            COALESCE((SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = p.id OR (p.browseId IS NOT NULL AND playlistId = p.browseId)), 0) AS songCount
        FROM playlist p 
        WHERE p.browseId = :browseId
    """)
    fun playlistByBrowseId(browseId: String): Flow<Playlist?>

    @Transaction
    @Query("SELECT * FROM song WHERE title LIKE '%' || :query || '%' LIMIT :previewSize")
    fun searchSongs(
        query: String,
        previewSize: Int = Int.MAX_VALUE,
    ): Flow<List<Song>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query(
        "SELECT *, (SELECT COUNT(1) FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE artistId = artist.id) AS songCount FROM artist WHERE name LIKE '%' || :query || '%' LIMIT :previewSize",
    )
    fun searchArtists(
        query: String,
        previewSize: Int = Int.MAX_VALUE,
    ): Flow<List<Artist>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query(
        "SELECT * FROM album WHERE title LIKE '%' || :query || '%' LIMIT :previewSize",
    )
    fun searchAlbums(
        query: String,
        previewSize: Int = Int.MAX_VALUE,
    ): Flow<List<Album>>

    @Transaction
    @Query("""
        SELECT p.*, 
            COALESCE((SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = p.id), 0) AS songCount
        FROM playlist p 
        WHERE p.name LIKE '%' || :query || '%' 
        LIMIT :previewSize
    """)
    fun searchPlaylists(
        query: String,
        previewSize: Int = Int.MAX_VALUE,
    ): Flow<List<Playlist>>

    @Transaction
    @Query("SELECT * FROM event ORDER BY rowId DESC")
    fun events(): Flow<List<EventWithSong>>

    @Transaction
    @Query("SELECT * FROM event ORDER BY rowId ASC LIMIT 1")
    fun firstEvent(): Flow<EventWithSong?>

    @Transaction
    @Query("DELETE FROM event")
    fun clearListenHistory()

    @Transaction
    @Query("SELECT * FROM search_history WHERE `query` LIKE :query || '%' ORDER BY id DESC")
    fun searchHistory(query: String = ""): Flow<List<SearchHistory>>

    @Transaction
    @Query("DELETE FROM search_history")
    fun clearSearchHistory()

    @Query("UPDATE song SET totalPlayTime = totalPlayTime + :playTime WHERE id = :songId")
    fun incrementTotalPlayTime(songId: String, playTime: Long)

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

    @Transaction
    @Query(
        "SELECT song.* FROM (SELECT * from related_song_map GROUP BY relatedSongId) map JOIN song ON song.id = map.relatedSongId where songId = :songId",
    )
    fun getRelatedSongs(songId: String): Flow<List<Song>>

    @Transaction
    @Query(
        """
        SELECT song.*
        FROM (SELECT *
              FROM related_song_map
              GROUP BY relatedSongId) map
                 JOIN
             song
             ON song.id = map.relatedSongId
        WHERE songId = :songId
        """
    )
    fun relatedSongs(songId: String): List<Song>

    @Transaction
    @Query("SELECT * FROM artist WHERE name = :name")
    fun artistByName(name: String): ArtistEntity?

    @Query("SELECT * FROM artist WHERE id = :id LIMIT 1")
    suspend fun getArtistById(id: String): ArtistEntity?

    @Query("SELECT * FROM album WHERE id = :id LIMIT 1")
    suspend fun getAlbumById(id: String): AlbumEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(song: SongEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(artist: ArtistEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(album: AlbumEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(playlist: PlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(map: SongArtistMap)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(map: SongAlbumMap)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(map: AlbumArtistMap)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(setVideoIdEntity: SetVideoIdEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(searchHistory: SearchHistory)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(event: Event)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(map: RelatedSongMap)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(playCountEntity: PlayCountEntity): Long

    @Transaction
    fun insert(
        mediaMetadata: MediaMetadata,
        block: (SongEntity) -> SongEntity = { it },
    ) {
        if (insert(mediaMetadata.toSongEntity().let(block)) == -1L) return

        if (mediaMetadata.setVideoId != null) {
            insert(
                SetVideoIdEntity(
                    videoId = mediaMetadata.id,
                    setVideoId = mediaMetadata.setVideoId,
                ),
            )
        }

        mediaMetadata.artists.forEachIndexed { index, artist ->
            val artistId = artist.id ?: artistByName(artist.name)?.id ?: ArtistEntity.generateArtistId()
            
            insert(
                ArtistEntity(
                    id = artistId,
                    name = artist.name,
                    channelId = artist.id,
                )
            )

            insert(
                SongArtistMap(
                    songId = mediaMetadata.id,
                    artistId = artistId,
                    position = index,
                )
            )
        }
    }

    @Transaction
    fun insert(albumPage: AlbumPage) {
        if (insert(
                AlbumEntity(
                    id = albumPage.album.browseId,
                    playlistId = albumPage.album.playlistId,
                    title = albumPage.album.title,
                    year = albumPage.album.year,
                    thumbnailUrl = albumPage.album.thumbnail,
                    songCount = albumPage.songs.size,
                    duration = albumPage.songs.sumOf { song -> song.duration ?: 0 },
                    explicit = albumPage.album.explicit || albumPage.songs.any { it.explicit },
                ),
            ) == -1L
        ) {
            return
        }
        albumPage.songs
            .map(SongItem::toMediaMetadata)
            .onEach(::insert)
            .onEach {
                val existingSong = getSongByIdBlocking(it.id)
                if (existingSong != null) {
                    update(existingSong, it)
                }
            }.mapIndexed { index, song ->
                SongAlbumMap(
                    songId = song.id,
                    albumId = albumPage.album.browseId,
                    index = index,
                )
            }.forEach(::upsert)
        albumPage.album.artists
            ?.map { artist ->
                ArtistEntity(
                    id = artist.id ?: artistByName(artist.name)?.id
                    ?: ArtistEntity.generateArtistId(),
                    name = artist.name,
                )
            }?.onEach(::insert)
            ?.mapIndexed { index, artist ->
                AlbumArtistMap(
                    albumId = albumPage.album.browseId,
                    artistId = artist.id,
                    order = index,
                )
            }?.forEach(::insert)
    }

    @Transaction
    fun update(
        song: Song,
        mediaMetadata: MediaMetadata,
    ) {
        update(
            song.song.copy(
                title = mediaMetadata.title,
                duration = mediaMetadata.duration,
                thumbnailUrl = mediaMetadata.thumbnailUrl,
                albumId = mediaMetadata.album?.id,
                albumName = mediaMetadata.album?.title,
            ),
        )
        songArtistMap(song.id).forEach(::delete)
        mediaMetadata.artists.forEachIndexed { index, artist ->
            val artistId = artist.id ?: artistByName(artist.name)?.id ?: ArtistEntity.generateArtistId()
            
            insert(
                ArtistEntity(
                    id = artistId,
                    name = artist.name,
                    channelId = artist.id,
                ),
            )
            insert(
                SongArtistMap(
                    songId = song.id,
                    artistId = artistId,
                    position = index,
                ),
            )
        }
    }

    @Update
    fun update(song: SongEntity)

    @Update
    fun update(artist: ArtistEntity)

    @Update
    fun update(album: AlbumEntity)

    @Update
    fun update(playlist: PlaylistEntity)

    @Transaction
    fun update(
        artist: ArtistEntity,
        artistPage: ArtistPage
    ) {
        update(
            artist.copy(
                name = artistPage.artist.title,
                thumbnailUrl = artistPage.artist.thumbnail?.resize(544, 544),
                lastUpdateTime = LocalDateTime.now()
            )
        )
    }

    @Transaction
    fun update(
        album: AlbumEntity,
        albumPage: AlbumPage,
        artists: List<ArtistEntity>? = emptyList(),
    ) {
        update(
            album.copy(
                id = albumPage.album.browseId,
                playlistId = albumPage.album.playlistId,
                title = albumPage.album.title,
                year = albumPage.album.year,
                thumbnailUrl = albumPage.album.thumbnail,
                songCount = albumPage.songs.size,
                duration = albumPage.songs.sumOf { song -> song.duration ?: 0 },
                explicit = albumPage.album.explicit || albumPage.songs.any { it.explicit },
            ),
        )
        if (artists?.size != albumPage.album.artists?.size) {
            artists?.forEach(::delete)
        }
        albumPage.songs
            .map(SongItem::toMediaMetadata)
            .onEach(::insert)
            .onEach {
                val existingSong = getSongByIdBlocking(it.id)
                if (existingSong != null) {
                    update(existingSong, it)
                }
            }.mapIndexed { index, song ->
                SongAlbumMap(
                    songId = song.id,
                    albumId = albumPage.album.browseId,
                    index = index,
                )
            }.forEach(::upsert)

        albumPage.album.artists?.let { artists ->
            // Recreate album artists
            albumArtistMaps(album.id).forEach(::delete)
            artists
                .map { artist ->
                    ArtistEntity(
                        id = artist.id ?: artistByName(artist.name)?.id
                        ?: ArtistEntity.generateArtistId(),
                        name = artist.name,
                    )
                }.onEach(::insert)
                .mapIndexed { index, artist ->
                    AlbumArtistMap(
                        albumId = albumPage.album.browseId,
                        artistId = artist.id,
                        order = index,
                    )
                }.forEach(::insert)
        }
    }

    @Update
    fun update(playlistEntity: PlaylistEntity, playlistItem: PlaylistItem) {
        update(
            playlistEntity.copy(
                name = playlistItem.title,
                browseId = playlistItem.id,
                thumbnailUrl = playlistItem.thumbnail,
                remoteSongCount = playlistItem.songCountText?.let { Regex("""\d+""").find(it)?.value?.toIntOrNull() },
                playEndpointParams = playlistItem.playEndpoint?.params,
                shuffleEndpointParams = playlistItem.shuffleEndpoint?.params,
                radioEndpointParams = playlistItem.radioEndpoint?.params
            )
        )
    }

    @Upsert
    fun upsert(map: SongAlbumMap)

    @Upsert
    fun upsert(lyrics: LyricsEntity)

    @Upsert
    fun upsert(format: FormatEntity)

    @Upsert
    fun upsert(song: SongEntity)

    @Delete
    fun delete(song: SongEntity)

    @Delete
    fun delete(songArtistMap: SongArtistMap)

    @Delete
    fun delete(artist: ArtistEntity)

    @Delete
    fun delete(album: AlbumEntity)

    @Delete
    fun delete(albumArtistMap: AlbumArtistMap)

    @Delete
    fun delete(playlist: PlaylistEntity)

    @Query("DELETE FROM playlist WHERE id = :playlistId OR browseId = :playlistId")
    fun deletePlaylistById(playlistId: String)

    @Delete
    fun delete(lyrics: LyricsEntity)

    @Delete
    fun delete(searchHistory: SearchHistory)

    @Delete
    fun delete(event: Event)

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

    fun historySongsWithMetadata(): Flow<List<EventWithSong>> = allHistorySongs().map { historyList ->
        historyList.mapNotNull { historySong ->
            val savedSong = getSavedSong(historySong.songId)
            val songObj = savedSong?.toSong() ?: getSongById(historySong.songId)
            if (songObj != null) {
                val timestamp = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(historySong.playedAt),
                    ZoneId.systemDefault()
                )
                EventWithSong(
                    event = Event(
                        id = historySong.playedAt,
                        songId = historySong.songId,
                        timestamp = timestamp,
                        playTime = historySong.playTimeMs
                    ),
                    song = songObj
                )
            } else null
        }
    }.flowOn(Dispatchers.IO)

    @Query("SELECT * FROM historialartist ORDER BY playedAt DESC")
    fun allHistoryArtists(): Flow<List<HistoryArtistEntity>>

    @Query("SELECT * FROM historialalbum ORDER BY playedAt DESC")
    fun allHistoryAlbums(): Flow<List<HistoryAlbumEntity>>

    @Query("SELECT * FROM historialplaylist ORDER BY playedAt DESC")
    fun allHistoryPlaylists(): Flow<List<HistoryPlaylistEntity>>

    // --- Migración de Esquema Legacy al Nuevo Esquema ---
    @Transaction
    suspend fun migrateLegacyDataToNewSchema() {
        val now = LocalDateTime.now()
        
        // 1. Migrar canciones Liked -> SavedSongEntity
        val legacySongs = getLikedSongsSync()
        val savedSongs = legacySongs.map { s ->
            val songArtist = songArtistMap(s.id).firstOrNull()?.let { getArtistById(it.artistId) }
            SavedSongEntity(
                id = s.id,
                title = s.title,
                artistId = songArtist?.id,
                artistName = songArtist?.name,
                duration = s.duration,
                thumbnailUrl = s.thumbnailUrl,
                albumId = s.albumId,
                albumName = s.albumName,
                savedAt = s.bookmarkedAt ?: now
            )
        }
        if (savedSongs.isNotEmpty()) {
            insertSavedSongs(savedSongs)
        }

        // 2. Migrar artistas Bookmarked -> SavedArtistEntity
        val legacyArtists = getBookmarkedArtistsSync()
        val savedArtists = legacyArtists.map { a ->
            SavedArtistEntity(
                id = a.id,
                name = a.name,
                thumbnailUrl = a.thumbnailUrl,
                channelId = a.channelId,
                savedAt = a.bookmarkedAt ?: now
            )
        }
        if (savedArtists.isNotEmpty()) {
            insertSavedArtists(savedArtists)
        }

        // 3. Migrar álbumes Liked -> SavedAlbumEntity
        val legacyAlbums = getLikedAlbumsSync()
        val savedAlbums = legacyAlbums.map { al ->
            val albumArtist = albumArtistMaps(al.id).firstOrNull()?.let { getArtistById(it.artistId) }
            SavedAlbumEntity(
                id = al.id,
                title = al.title,
                artistId = albumArtist?.id,
                artistName = albumArtist?.name,
                songCount = al.songCount,
                duration = al.duration,
                thumbnailUrl = al.thumbnailUrl,
                year = al.year,
                savedAt = al.bookmarkedAt ?: now
            )
        }
        if (savedAlbums.isNotEmpty()) {
            insertSavedAlbums(savedAlbums)
        }

        // 4. Migrar playlists -> SavedPlaylistEntity
        val legacyPlaylists = getPlaylistsSync()
        val savedPlaylists = legacyPlaylists.map { p ->
            SavedPlaylistEntity(
                id = p.id,
                name = p.name,
                browseId = p.browseId,
                isEditable = p.isEditable,
                thumbnailUrl = p.thumbnailUrl,
                createdAt = p.createdAt ?: now
            )
        }
        if (savedPlaylists.isNotEmpty()) {
            insertSavedPlaylists(savedPlaylists)
        }

        // 5. Migrar canciones escuchadas HistorySongEntity
        val legacyPlayedSongs = getPlayedSongsSync()
        legacyPlayedSongs.forEach { song ->
            if (song.totalPlayTime >= 25_000L) {
                insertHistorySong(
                    HistorySongEntity(
                        playedAt = System.currentTimeMillis() - (song.totalPlayTime),
                        songId = song.id,
                        duration = song.duration,
                        playTimeMs = song.totalPlayTime
                    )
                )
            }
        }
    }

    @Query("SELECT * FROM song WHERE isLiked = 1 OR id IN (SELECT videoId FROM set_video_id) OR id IN (SELECT id FROM savedsong)")
    fun getLikedSongsSync(): List<SongEntity>

    @Query("SELECT * FROM artist WHERE isBookmarked = 1 OR id IN (SELECT id FROM savedartist)")
    fun getBookmarkedArtistsSync(): List<ArtistEntity>

    @Query("SELECT * FROM album WHERE isLiked = 1 OR id IN (SELECT id FROM savedalbum)")
    fun getLikedAlbumsSync(): List<AlbumEntity>

    @Query("SELECT * FROM playlist")
    fun getPlaylistsSync(): List<PlaylistEntity>

    @Query("SELECT * FROM song WHERE totalPlayTime > 0 OR id IN (SELECT songId FROM event)")
    fun getPlayedSongsSync(): List<SongEntity>
}

