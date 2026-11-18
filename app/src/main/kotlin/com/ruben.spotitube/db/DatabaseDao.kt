/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
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
import com.ruben.spotitube.db.entities.AlbumWithSongs
import com.ruben.spotitube.db.entities.Artist
import com.ruben.spotitube.db.entities.DownloadOriginEntity
import com.ruben.spotitube.db.entities.DownloadedOriginRow
import com.ruben.spotitube.db.entities.DownloadedSongEntity
import com.ruben.spotitube.db.entities.FormatEntity
import com.ruben.spotitube.db.entities.LyricsEntity
import com.ruben.spotitube.db.entities.PlayCountEntity
import com.ruben.spotitube.db.entities.Playlist
import com.ruben.spotitube.db.entities.PlaylistEntity
import com.ruben.spotitube.db.entities.PlaylistSongItem
import com.ruben.spotitube.db.entities.PlaylistTagMap
import com.ruben.spotitube.db.entities.SearchHistory
import com.ruben.spotitube.db.entities.SetVideoIdEntity
import com.ruben.spotitube.db.entities.Song
import com.ruben.spotitube.db.entities.SavedAlbumEntity
import com.ruben.spotitube.db.entities.SavedArtistEntity
import com.ruben.spotitube.db.entities.SavedPlaylistEntity
import com.ruben.spotitube.db.entities.SavedSongEntity
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.text.Collator
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale

internal fun String.escapeSqlLike(): String =
    this.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

@Dao
@RewriteQueriesToDropUnusedColumns
interface DatabaseDao {
    fun songsByRowIdAsc(): Flow<List<Song>> = allSavedSongs().map { list -> list.map { it.toSong() } }
    fun songsByNameAsc(): Flow<List<Song>> = allSavedSongs().map { list -> list.map { it.toSong() }.sortedBy { s -> s.song.title } }
    fun songsByPlayTimeAsc(): Flow<List<Song>> = allSavedSongs().map { list -> list.map { it.toSong() } }

    fun allAlbumsByRowIdAsc(): Flow<List<Album>> = allSavedAlbums().map { list -> list.map { it.toAlbum() } }
    fun allArtistsByNameAsc(): Flow<List<Artist>> = allSavedArtists().map { list -> list.map { it.toArtist() } }

    fun editablePlaylistsByCreateDateAsc(): Flow<List<Playlist>> = playlistsWithMetadata().map { list -> list.filter { it.playlist.isEditable } }

    suspend fun checkInPlaylist(playlistId: String, songId: String): Int {
        val playlist = getSavedPlaylist(playlistId) ?: return 0
        return if (playlist.songs.any { it.id == songId }) 1 else 0
    }

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

    @Query("SELECT * FROM savedsong WHERE albumId = :albumId ORDER BY savedAt DESC")
    fun getSavedSongsByAlbum(albumId: String): Flow<List<SavedSongEntity>>

    @Query("SELECT * FROM savedsong WHERE artistId = :artistId ORDER BY savedAt DESC")
    fun getSavedSongsByArtist(artistId: String): Flow<List<SavedSongEntity>>

    @Query("SELECT * FROM savedsong WHERE artistId = :artistId ORDER BY savedAt DESC LIMIT :limit")
    fun getSavedSongsByArtistPreview(artistId: String, limit: Int): Flow<List<SavedSongEntity>>

    @Query("SELECT * FROM savedsong ORDER BY savedAt DESC LIMIT :limit")
    fun getSavedSongsRecommendation(limit: Int): Flow<List<SavedSongEntity>>

    fun albumSongs(albumId: String): Flow<List<Song>> = getSavedSongsByAlbum(albumId).map { list -> list.map { it.toSong() } }

    fun artistSongs(
        artistId: String,
        sortType: ArtistSongSortType,
        descending: Boolean,
    ): Flow<List<Song>> = getSavedSongsByArtist(artistId).map { savedList ->
        val savedMatching = savedList.map { it.toSong() }
        val collator = Collator.getInstance(Locale.getDefault())
        collator.strength = Collator.PRIMARY
        when (sortType) {
            ArtistSongSortType.CREATE_DATE -> savedMatching
            ArtistSongSortType.NAME -> savedMatching.sortedWith(compareBy(collator) { it.song.title })
            ArtistSongSortType.PLAY_TIME -> savedMatching.sortedBy { it.song.duration }
        }.let { if (descending) it.reversed() else it }
    }.flowOn(Dispatchers.IO)

