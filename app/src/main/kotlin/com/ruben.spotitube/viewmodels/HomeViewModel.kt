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
import com.ruben.spotitube.db.entities.SavedArtistEntity
import com.ruben.spotitube.db.entities.SavedPlaylistEntity
import com.ruben.spotitube.db.entities.SavedSongEntity
import com.ruben.spotitube.db.entities.Song
import com.ruben.spotitube.extensions.toEnum
import com.ruben.spotitube.innertube.YouTube
import com.ruben.spotitube.innertube.models.ArtistItem
import com.ruben.spotitube.innertube.models.AlbumItem
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
import com.ruben.spotitube.utils.AlbumDateCache
import com.ruben.spotitube.utils.QuickPickEntry
import com.ruben.spotitube.utils.QuickPicksPoolBuilder
import com.ruben.spotitube.utils.QuickPicksPoolStore
import com.ruben.spotitube.utils.QuickPicksSelector
import com.ruben.spotitube.utils.SyncUtils
import com.ruben.spotitube.utils.dataStore
import com.ruben.spotitube.utils.get
import com.ruben.spotitube.utils.getAsync
import com.ruben.spotitube.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
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

    val continueListening: StateFlow<List<com.ruben.spotitube.models.ContinueListeningEntry>> = com.ruben.spotitube.models.ContinueListeningStore.entries

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

    private var lastExhausted = false
    private var currentEntries: List<QuickPickEntry> = emptyList()

    private suspend fun applyPoolQuickPicks(): Boolean = runCatching {
        var file = QuickPicksPoolStore.load(context)
        if (currentEntries.isEmpty() && file != null && !lastExhausted) {
            currentEntries = file.current
        }

        if (file == null || lastExhausted) {
            val built = QuickPicksPoolBuilder.build(context, database)
            if (built.isNotEmpty()) {
                QuickPicksPoolStore.save(context, built)
                file = QuickPicksPoolStore.load(context)
                currentEntries = emptyList()
                lastExhausted = false
            }
            if (built.isEmpty() && file == null) {
                return false
            }
        }

        if (file == null || file.entries.isEmpty()) {
            return false
        }

        val selection = QuickPicksSelector.next(file.entries, currentEntries)
        QuickPicksPoolStore.replaceEntries(context, selection.remainingPool, selection.batch, file.builtAt)
        currentEntries = selection.batch
        lastExhausted = selection.exhausted

        val songs = selection.batch.map { entry ->
            Song(
                song = SavedSongEntity(
                    id = entry.id,
                    title = entry.title,
                    artistId = entry.artistId,
                    artistName = entry.artistName,
                    duration = entry.duration,
                    thumbnailUrl = entry.thumbnailUrl,
                    albumId = null,
                    albumName = null,
                    explicit = false,
                ),
                artists = listOfNotNull(
                    entry.artistName?.let { name ->
                        SavedArtistEntity(
                            id = entry.artistId.orEmpty(),
                            name = name,
                        )
                    }
                ),
            )
        }

        _quickPicks.value = songs.ifEmpty { null }
        true
    }.getOrDefault(false)

    private suspend fun getQuickPicks(){
        val n = getQuickPicksCount.incrementAndGet()
        when (quickPicksEnum.first()) {
            QuickPicks.QUICK_PICKS -> {
                if (applyPoolQuickPicks()) return

                val savedSongs: List<Song> = runCatching {
                    com.ruben.spotitube.utils.PerfTiming.measureRoomQuery("Home.getRandomSavedSongs") {
                        database.getRandomSavedSongs(20).map { it.toSong() }
                    }
                }.getOrDefault(emptyList())

                val localList: List<Song> = savedSongs.distinctBy { it.id }

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

                HomeTiming.log("getQuickPicks #$n: finalSize=${combined.size}")
                _quickPicks.value = combined.ifEmpty { null }
            }
            QuickPicks.LAST_LISTEN -> Unit
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

                launch {
                    getQuickPicks()
                    HomeTiming.log("load: quickPicks end")
                }
                launch {
                    _forgottenFavorites.value = com.ruben.spotitube.utils.PerfTiming.measureRoomQuery("Home.forgottenFavorites") {
                        database.forgottenFavorites().first().shuffled().take(20)
                    }
                    HomeTiming.log("load: forgottenFavorites end")
                }
                
                launch {
                    _keepListening.value = emptyList()
                    HomeTiming.log("load: keepListening end")
                }

                launch {
                    HomeTiming.log("Before YouTube.home(): gl=${YouTube.locale.gl}, visitorDataIsEmpty=${YouTube.visitorData.isNullOrBlank()}")
                    com.ruben.spotitube.utils.PerfTiming.measureNetworkCallSuspend("YouTube.home") {
                        YouTube.home()
                    }.onSuccess { page ->
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

    private suspend fun buildArtistRecommendation(
        artist: com.ruben.spotitube.db.entities.Artist,
        hideVideo: Boolean
    ): SimilarRecommendation? {
        val items = mutableListOf<AlbumItem>()
        runCatching {
            YouTube.artist(artist.id).onSuccess { page ->
                items += page.sections.flatMap { it.items }
                    .filterIsInstance<AlbumItem>()
            }
        }
        val albums = items
            .distinctBy { it.id }
            .filterVideo(hideVideo)

        val cal = java.util.Calendar.getInstance()
        val curYear = cal.get(java.util.Calendar.YEAR)
        val cutoff = curYear * 12 + cal.get(java.util.Calendar.MONTH) - 5
        val filtered = albums.filter { album ->
            val y = album.year ?: return@filter false
            when {
                y == curYear -> true
                y == curYear - 1 && cutoff < curYear * 12 -> {
                    val idx = AlbumDateCache.monthIndex(AlbumDateCache.resolve(album.id, y))
                    idx != null && idx >= cutoff
                }
                else -> false
            }
        }

        if (filtered.isEmpty()) return null
        return SimilarRecommendation(
            title = artist,
            items = filtered
        )
    }

    private suspend fun loadSimilarRecommendations() {
        try {
            HomeTiming.log("loadSimilarRecommendations start")
            val hideVideo = context.dataStore.get(HideVideoKey, false)
            val savedArtists = database.allSavedArtists().first()
                .filter { it.isYouTubeArtist }
                .shuffled()
                .take(3)
            val artistRecommendations = savedArtists.mapNotNull { savedArtist ->
                buildArtistRecommendation(savedArtist.toArtist(), hideVideo)
            }

            _similarRecommendations.value = artistRecommendations
            HomeTiming.log("loadSimilarRecommendations end")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            reportException(e)
            _similarRecommendations.value = emptyList()
        } finally {
            if (_similarRecommendations.value == null) {
                _similarRecommendations.value = emptyList()
            }
        }
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
            kotlinx.coroutines.delay(3000.milliseconds)
            
            syncUtils.cleanupDuplicatePlaylists()
        }
    }
}
