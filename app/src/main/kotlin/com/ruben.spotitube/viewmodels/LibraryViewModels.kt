/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



@file:OptIn(ExperimentalCoroutinesApi::class)

package com.ruben.spotitube.viewmodels


import android.content.Context
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.offline.Download
import com.ruben.spotitube.constants.AlbumFilter
import com.ruben.spotitube.constants.AlbumFilterKey
import com.ruben.spotitube.constants.AlbumSortDescendingKey
import com.ruben.spotitube.constants.AlbumSortType
import com.ruben.spotitube.constants.AlbumSortTypeKey
import com.ruben.spotitube.constants.ArtistFilter
import com.ruben.spotitube.constants.ArtistFilterKey
import com.ruben.spotitube.constants.ArtistSongSortDescendingKey
import com.ruben.spotitube.constants.ArtistSongSortType
import com.ruben.spotitube.constants.ArtistSongSortTypeKey
import com.ruben.spotitube.constants.ArtistSortDescendingKey
import com.ruben.spotitube.constants.ArtistSortType
import com.ruben.spotitube.constants.ArtistSortTypeKey
import com.ruben.spotitube.constants.HideExplicitKey
import com.ruben.spotitube.constants.HideVideoKey
import com.ruben.spotitube.constants.LibraryFilter
import com.ruben.spotitube.constants.PlaylistSortDescendingKey
import com.ruben.spotitube.constants.PlaylistSortType
import com.ruben.spotitube.constants.PlaylistSortTypeKey
import com.ruben.spotitube.constants.SongFilter
import com.ruben.spotitube.constants.SongFilterKey
import com.ruben.spotitube.constants.SongSortDescendingKey
import com.ruben.spotitube.constants.SongSortType
import com.ruben.spotitube.constants.SongSortTypeKey
import com.ruben.spotitube.constants.TopSize
import com.ruben.spotitube.db.MusicDatabase
import com.ruben.spotitube.db.entities.SavedAlbumEntity
import com.ruben.spotitube.extensions.toEnum
import com.ruben.spotitube.innertube.YouTube
import com.ruben.spotitube.playback.DownloadUtil
import com.ruben.spotitube.sync.LibraryStateHolder
import com.ruben.spotitube.utils.dataStore
import com.ruben.spotitube.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class LibrarySongsViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    downloadUtil: DownloadUtil,
    private val libraryStateHolder: LibraryStateHolder
) : ViewModel() {
    val allSongs =
        context.dataStore.data
            .map {
                Triple(
                    Triple(
                        it[SongFilterKey].toEnum(SongFilter.SAVED),
                        it[SongSortTypeKey].toEnum(SongSortType.CREATE_DATE),
                        (it[SongSortDescendingKey] ?: true),
                    ),
                    it[HideExplicitKey] ?: false,
                    it[HideVideoKey] ?: false,
                )
            }.distinctUntilChanged()
            .flatMapLatest { (filterSort, hideExplicit, hideVideo) ->
                val (filter, sortType, descending) = filterSort
                when (filter) {
                    SongFilter.LIBRARY,
                    SongFilter.SAVED -> libraryStateHolder.getLibrarySongs().map { songs ->
                        when (sortType) {
                            SongSortType.CREATE_DATE -> songs
                            SongSortType.NAME -> songs.sortedBy { it.song.title }
                            SongSortType.ARTIST -> songs.sortedBy { it.artists.joinToString { a -> a.name } }
                            SongSortType.PLAY_TIME -> songs
                        }.let { if (descending) it.reversed() else it }
                    }
                    SongFilter.DOWNLOADED ->
                        downloadUtil.downloads.map { downloadsMap ->
                            val downloadedSongs = downloadsMap.values
                                .filter { it.state == Download.STATE_COMPLETED }
                                .mapNotNull { download ->
                                    com.ruben.spotitube.models.DownloadedSongMetadata.fromDownload(download)?.toSong()
                                }
                            when (sortType) {
                                SongSortType.CREATE_DATE -> downloadedSongs
                                SongSortType.NAME -> downloadedSongs.sortedBy { it.song.title }
                                SongSortType.ARTIST -> downloadedSongs.sortedBy { song ->
                                    song.artists.joinToString("") { it.name }
                                }
                                SongSortType.PLAY_TIME -> downloadedSongs
                            }.let { if (descending) it.reversed() else it }
                        }
                }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}

@HiltViewModel
class LibraryArtistsViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    private val libraryStateHolder: LibraryStateHolder
) : ViewModel() {
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    val allArtists =
        context.dataStore.data
            .map {
                Triple(
                    it[ArtistFilterKey].toEnum(ArtistFilter.SAVED),
                    it[ArtistSortTypeKey].toEnum(ArtistSortType.CREATE_DATE),
                    it[ArtistSortDescendingKey] ?: true,
                )
            }.distinctUntilChanged()
            .flatMapLatest { (filter, sortType, descending) ->
                when (filter) {
                    ArtistFilter.LIBRARY,
                    ArtistFilter.SAVED -> libraryStateHolder.getLibraryArtists().map { artists ->
                        when (sortType) {
                            ArtistSortType.CREATE_DATE -> artists.sortedBy { it.artist.savedAt }
                            ArtistSortType.NAME -> artists.sortedBy { it.artist.name }
                            ArtistSortType.SONG_COUNT -> artists.sortedBy { it.songCount }
                            ArtistSortType.PLAY_TIME -> artists.sortedBy { it.timeListened ?: 0 }
                        }.let { if (descending) it.reversed() else it }
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}

@HiltViewModel
class LibraryAlbumsViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    downloadUtil: DownloadUtil,
    private val libraryStateHolder: LibraryStateHolder
) : ViewModel() {
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    val allAlbums =
        context.dataStore.data
            .map {
                Pair(
                    Triple(
                        it[AlbumFilterKey].toEnum(AlbumFilter.SAVED),
                        it[AlbumSortTypeKey].toEnum(AlbumSortType.CREATE_DATE),
                        it[AlbumSortDescendingKey] ?: true,
                    ),
                    it[HideExplicitKey] ?: false
                )
            }.distinctUntilChanged()
            .flatMapLatest { (filterSort, hideExplicit) ->
                val (filter, sortType, descending) = filterSort
                when (filter) {
                    AlbumFilter.DOWNLOADED ->
                        downloadUtil.downloads.flatMapLatest { downloadsMap ->
                            val downloadedAlbumIds = downloadsMap.values
                                .filter { it.state == Download.STATE_COMPLETED }
                                .mapNotNull { download ->
                                    com.ruben.spotitube.models.DownloadedSongMetadata.fromDownload(download)?.albumId
                                }
                                .toSet()
                            database.albumsByIds(downloadedAlbumIds, sortType, descending)
                        }
                    
                    AlbumFilter.DOWNLOADED_FULL ->
                        downloadUtil.downloads.flatMapLatest { downloadsMap ->
                            val downloadedCountByAlbum = downloadsMap.values
                                .filter { it.state == Download.STATE_COMPLETED }
                                .mapNotNull { download ->
                                    com.ruben.spotitube.models.DownloadedSongMetadata.fromDownload(download)?.albumId
                                }
                                .groupBy { it }
                                .mapValues { it.value.size }
                            database.albumsByIds(downloadedCountByAlbum.keys, sortType, descending)
                                .map { albums ->
                                    albums.filter { album ->
                                        val totalSongsInAlbum = album.album.songCount
                                        val downloadedSongsCount = downloadedCountByAlbum[album.album.id] ?: 0
                                        totalSongsInAlbum in 1..downloadedSongsCount
                                    }
                                }
                        }
                    AlbumFilter.LIBRARY,
                    AlbumFilter.SAVED -> libraryStateHolder.getLibraryAlbums().map { albums ->
                        when (sortType) {
                            AlbumSortType.CREATE_DATE -> albums
                            AlbumSortType.NAME -> albums.sortedBy { it.album.title }
                            AlbumSortType.YEAR -> albums.sortedBy { it.album.year }
                            AlbumSortType.SONG_COUNT -> albums.sortedBy { it.album.songCount }
                            AlbumSortType.LENGTH -> albums.sortedBy { it.album.duration }
                            AlbumSortType.PLAY_TIME -> albums.sortedBy { it.timeListened ?: 0L }
                            AlbumSortType.ARTIST -> albums.sortedBy { it.artists.joinToString { a -> a.name } }
                        }.let { if (descending) it.reversed() else it }
                    }
                }
            }.onEach { albumsList ->
                Log.d("SAVED_ALBUM_DEBUG", "3. ViewModel (allAlbums) emite ${albumsList.size} álbumes a la UI")
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            allAlbums.collect { albums ->
                albums
                    .filter {
                        it.album.songCount == 0
                    }.forEach { album ->
                        YouTube
                            .album(album.id)
                            .onSuccess { albumPage ->
                                database.withTransaction {
                                    val albumArtist = album.artists.firstOrNull()
                                    database.insertSavedAlbum(
                                        SavedAlbumEntity(
                                            id = albumPage.album.browseId,
                                            title = albumPage.album.title,
                                            artistId = albumArtist?.id,
                                            artistName = albumArtist?.name,
                                            songCount = albumPage.songs.size,
                                            duration = albumPage.songs.sumOf { song -> song.duration ?: 0 },
                                            thumbnailUrl = albumPage.album.thumbnail,
                                            year = albumPage.album.year
                                        )
                                    )
                                }
                            }.onFailure {
                                reportException(it)
                                if (it.message?.contains("NOT_FOUND") == true) {
                                    database.withTransaction {
                                        database.deleteSavedAlbum(album.album.id)
                                    }
                                }
                            }
                    }
            }
        }
    }
}

@HiltViewModel
class LibraryPlaylistsViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
) : ViewModel() {
    val allPlaylists =
        context.dataStore.data
            .map {
                it[PlaylistSortTypeKey].toEnum(PlaylistSortType.CUSTOM) to (it[PlaylistSortDescendingKey]
                    ?: true)
            }.distinctUntilChanged()
            .flatMapLatest { (sortType, descending) ->
                database.playlists(sortType, descending)
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val topValue =
        context.dataStore.data
            .map { it[TopSize] ?: "50" }
            .distinctUntilChanged()
}

@HiltViewModel
class ArtistSongsViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val artistId = savedStateHandle.get<String>("artistId")!!
    val artist =
        database
            .artist(artistId)
            .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val songs =
        context.dataStore.data
            .map {
                Pair(
                    it[ArtistSongSortTypeKey].toEnum(ArtistSongSortType.CREATE_DATE) to (it[ArtistSongSortDescendingKey]
                        ?: true),
                    it[HideExplicitKey] ?: false
                )
            }.distinctUntilChanged()
            .flatMapLatest { (sortDesc, hideExplicit) ->
                val (sortType, descending) = sortDesc
                database.artistSongs(artistId, sortType, descending)
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}

@HiltViewModel
class LibraryMixViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    libraryStateHolder: LibraryStateHolder
) : ViewModel() {
    val topValue =
        context.dataStore.data
            .map { it[TopSize] ?: "50" }
            .distinctUntilChanged()
    var artists =
        libraryStateHolder.getLibraryArtists().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    var albums = libraryStateHolder.getLibraryAlbums()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    var playlists =
        context.dataStore.data
            .map {
                it[PlaylistSortTypeKey].toEnum(PlaylistSortType.CUSTOM) to (it[PlaylistSortDescendingKey] ?: true)
            }.distinctUntilChanged()
            .flatMapLatest { (sortType, descending) -> database.playlists(sortType, descending) }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
    }
}

@HiltViewModel
class LibraryViewModel
@Inject
constructor() : ViewModel() {
    private val curScreen = mutableStateOf(LibraryFilter.LIBRARY)
    val filter: MutableState<LibraryFilter> = curScreen
}