    fun artistSongsPreview(
        artistId: String,
        previewSize: Int = 3,
    ): Flow<List<Song>> = getSavedSongsByArtistPreview(artistId, previewSize).map { savedList ->
        savedList.map { it.toSong() }
    }.flowOn(Dispatchers.IO)

    fun quickPicks(now: Long = System.currentTimeMillis()): Flow<List<Song>> = allSavedSongs().map { list -> list.map { it.toSong() } }

    fun getRecommendationAlbum(
        now: Long = System.currentTimeMillis(),
        limit: Int = 5,
        offset: Int = 0,
    ): Flow<List<Song>> = getSavedSongsRecommendation(limit).map { list -> list.map { it.toSong() } }


    @Query("SELECT * FROM savedalbum WHERE artistId = :artistId ORDER BY savedAt DESC LIMIT :limit")
    fun getSavedAlbumsByArtistPreview(artistId: String, limit: Int): Flow<List<SavedAlbumEntity>>

    @Query("SELECT * FROM savedsong WHERE id = :songId LIMIT 1")
    fun getSavedSongFlow(songId: String?): Flow<SavedSongEntity?>

    fun artistAlbumsPreview(artistId: String, previewSize: Int = 6): Flow<List<Album>> = getSavedAlbumsByArtistPreview(artistId, previewSize).map { savedList ->
        savedList.map { it.toAlbum() }
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
    ): Flow<List<Song>> = getSavedSongsRecommendation(limit).map { list -> list.map { it.toSong() } }

    fun song(songId: String?): Flow<Song?> = getSavedSongFlow(songId).map { it?.toSong() }

    suspend fun getSongById(songId: String): Song? = getSavedSong(songId)?.toSong()

    fun getSongByIdBlocking(songId: String): Song? = kotlinx.coroutines.runBlocking { getSavedSong(songId)?.toSong() }

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

    @Query("SELECT * FROM savedartist WHERE id = :id LIMIT 1")
    fun getSavedArtistFlow(id: String): Flow<SavedArtistEntity?>

    @Query("SELECT * FROM savedalbum WHERE id = :id LIMIT 1")
    fun getSavedAlbumFlow(id: String): Flow<SavedAlbumEntity?>

    @Query("SELECT * FROM savedalbum WHERE id IN (:ids) ORDER BY savedAt DESC")
    fun getSavedAlbumsByIds(ids: Collection<String>): Flow<List<SavedAlbumEntity>>

