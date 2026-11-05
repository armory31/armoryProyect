/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ruben.spotitube.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ruben.spotitube.HomeTiming
import com.ruben.spotitube.constants.HideVideoKey
import com.ruben.spotitube.constants.QuickPicks
import com.ruben.spotitube.constants.QuickPicksKey
import com.ruben.spotitube.constants.SpeedDialSongIdsKey
import com.ruben.spotitube.db.MusicDatabase
import com.ruben.spotitube.db.entities.LocalItem
import com.ruben.spotitube.db.entities.SavedPlaylistEntity
import com.ruben.spotitube.db.entities.Song
import com.ruben.spotitube.extensions.toEnum
import com.ruben.spotitube.innertube.YouTube
import com.ruben.spotitube.innertube.models.SongItem
import com.ruben.spotitube.models.toSong
import com.ruben.spotitube.models.toAlbum
import com.ruben.spotitube.innertube.models.WatchEndpoint
import com.ruben.spotitube.innertube.models.YTItem
import com.ruben.spotitube.innertube.models.filterVideo
import com.ruben.spotitube.innertube.pages.HomePage
import com.ruben.spotitube.models.ItemMetadata
import com.ruben.spotitube.models.SimilarRecommendation
import com.ruben.spotitube.playback.DownloadUtil
import com.ruben.spotitube.sync.LibraryStateHolder
import com.ruben.spotitube.utils.SyncUtils
import com.ruben.spotitube.utils.dataStore
import com.ruben.spotitube.utils.get
import com.ruben.spotitube.utils.getAsync
import com.ruben.spotitube.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext val context: Context,
    val database: MusicDatabase,
    val syncUtils: SyncUtils,
    val downloadUtil: DownloadUtil,
    val libraryStateHolder: LibraryStateHolder,
) : ViewModel() {
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isInitialLoadComplete = MutableStateFlow(false)
    val isInitialLoadComplete: StateFlow<Boolean> = _isInitialLoadComplete.asStateFlow()

    private val quickPicksEnum = context.dataStore.data.map {
        it[QuickPicksKey].toEnum(QuickPicks.QUICK_PICKS)
    }.distinctUntilChanged()

    private val _quickPicks = MutableStateFlow<List<Song>?>(null)
    val quickPicks: StateFlow<List<Song>?> = _quickPicks.asStateFlow()

    private val _speedDialItems = MutableStateFlow<List<LocalItem>>(emptyList())
    val speedDialItems: StateFlow<List<LocalItem>> = _speedDialItems.asStateFlow()

    private val _speedDialSongs = MutableStateFlow<List<Song>>(emptyList())
    val speedDialSongs: StateFlow<List<Song>> = _speedDialSongs.asStateFlow()

    private val _forgottenFavorites = MutableStateFlow<List<Song>?>(null)
    val forgottenFavorites: StateFlow<List<Song>?> = _forgottenFavorites.asStateFlow()

    private val _forgottenFavoritesDaysRemaining = MutableStateFlow<Int?>(null)
    val forgottenFavoritesDaysRemaining: StateFlow<Int?> = _forgottenFavoritesDaysRemaining.asStateFlow()

    private val _keepListening = MutableStateFlow<List<LocalItem>?>(null)
    val keepListening: StateFlow<List<LocalItem>?> = _keepListening.asStateFlow()

    private val _similarRecommendations = MutableStateFlow<List<SimilarRecommendation>?>(null)
    val similarRecommendations: StateFlow<List<SimilarRecommendation>?> = _similarRecommendations.asStateFlow()

    private val _homePage = MutableStateFlow<HomePage?>(null)
    val homePage: StateFlow<HomePage?> = _homePage.asStateFlow()

    val allItemsMetadata: StateFlow<Map<String, ItemMetadata>> = downloadUtil.downloads
        .map { downloads ->
            downloads.mapValues { (_, download) -> ItemMetadata(downloadState = download.state) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val accountImageUrl: StateFlow<String?> = MutableStateFlow<String?>(null).asStateFlow()

    private val getQuickPicksCount = java.util.concurrent.atomic.AtomicInteger(0)
    private val speedDialCount = java.util.concurrent.atomic.AtomicInteger(0)
    private val isFirstQuickPicksLogged = java.util.concurrent.atomic.AtomicBoolean(false)

    private suspend fun getQuickPicks(){
        val n = getQuickPicksCount.incrementAndGet()
        when (quickPicksEnum.first()) {
            QuickPicks.QUICK_PICKS -> {
                val fromTimeStamp = System.currentTimeMillis() - 86400000L * 30
                var mostPlayedRows = runCatching {
                    database.mostPlayedSongs(fromTimeStamp, limit = 20, offset = 0).first()
                }.getOrDefault(emptyList())

                if (mostPlayedRows.size < 10) {
                    mostPlayedRows = runCatching {
                        database.mostPlayedSongStatsAllTime(limit = 20, offset = 0).first()
                    }.getOrDefault(emptyList())
                }
                HomeTiming.log("getQuickPicks #$n: DB queries done")

                val metadataMap = com.ruben.spotitube.utils.HistoryMetadataLoader.resolve(mostPlayedRows.map { it.id }, timeoutMs = 5000L)
                HomeTiming.log("getQuickPicks #$n: resolve done (requested=${mostPlayedRows.size}, resolved=${metadataMap.size})")

                val mostPlayed: List<Song> = mostPlayedRows.mapNotNull { row ->
                    metadataMap[row.id]?.toSong()
                }

                val savedSongs: List<Song> = runCatching {
                    database.getRandomSavedSongs(20).map { it.toSong() }
                }.getOrDefault(emptyList())

                val localList: List<Song> = (mostPlayed + savedSongs).distinctBy { it.id }

                val ytSongs: List<Song> = _homePage.value?.sections
                    ?.flatMap { it.items }
                    ?.filterIsInstance<SongItem>()
                    ?.map { it.toSong() }
                    .orEmpty()

                val combined = mutableListOf<Song>()
                var localIndex = 0
                var ytIndex = 0

                while ((localIndex < localList.size || ytIndex < ytSongs.size) && combined.size < 20) {
                    if (localIndex < localList.size) {
                        combined.add(localList[localIndex++])
                    }
                    if (ytIndex < ytSongs.size && combined.size < 20) {
                        val ytSong = ytSongs[ytIndex++]
                        if (combined.none { it.id == ytSong.id }) {
                            combined.add(ytSong)
                        }
                    }
                }

                if (combined.size < 20) {
                    val lastSongId = runCatching { database.getLastHistorySongId() }.getOrNull()
                    if (!lastSongId.isNullOrBlank()) {
                        val nextSongs = runCatching {
                            YouTube.next(WatchEndpoint(videoId = lastSongId)).getOrNull()
                                ?.items
                                ?.filterIsInstance<SongItem>()
                                ?.map { it.toSong() }
                                .orEmpty()
                        }.getOrDefault(emptyList())
                        HomeTiming.log("getQuickPicks #$n: YouTube.next done")

                        for (song in nextSongs) {
                            if (combined.size >= 20) break
                            if (combined.none { it.id == song.id }) {
                                combined.add(song)
                            }
                        }
                    }
                }

                HomeTiming.log("getQuickPicks #$n: finalSize=${combined.size}")
                _quickPicks.value = combined.ifEmpty { null }
            }
            QuickPicks.LAST_LISTEN -> songLoad()
        }
    }

    private suspend fun loadSpeedDialSongs() {
        val n = speedDialCount.incrementAndGet()
        val speedDialRaw = context.dataStore.getAsync(SpeedDialSongIdsKey, "")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(24)

        if (speedDialRaw.isEmpty()) {
            HomeTiming.log("loadSpeedDialSongs #$n: totalIds=0, networkNeeded=0")
            _speedDialItems.value = emptyList()
            _speedDialSongs.value = emptyList()
            return
        }

        var networkNeededCount = 0
        val items = mutableListOf<LocalItem>()
        for (rawId in speedDialRaw) {
            when {
                rawId.startsWith("album:") -> {
                    val albumId = rawId.removePrefix("album:")
                    val savedAlbum = runCatching { database.getSavedAlbum(albumId)?.toAlbum() }.getOrNull()
                    if (savedAlbum != null) {
                        items.add(savedAlbum)
                    } else {
                        networkNeededCount++
                        YouTube.album(albumId).getOrNull()?.let { albumPage ->
                            items.add(albumPage.album.toAlbum())
                        }
                    }
                }
                rawId.startsWith("playlist:") -> {
                    val playlistId = rawId.removePrefix("playlist:")
                    val savedPlaylist = runCatching { database.getSavedPlaylist(playlistId)?.toPlaylist() }.getOrNull()
                    if (savedPlaylist != null) {
                        items.add(savedPlaylist)
                    } else {
                        networkNeededCount++
                        YouTube.playlist(playlistId).getOrNull()?.let { ytPlaylistPage ->
                            val playlistEntity = SavedPlaylistEntity(
                                id = ytPlaylistPage.playlist.id,
                                name = ytPlaylistPage.playlist.title,
                                browseId = ytPlaylistPage.playlist.id,
                                isEditable = false,
                                thumbnailUrl = ytPlaylistPage.playlist.thumbnail
                            )
                            items.add(playlistEntity.toPlaylist())
                        }
                    }
                }
                rawId.startsWith("song:") -> {
                    val songId = rawId.removePrefix("song:")
                    val savedSong = runCatching { database.getSavedSong(songId)?.toSong() }.getOrNull()
                    if (savedSong != null) {
                        items.add(savedSong)
                    }
                }
                else -> {
                    val savedSong = runCatching { database.getSavedSong(rawId)?.toSong() }.getOrNull()
                    if (savedSong != null) {
                        items.add(savedSong)
                    }
                }
            }
        }

        HomeTiming.log("loadSpeedDialSongs #$n: totalIds=${speedDialRaw.size}, networkNeeded=$networkNeededCount")
        _speedDialItems.value = items
        _speedDialSongs.value = items.filterIsInstance<Song>()
    }

    private suspend fun load() {
        if (_isLoading.value) return
        HomeTiming.log("HomeViewModel.load start")
        _isLoading.value = true
        
        try {
            supervisorScope {
                val hideVideo = context.dataStore.get(HideVideoKey, false)
                val fromTimeStamp = System.currentTimeMillis() - 86400000 * 7 * 2

                launch {
                    getQuickPicks()
                    HomeTiming.log("load: quickPicks end")
                }
                launch {
                    loadSpeedDialSongs()
                    HomeTiming.log("load: speedDial end")
                }
                launch {
                    _forgottenFavorites.value = database.forgottenFavorites().first().shuffled().take(20)
                    HomeTiming.log("load: forgottenFavorites end")
                }
                
                launch {
                    val keepListeningRows = database.mostPlayedSongs(fromTimeStamp, limit = 15, offset = 5)
                        .first().shuffled().take(10)
                    val keepListeningMap = com.ruben.spotitube.utils.HistoryMetadataLoader.resolve(keepListeningRows.map { it.id }, timeoutMs = 5000L)
                    val keepListeningSongs: List<Song> = keepListeningRows.mapNotNull { row -> keepListeningMap[row.id]?.toSong() }
                    val rawAlbums = database.mostPlayedAlbums(fromTimeStamp, limit = 8, offset = 2).first()
                    val albumsMap = com.ruben.spotitube.utils.HistoryMetadataLoader.resolveAlbums(rawAlbums.map { it.id }, timeoutMs = 5000L)
                    val keepListeningAlbums = rawAlbums.mapNotNull { albumObj ->
                        albumsMap[albumObj.id]?.let { meta ->
                            com.ruben.spotitube.db.entities.Album(
                                album = com.ruben.spotitube.db.entities.SavedAlbumEntity(
                                    id = albumObj.id,
                                    title = meta.title,
                                    artistName = meta.artistName,
                                    songCount = meta.songCount,
                                    thumbnailUrl = meta.thumbnailUrl
                                ),
                                artists = emptyList()
                            )
                        }
                    }.filter { it.album.thumbnailUrl != null }.shuffled().take(5)

                    val rawArtists = database.mostPlayedArtists(fromTimeStamp).first()
                    val artistsMap = com.ruben.spotitube.utils.HistoryMetadataLoader.resolveArtists(rawArtists.map { it.id }, timeoutMs = 5000L)
                    val keepListeningArtists = rawArtists.mapNotNull { artistObj ->
                        artistsMap[artistObj.id]?.let { meta ->
                            com.ruben.spotitube.db.entities.Artist(
                                artist = com.ruben.spotitube.db.entities.SavedArtistEntity(
                                    id = artistObj.id,
                                    name = meta.name,
                                    thumbnailUrl = meta.thumbnailUrl
                                ),
                                songCount = artistObj.songCount
                            )
                        }
                    }.filter { it.artist.isYouTubeArtist && it.artist.thumbnailUrl != null }.shuffled().take(5)
                    _keepListening.value = (keepListeningSongs + keepListeningAlbums + keepListeningArtists)
                        .distinctBy { item ->
                            when (item) {
                                is Song -> "song_${item.id}"
                                is com.ruben.spotitube.db.entities.Album -> "album_${item.id}"
                                is com.ruben.spotitube.db.entities.Artist -> "artist_${item.id}"
                                is com.ruben.spotitube.db.entities.Playlist -> "playlist_${item.id}"
                            }
                        }
                        .shuffled()
                    HomeTiming.log("load: keepListening end")
                }

                launch {
                    HomeTiming.log("Before YouTube.home(): gl=${YouTube.locale.gl}, visitorDataIsEmpty=${YouTube.visitorData.isNullOrBlank()}")
                    YouTube.home().onSuccess { page ->
                        _homePage.value = page.copy(
                            sections = page.sections.map { section ->
                                section.copy(items = section.items.filterVideo(hideVideo))
                            }
                        )
                    }.onFailure { reportException(it) }
                    HomeTiming.log("load: YouTube.home end")
                }
            }

            viewModelScope.launch(Dispatchers.IO) {
                loadSimilarRecommendations()
            }
                    
            _isInitialLoadComplete.value = true
            HomeTiming.log("_isInitialLoadComplete = true")
        } catch (e: Exception) {
            reportException(e)
        } finally {
            _isLoading.value = false
            HomeTiming.log("HomeViewModel.load end")
        }
    }

    private suspend fun loadSimilarRecommendations() {
        HomeTiming.log("loadSimilarRecommendations start")
        val hideVideo = context.dataStore.get(HideVideoKey, false)
        val fromTimeStamp = System.currentTimeMillis() - 86400000 * 7 * 2
        
        val rawArtistRecs = database.mostPlayedArtists(fromTimeStamp, limit = 10).first()
        val artistRecsMap = com.ruben.spotitube.utils.HistoryMetadataLoader.resolveArtists(rawArtistRecs.map { it.id }, timeoutMs = 5000L)
        val artistRecommendations = rawArtistRecs.mapNotNull { artistObj ->
            artistRecsMap[artistObj.id]?.let { meta ->
                com.ruben.spotitube.db.entities.Artist(
                    artist = com.ruben.spotitube.db.entities.SavedArtistEntity(
                        id = artistObj.id,
                        name = meta.name,
                        thumbnailUrl = meta.thumbnailUrl
                    ),
                    songCount = artistObj.songCount
                )
            }
        }.filter { it.artist.isYouTubeArtist }
            .shuffled().take(3)
            .mapNotNull {
                val items = mutableListOf<YTItem>()
                YouTube.artist(it.id).onSuccess { page ->
                    items += page.sections.getOrNull(page.sections.size - 2)?.items.orEmpty()
                    items += page.sections.lastOrNull()?.items.orEmpty()
                }
                SimilarRecommendation(
                    title = it,
                    items = items.filterVideo(hideVideo).shuffled().ifEmpty { return@mapNotNull null }
                )
            }

        val songRows = database.mostPlayedSongs(fromTimeStamp, limit = 10).first()
        val songMetadataMap = com.ruben.spotitube.utils.HistoryMetadataLoader.resolve(songRows.map { it.id }, timeoutMs = 5000L)
        val songRecommendations = songRows.mapNotNull { row -> songMetadataMap[row.id]?.toSong() }
            .filter { it.album != null }
            .shuffled().take(2)
            .mapNotNull { song ->
                val endpoint = YouTube.next(WatchEndpoint(videoId = song.id)).getOrNull()?.relatedEndpoint
                    ?: return@mapNotNull null
                val page = YouTube.related(endpoint).getOrNull() ?: return@mapNotNull null
                SimilarRecommendation(
                    title = song,
                    items = (page.songs.shuffled().take(8) +
                            page.albums.shuffled().take(4) +
                            page.artists.shuffled().take(4) +
                            page.playlists.shuffled().take(4))
                        .filterVideo(hideVideo)
                        .shuffled()
                        .ifEmpty { return@mapNotNull null }
                )
            }

        _similarRecommendations.value = (artistRecommendations + songRecommendations).shuffled()
        HomeTiming.log("loadSimilarRecommendations end")
    }

    private suspend fun songLoad() {
        val songId = database.getLastHistorySongId() ?: return
        val nextResult = com.ruben.spotitube.innertube.YouTube.next(com.ruben.spotitube.innertube.models.WatchEndpoint(videoId = songId)).getOrNull()
        val songs = nextResult?.items?.filterIsInstance<com.ruben.spotitube.innertube.models.SongItem>()?.map { it.toSong() }?.take(20) ?: emptyList()
        _quickPicks.value = songs.ifEmpty { null }
    }

    private val _isLoadingMore = MutableStateFlow(false)
    fun loadMoreYouTubeItems(continuation: String?) {
        if (continuation == null || _isLoadingMore.value) return
        val hideVideo = context.dataStore.get(HideVideoKey, false)

        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingMore.value = true
            val nextSections = YouTube.home(continuation).getOrNull() ?: run {
                _isLoadingMore.value = false
                return@launch
            }

            _homePage.value = nextSections.copy(
                chips = _homePage.value?.chips,
                sections = (_homePage.value?.sections.orEmpty() + nextSections.sections).map { section ->
                    section.copy(items = section.items.filterVideo(hideVideo))
                }
            )
            _isLoadingMore.value = false
        }
    }

    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshing.value = true
            load()
            _isRefreshing.value = false
        }
    }

    init {
        HomeTiming.log("HomeViewModel init")

        viewModelScope.launch(Dispatchers.IO) {
            _quickPicks.collectLatest { list ->
                if (list != null && isFirstQuickPicksLogged.compareAndSet(false, true)) {
                    HomeTiming.log("quickPicks first non-null value")
                }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            load()
        }

        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.data
                .map { it[SpeedDialSongIdsKey].orEmpty() }
                .distinctUntilChanged()
                .collect {
                    loadSpeedDialSongs()
                }
        }

        viewModelScope.launch(Dispatchers.IO) {
            database.firstEvent().collect { firstEvent ->
                if (firstEvent != null) {
                    val firstEventMs = firstEvent.playedAt
                    val daysElapsed = ((System.currentTimeMillis() - firstEventMs) / (1000L * 60 * 60 * 24)).toInt()
                    _forgottenFavoritesDaysRemaining.value = (30 - daysElapsed).coerceAtLeast(0)
                } else {
                    _forgottenFavoritesDaysRemaining.value = 30
                }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(3000.milliseconds)
            
            syncUtils.cleanupDuplicatePlaylists()
        }
    }
}