    fun artist(id: String): Flow<Artist?> = getSavedArtistFlow(id).map { it?.toArtist() }

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
        AlbumSortType.CUSTOM -> albumsByRowIdAsc()
    }.map { it.reversed(descending) }

    fun album(id: String): Flow<Album?> = getSavedAlbumFlow(id).map { it?.toAlbum() }

    fun albumsByRowId(ids: Collection<String>): Flow<List<Album>> = getSavedAlbumsByIds(ids).map { list -> list.map { it.toAlbum() } }
    fun albumsByTitle(ids: Collection<String>): Flow<List<Album>> = getSavedAlbumsByIds(ids).map { list -> list.map { it.toAlbum() } }
    fun albumsByYear(ids: Collection<String>): Flow<List<Album>> = getSavedAlbumsByIds(ids).map { list -> list.map { it.toAlbum() } }
    fun albumsBySongCount(ids: Collection<String>): Flow<List<Album>> = getSavedAlbumsByIds(ids).map { list -> list.map { it.toAlbum() } }
    fun albumsByDuration(ids: Collection<String>): Flow<List<Album>> = getSavedAlbumsByIds(ids).map { list -> list.map { it.toAlbum() } }
    fun albumsByPlayTime(ids: Collection<String>): Flow<List<Album>> = getSavedAlbumsByIds(ids).map { list -> list.map { it.toAlbum() } }

    fun albumsByIds(
        ids: Collection<String>,
        sortType: AlbumSortType,
        descending: Boolean,
    ): Flow<List<Album>> = getSavedAlbumsByIds(ids).map { list -> list.map { it.toAlbum() } }

    fun albumWithSongs(albumId: String): Flow<AlbumWithSongs?> = allSavedAlbums().map { list ->
        val saved = list.firstOrNull { it.id == albumId }
        saved?.let { AlbumWithSongs(album = it.toAlbum().album, songs = emptyList(), artists = emptyList()) }
    }

    fun playlistsWithMetadata(): Flow<List<Playlist>> = allSavedPlaylists().map { savedPlaylists ->
        savedPlaylists.map { saved ->
            saved.toPlaylist()
        }
    }

    fun playlistsByRowIdAsc(): Flow<List<Playlist>> = playlistsWithMetadata()
    fun playlistsByUpdatedDateAsc(): Flow<List<Playlist>> = playlistsWithMetadata()
    fun playlistsByNameAsc(): Flow<List<Playlist>> = playlistsWithMetadata()
    fun playlistsByCustomOrderAsc(): Flow<List<Playlist>> = playlistsWithMetadata()

    @Query("UPDATE savedplaylist SET name = :name, nameNormalized = :nameNormalized WHERE id = :playlistId OR browseId = :playlistId")
    suspend fun updatePlaylistTitleRaw(playlistId: String, name: String, nameNormalized: String)

    suspend fun updatePlaylistTitle(playlistId: String, name: String) {
        updatePlaylistTitleRaw(playlistId = playlistId, name = name, nameNormalized = normalizeForSearch(name))
    }

    @Query("UPDATE savedplaylist SET thumbnailUrl = :thumbnailUrl WHERE id = :playlistId OR browseId = :playlistId")
    suspend fun updatePlaylistThumbnail(playlistId: String, thumbnailUrl: String?)

    suspend fun playlistDuplicates(playlistId: String, songIds: List<String>): List<String> {
        val playlist = getSavedPlaylist(playlistId) ?: return emptyList()
        val existingIds = playlist.songs.map { it.id }.toSet()
        return songIds.filter { it in existingIds }
    }

    @Transaction
    suspend fun addSongsToPlaylist(playlistId: String, items: List<PlaylistSongItem>) {
        val playlist = getSavedPlaylist(playlistId) ?: return
        val updatedSongs = playlist.songs + items
        insertSavedPlaylist(playlist.copy(songs = updatedSongs))
    }

    @Transaction
    suspend fun removeSongFromPlaylist(playlistId: String, index: Int) {
        val playlist = getSavedPlaylist(playlistId) ?: return
        if (index in playlist.songs.indices) {
            val updatedSongs = playlist.songs.toMutableList().apply { removeAt(index) }
            insertSavedPlaylist(playlist.copy(songs = updatedSongs))
        }
    }

    @Transaction
    suspend fun removeSongsFromPlaylist(playlistId: String, indices: List<Int>) {
        val playlist = getSavedPlaylist(playlistId) ?: return
        val sortedIndices = indices.distinct().sortedDescending()
        val updatedSongs = playlist.songs.toMutableList()
        sortedIndices.forEach { idx ->
            if (idx in updatedSongs.indices) {
                updatedSongs.removeAt(idx)
            }
        }
        insertSavedPlaylist(playlist.copy(songs = updatedSongs))
    }

    @Transaction
    suspend fun moveSongInPlaylist(playlistId: String, fromIndex: Int, toIndex: Int) {
        val playlist = getSavedPlaylist(playlistId) ?: return
        if (fromIndex in playlist.songs.indices && toIndex in playlist.songs.indices) {
            val updatedSongs = playlist.songs.toMutableList().apply {
                val item = removeAt(fromIndex)
                add(toIndex, item)
            }
            insertSavedPlaylist(playlist.copy(songs = updatedSongs))
        }
    }

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

    fun albumsSavedInternal(): Flow<List<Album>> = allSavedAlbums().map { list -> list.map { it.toAlbum() } }

    fun albumsSaved(sortType: AlbumSortType, descending: Boolean): Flow<List<Album>> =
        albumsSavedInternal().map { albums ->
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
                AlbumSortType.CUSTOM -> albums
            }.reversed(descending)
        }

    fun artistsSavedInternal(): Flow<List<Artist>> = allSavedArtists().map { list -> list.map { it.toArtist() } }

    fun artistsSaved(sortType: ArtistSortType, descending: Boolean): Flow<List<Artist>> =
        artistsSavedInternal().map { artists ->
            val collator = Collator.getInstance(Locale.getDefault())
            collator.strength = Collator.PRIMARY
            when (sortType) {
                ArtistSortType.CREATE_DATE -> artists
                ArtistSortType.NAME -> artists.sortedWith(compareBy(collator) { it.artist.name })
                ArtistSortType.SONG_COUNT -> artists.sortedBy { it.songCount }
                ArtistSortType.PLAY_TIME -> artists
                ArtistSortType.CUSTOM -> artists
            }.reversed(descending)
        }

    @Query("SELECT id FROM savedartist")
    fun getSavedArtistIds(): Flow<List<String>>

    @Query("SELECT id FROM savedalbum")
    fun getSavedAlbumIds(): Flow<List<String>>

    @Query("SELECT id FROM savedsong")
    fun getSavedSongIds(): Flow<List<String>>

    @Query("SELECT id FROM savedplaylist UNION SELECT browseId FROM savedplaylist WHERE browseId IS NOT NULL")
    fun getSavedPlaylistIds(): Flow<List<String>>

    @Transaction
    @Query("""
        SELECT 
            (SELECT COUNT(*) FROM savedsong) as songCount,
            (SELECT COUNT(*) FROM savedalbum) as albumCount,
            (SELECT COUNT(*) FROM savedartist) as artistCount,
            0 as totalPlayTimeMillis
    """)
    fun getLibrarySummary(): Flow<LibrarySummary>

    @Query("SELECT * FROM savedplaylist WHERE id = :playlistId OR browseId = :playlistId OR id = REPLACE(:playlistId, 'VL', '') OR browseId = REPLACE(:playlistId, 'VL', '') OR id = 'VL' || REPLACE(:playlistId, 'VL', '') OR browseId = 'VL' || REPLACE(:playlistId, 'VL', '') LIMIT 1")
    fun getSavedPlaylistFlow(playlistId: String): Flow<SavedPlaylistEntity?>

    @Query("SELECT * FROM savedplaylist WHERE browseId = :browseId LIMIT 1")
    fun getSavedPlaylistByBrowseIdFlow(browseId: String): Flow<SavedPlaylistEntity?>

    fun playlistSongItems(playlistId: String): Flow<List<PlaylistSongItem>> = getSavedPlaylistFlow(playlistId).map { playlist ->
        playlist?.songs ?: emptyList()
    }

    fun playlistSongs(playlistId: String): Flow<List<Song>> = playlistSongItems(playlistId).map { items ->
        items.map { item ->
            Song(
                song = SavedSongEntity(
                    id = item.id,
                    title = item.title,
                    artistId = null,
                    artistName = item.artistName,
                    duration = item.duration,
                    thumbnailUrl = item.thumbnailUrl,
                    albumId = item.albumId,
                    albumName = item.albumName,
                    explicit = item.explicit
                ),
                artists = item.artistName?.let { name ->
                    listOf(SavedArtistEntity(id = "", name = name))
                } ?: emptyList()
            )
        }
    }

    fun playlist(id: String): Flow<Playlist?> = getSavedPlaylistFlow(id).map { it?.toPlaylist() }

    suspend fun getPlaylistById(playlistId: String): Playlist? = getSavedPlaylist(playlistId)?.toPlaylist()

    fun playlistByBrowseId(browseId: String): Flow<Playlist?> = getSavedPlaylistByBrowseIdFlow(browseId).map { it?.toPlaylist() }

    @Query("""
        SELECT * FROM savedsong 
        WHERE titleNormalized LIKE '%' || :query || '%' ESCAPE '\' 
           OR artistNormalized LIKE '%' || :query || '%' ESCAPE '\' 
           OR (titleNormalized = '' AND (title LIKE '%' || :rawQuery || '%' ESCAPE '\' OR artistName LIKE '%' || :rawQuery || '%' ESCAPE '\'))
        LIMIT :limit
    """)
    fun searchSongsRaw(
        query: String,
        rawQuery: String,
        limit: Int = Int.MAX_VALUE
    ): Flow<List<SavedSongEntity>>

    fun searchSongs(
        query: String,
        previewSize: Int = Int.MAX_VALUE,
    ): Flow<List<Song>> = searchSongsRaw(
        query = normalizeForSearch(query).escapeSqlLike(),
        rawQuery = query.escapeSqlLike(),
        limit = previewSize
    ).map { list ->
        list.map { it.toSong() }
    }.flowOn(Dispatchers.IO)

    @Query("""
        SELECT * FROM savedartist 
        WHERE nameNormalized LIKE '%' || :query || '%' ESCAPE '\' 
           OR (nameNormalized = '' AND name LIKE '%' || :rawQuery || '%' ESCAPE '\')
        LIMIT :limit
    """)
    fun searchArtistsRaw(
        query: String,
        rawQuery: String,
        limit: Int = Int.MAX_VALUE
    ): Flow<List<SavedArtistEntity>>

    fun searchArtists(
        query: String,
        previewSize: Int = Int.MAX_VALUE,
    ): Flow<List<Artist>> = searchArtistsRaw(
        query = normalizeForSearch(query).escapeSqlLike(),
        rawQuery = query.escapeSqlLike(),
        limit = previewSize
    ).map { list ->
        list.map { it.toArtist() }
    }.flowOn(Dispatchers.IO)

    @Query("""
        SELECT * FROM savedalbum 
        WHERE titleNormalized LIKE '%' || :query || '%' ESCAPE '\' 
           OR artistNormalized LIKE '%' || :query || '%' ESCAPE '\' 
           OR (titleNormalized = '' AND (title LIKE '%' || :rawQuery || '%' ESCAPE '\' OR artistName LIKE '%' || :rawQuery || '%' ESCAPE '\'))
        LIMIT :limit
    """)
    fun searchAlbumsRaw(
        query: String,
        rawQuery: String,
        limit: Int = Int.MAX_VALUE
    ): Flow<List<SavedAlbumEntity>>

    fun searchAlbums(
        query: String,
        previewSize: Int = Int.MAX_VALUE,
    ): Flow<List<Album>> = searchAlbumsRaw(
        query = normalizeForSearch(query).escapeSqlLike(),
        rawQuery = query.escapeSqlLike(),
        limit = previewSize
    ).map { list ->
        list.map { it.toAlbum() }
    }.flowOn(Dispatchers.IO)

    @Query("""
        SELECT * FROM savedplaylist 
        WHERE nameNormalized LIKE '%' || :query || '%' ESCAPE '\' 
           OR (nameNormalized = '' AND name LIKE '%' || :rawQuery || '%' ESCAPE '\')
        LIMIT :limit
    """)
    fun searchPlaylistsRaw(
        query: String,
        rawQuery: String,
        limit: Int = Int.MAX_VALUE
    ): Flow<List<SavedPlaylistEntity>>

    fun searchPlaylists(
        query: String,
        previewSize: Int = Int.MAX_VALUE,
    ): Flow<List<Playlist>> = searchPlaylistsRaw(
        query = normalizeForSearch(query).escapeSqlLike(),
        rawQuery = query.escapeSqlLike(),
        limit = previewSize
    ).map { list ->
        list.map { it.toPlaylist() }
    }.flowOn(Dispatchers.IO)


    @Transaction
    @Query("SELECT * FROM search_history WHERE `query` LIKE :query || '%' ORDER BY id DESC")
    fun searchHistory(query: String = ""): Flow<List<SearchHistory>>

    @Transaction
    @Query("DELETE FROM search_history")
    fun clearSearchHistory()

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

    @Query("SELECT * FROM savedartist WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun artistByName(name: String): SavedArtistEntity?

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
    fun insert(playCountEntity: PlayCountEntity): Long

    @Query("""
        UPDATE savedsong 
        SET title = :title,
            titleNormalized = :titleNormalized,
            artistId = COALESCE(:artistId, artistId),
            artistName = COALESCE(:artistName, artistName),
            artistNormalized = COALESCE(:artistNormalized, artistNormalized),
            duration = CASE WHEN :duration > 0 THEN :duration ELSE duration END,
            thumbnailUrl = COALESCE(:thumbnailUrl, thumbnailUrl),
            albumId = COALESCE(:albumId, albumId),
            albumName = COALESCE(:albumName, albumName),
            explicit = :explicit,
            year = COALESCE(:year, year)
        WHERE id = :id
    """)
    fun updateSongMetadataOnlyRaw(
        id: String,
        title: String,
        titleNormalized: String,
        artistId: String?,
        artistName: String?,
        artistNormalized: String?,
        duration: Int,
        thumbnailUrl: String?,
        albumId: String?,
        albumName: String?,
        explicit: Boolean,
        year: Int?
    )

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
    ) {
        val titleNormalized = normalizeForSearch(title)
        val artistNormalized = artistName?.let { normalizeForSearch(it) }
        updateSongMetadataOnlyRaw(
            id = id,
            title = title,
            titleNormalized = titleNormalized,
            artistId = artistId,
            artistName = artistName,
            artistNormalized = artistNormalized,
            duration = duration,
            thumbnailUrl = thumbnailUrl,
            albumId = albumId,
            albumName = albumName,
            explicit = explicit,
            year = year
        )
    }

    @Query("UPDATE savedsong SET duration = :duration WHERE id = :songId AND (duration = -1 OR duration IS NULL)")
    fun updateSongDuration(songId: String, duration: Int)

    @Query("SELECT * FROM savedsong WHERE id = :songId")
    fun getSavedSongDirect(songId: String): SavedSongEntity?

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

    @Transaction
    suspend fun clearPlaylist(playlistId: String) {
        val playlist = getSavedPlaylist(playlistId) ?: return
        insertSavedPlaylist(playlist.copy(songs = emptyList()))
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

    @Query("SELECT * FROM savedsong ORDER BY RANDOM() LIMIT :limit")
    suspend fun getRandomSavedSongs(limit: Int = 20): List<SavedSongEntity>

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

    // --- Downloaded ---
    @Upsert
    fun upsertDownloadedSong(song: DownloadedSongEntity)

    @Upsert
    fun upsertDownloadedSongs(songs: List<DownloadedSongEntity>)

    @Query("SELECT * FROM downloaded_song ORDER BY downloadedAt DESC")
    fun allDownloadedSongs(): Flow<List<DownloadedSongEntity>>

    @Query("SELECT id FROM downloaded_song")
    fun downloadedSongIds(): Flow<List<String>>

    @Query("SELECT * FROM downloaded_song")
    suspend fun getDownloadedSongsDirect(): List<DownloadedSongEntity>

    @Query("DELETE FROM downloaded_song WHERE id = :songId")
    suspend fun deleteDownloadedSong(songId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDownloadOrigins(origins: List<DownloadOriginEntity>)

    @Query("DELETE FROM download_origin WHERE originType = :type AND originId = :originId")
    suspend fun deleteDownloadOrigin(type: String, originId: String)

    @Query("SELECT songId FROM download_origin WHERE originType = :type AND originId = :originId ORDER BY position")
    suspend fun getOriginSongIds(type: String, originId: String): List<String>

    @Query("SELECT songId FROM download_origin WHERE originType = :type AND originId = :originId ORDER BY position")
    fun originSongIdsFlow(type: String, originId: String): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM download_origin WHERE songId = :songId")
    suspend fun countOriginsForSong(songId: String): Int

    @Query("SELECT id FROM downloaded_song WHERE id NOT IN (SELECT songId FROM download_origin)")
    suspend fun downloadedSongIdsWithoutOrigin(): List<String>

    @Query("SELECT * FROM download_origin WHERE originType = :type GROUP BY originId ORDER BY MAX(createdAt) DESC")
    fun downloadOriginsByType(type: String): Flow<List<DownloadOriginEntity>>

    @Query("SELECT o.originId AS originId, MAX(o.originTitle) AS originTitle, MAX(o.originThumbnailUrl) AS originThumbnailUrl, COUNT(*) AS songCount FROM download_origin o INNER JOIN downloaded_song d ON d.id = o.songId WHERE o.originType = :type GROUP BY o.originId ORDER BY MAX(o.createdAt) DESC")
    fun downloadedOriginRows(type: String): Flow<List<DownloadedOriginRow>>

    @Query("DELETE FROM download_origin WHERE songId = :songId")
    suspend fun deleteOriginsForSong(songId: String)

    @Query("SELECT DISTINCT songId FROM download_origin")
    suspend fun getAllOriginSongIds(): List<String>

    @Query("SELECT d.* FROM downloaded_song d INNER JOIN download_origin o ON o.songId = d.id WHERE o.originType = :type AND o.originId = :originId ORDER BY o.position")
    fun downloadedSongsByOrigin(type: String, originId: String): Flow<List<DownloadedSongEntity>>

    @Query("SELECT * FROM download_origin WHERE originType = :type AND originId = :originId LIMIT 1")
    fun downloadOriginInfo(type: String, originId: String): Flow<DownloadOriginEntity?>

}
