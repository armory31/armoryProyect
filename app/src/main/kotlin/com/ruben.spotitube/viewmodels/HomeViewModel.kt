/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ruben.spotitube.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ruben.spotitube.constants.HideVideoKey
import com.ruben.spotitube.constants.InnerTubeCookieKey
import com.ruben.spotitube.constants.PlaylistSortType
import com.ruben.spotitube.constants.QuickPicks
import com.ruben.spotitube.constants.QuickPicksKey
import com.ruben.spotitube.constants.SpeedDialSongIdsKey
import com.ruben.spotitube.constants.YtmSyncKey
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
import com.ruben.spotitube.innertube.utils.parseCookieString
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import timber.log.Timber
import java.time.ZoneId
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
    val isRefreshing = MutableStateFlow(false)
    val isLoading = MutableStateFlow(false)
    val isInitialLoadComplete = MutableStateFlow(false)

    private val quickPicksEnum = context.dataStore.data.map {
        it[QuickPicksKey].toEnum(QuickPicks.QUICK_PICKS)
    }.distinctUntilChanged()

    val quickPicks = MutableStateFlow<List<Song>?>(null)
    val speedDialItems = MutableStateFlow<List<LocalItem>>(emptyList())
    val speedDialSongs = MutableStateFlow<List<Song>>(emptyList())
    val forgottenFavorites = MutableStateFlow<List<Song>?>(null)
    val forgottenFavoritesDaysRemaining = MutableStateFlow<Int?>(null)
    val keepListening = MutableStateFlow<List<LocalItem>?>(null)
    val similarRecommendations = MutableStateFlow<List<SimilarRecommendation>?>(null)
    val homePage = MutableStateFlow<HomePage?>(null)

    private val libraryMetadata = combine(
        combine(
            database.allSongs(),
            database.allArtistsByPlayTime(),
            database.playlists(PlaylistSortType.CREATE_DATE, true)
        ) { songs, artists, playlists ->
            Triple(songs, artists, playlists)
        },
        libraryStateHolder.savedSongIds,
        libraryStateHolder.savedAlbumIds,
        libraryStateHolder.savedArtistIds,
        libraryStateHolder.savedPlaylistIds
    ) { (songs, artists, playlists), likedSongIds, likedAlbumIds, bookmarkedArtistIds, likedPlaylistIds ->
        val metadataMap = mutableMapOf<String, ItemMetadata>()

        songs.forEach { song ->
            metadataMap[song.id] = ItemMetadata(
                isLiked = song.id in likedSongIds,
                isInLibrary = song.id in likedSongIds // Si está liked, está en la librería para efectos visuales
            )
        }

        artists.forEach { artist ->
            if (artist.id in bookmarkedArtistIds) {
                metadataMap[artist.id] = ItemMetadata(isLiked = true)
            }
        }

        playlists.forEach { playlist ->
            if (playlist.id in likedPlaylistIds) {
                metadataMap[playlist.id] = ItemMetadata(isLiked = true)
            }
        }

        // Add entries for liked items that might not be in the local database lists yet
        likedSongIds.forEach { id ->
            if (!metadataMap.containsKey(id)) {
                metadataMap[id] = ItemMetadata(isLiked = true, isInLibrary = true)
            }
        }

        likedAlbumIds.forEach { id ->
            if (!metadataMap.containsKey(id)) {
                metadataMap[id] = ItemMetadata(isLiked = true)
            }
        }

        bookmarkedArtistIds.forEach { id ->
            if (!metadataMap.containsKey(id)) {
                metadataMap[id] = ItemMetadata(isLiked = true)
            }
        }
        
        likedPlaylistIds.forEach { id ->
            if (!metadataMap.containsKey(id)) {
                metadataMap[id] = ItemMetadata(isLiked = true)
            }
        }

        metadataMap
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val allItemsMetadata = combine(
        libraryMetadata,
        downloadUtil.downloads
    ) { libraryMeta, downloads ->
        if (downloads.isEmpty()) return@combine libraryMeta

        val metadataMap = libraryMeta.toMutableMap()
        downloads.forEach { (id, download) ->
            val current = metadataMap[id] ?: ItemMetadata()
            metadataMap[id] = current.copy(downloadState = download.state)
        }
        metadataMap
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Account display info
    val accountName = MutableStateFlow("")
    val accountImageUrl = MutableStateFlow<String?>(null)
    
    // Track last processed cookie to avoid unnecessary updates
    private var lastProcessedCookie: String? = null
    
    // Track if we're currently processing account data
    private var isProcessingAccountData = false
    private var wasLoggedIn = false

    private suspend fun getQuickPicks(){
        when (quickPicksEnum.first()) {
            QuickPicks.QUICK_PICKS -> {
                val fromTimeStamp = System.currentTimeMillis() - 86400000L * 30
                val mostPlayed: List<Song> = runCatching {
                    database.mostPlayedSongs(fromTimeStamp, limit = 20, offset = 0).first()
                }.getOrDefault(emptyList())

                val savedSongs: List<Song> = runCatching {
                    database.allSongs().first().shuffled()
                }.getOrDefault(emptyList())

                val localList: List<Song> = (mostPlayed + savedSongs).distinctBy { it.id }

                val ytSongs: List<Song> = homePage.value?.sections
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
                quickPicks.value = combined.ifEmpty { null }
            }
            QuickPicks.LAST_LISTEN -> songLoad()
        }
    }

    private suspend fun loadSpeedDialSongs() {
        val speedDialRaw = context.dataStore.getAsync(SpeedDialSongIdsKey, "")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(24)

        if (speedDialRaw.isEmpty()) {
            speedDialItems.value = emptyList()
            speedDialSongs.value = emptyList()
            return
        }

        val allSavedSongsMap = runCatching { database.allSavedSongs().first().map { it.toSong() }.associateBy { it.id } }.getOrDefault(emptyMap())
        val allSavedAlbumsMap = runCatching { database.allSavedAlbums().first().map { it.toAlbum() }.associateBy { it.id } }.getOrDefault(emptyMap())
        val allSavedPlaylistsMap = runCatching { database.playlistsWithMetadata().first().associateBy { it.id } }.getOrDefault(emptyMap())

        val items = mutableListOf<LocalItem>()
        for (rawId in speedDialRaw) {
            when {
                rawId.startsWith("album:") -> {
                    val albumId = rawId.removePrefix("album:")
                    allSavedAlbumsMap[albumId]?.let { items.add(it) } ?: run {
                        YouTube.album(albumId).getOrNull()?.let { albumPage ->
                            items.add(albumPage.album.toAlbum())
                        }
                    }
                }
                rawId.startsWith("playlist:") -> {
                    val playlistId = rawId.removePrefix("playlist:")
                    allSavedPlaylistsMap[playlistId]?.let { items.add(it) } ?: run {
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
                    allSavedSongsMap[songId]?.let { items.add(it) }
                }
                else -> {
                    allSavedSongsMap[rawId]?.let { items.add(it) }
                }
            }
        }

        speedDialItems.value = items
        speedDialSongs.value = items.filterIsInstance<Song>()
    }

    private suspend fun load() {
        if (isLoading.value) return
        isLoading.value = true
        
        try {
            supervisorScope {
                val hideVideo = context.dataStore.get(HideVideoKey, false)
                val fromTimeStamp = System.currentTimeMillis() - 86400000 * 7 * 2

                launch { getQuickPicks() }
                launch { loadSpeedDialSongs() }
                launch { forgottenFavorites.value = database.forgottenFavorites().first().shuffled().take(20) }
                
                launch {
                    val keepListeningSongs = database.mostPlayedSongs(fromTimeStamp, limit = 15, offset = 5)
                        .first().shuffled().take(10)
                    val keepListeningAlbums = database.mostPlayedAlbums(fromTimeStamp, limit = 8, offset = 2)
                        .first().filter { it.album.thumbnailUrl != null }.shuffled().take(5)
                    val keepListeningArtists = database.mostPlayedArtists(fromTimeStamp)
                        .first().filter { it.artist.isYouTubeArtist && it.artist.thumbnailUrl != null }
                        .shuffled().take(5)
                    keepListening.value = (keepListeningSongs + keepListeningAlbums + keepListeningArtists).shuffled()
                }

                launch {
                    YouTube.home().onSuccess { page ->
                        homePage.value = page.copy(
                            sections = page.sections.map { section ->
                                section.copy(items = section.items.filterVideo(hideVideo))
                            }
                        )
                    }.onFailure { reportException(it) }
                }
            }

            viewModelScope.launch(Dispatchers.IO) {
                loadSimilarRecommendations()
            }
                    
            isInitialLoadComplete.value = true
        } catch (e: Exception) {
            reportException(e)
        } finally {
            isLoading.value = false
        }
    }

    private suspend fun loadSimilarRecommendations() {
        val hideVideo = context.dataStore.get(HideVideoKey, false)
        val fromTimeStamp = System.currentTimeMillis() - 86400000 * 7 * 2
        
        val artistRecommendations = database.mostPlayedArtists(fromTimeStamp, limit = 10).first()
            .filter { it.artist.isYouTubeArtist }
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

        val songRecommendations = database.mostPlayedSongs(fromTimeStamp, limit = 10).first()
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

        similarRecommendations.value = (artistRecommendations + songRecommendations).shuffled()
    }

    private suspend fun songLoad() {
        val lastHistory = database.allHistorySongs().first().firstOrNull()
        val song = lastHistory?.let { database.getSavedSong(it.songId)?.toSong() }
        if (song != null) {
            if (database.hasRelatedSongs(song.id).first()) {
                val relatedSongs = database.getRelatedSongs(song.id).first().shuffled().take(20)
                quickPicks.value = relatedSongs
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

            homePage.value = nextSections.copy(
                chips = homePage.value?.chips,
                sections = (homePage.value?.sections.orEmpty() + nextSections.sections).map { section ->
                    section.copy(items = section.items.filterVideo(hideVideo))
                }
            )
            _isLoadingMore.value = false
        }
    }

    fun refresh() {
        if (isRefreshing.value) return
        viewModelScope.launch(Dispatchers.IO) {
            isRefreshing.value = true
            load()
            isRefreshing.value = false
        }
    }

    init {
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
                    forgottenFavoritesDaysRemaining.value = (30 - daysElapsed).coerceAtLeast(0)
                } else {
                    forgottenFavoritesDaysRemaining.value = 30
                }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(3000.milliseconds)
            
            syncUtils.cleanupDuplicatePlaylists()
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.data
                .map { it[InnerTubeCookieKey] }
                .distinctUntilChanged()
                .collect { cookie ->
                    if (isProcessingAccountData) return@collect
                    
                    lastProcessedCookie = cookie
                    isProcessingAccountData = true
                    
                    try {
                        val isLoggedIn = cookie?.let { "SAPISID" in parseCookieString(it) } ?: false
                        val loginTransition = isLoggedIn && !wasLoggedIn
                        wasLoggedIn = isLoggedIn
                        
                        if (isLoggedIn && cookie.isNotEmpty()) {
                            try {
                                YouTube.cookie = cookie
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to set YouTube cookie")
                                return@collect
                            }

                            if (loginTransition) {
                                launch {
                                    try {
                                        if (context.dataStore.get(YtmSyncKey, true)) {
                                            syncUtils.performFullSync()
                                        }
                                    } catch (e: Exception) {
                                        Timber.e(e, "Error during login-triggered sync")
                                        reportException(e)
                                    }
                                }
                            }
                            
                            kotlinx.coroutines.delay(100.milliseconds)
                            
                            try {
                                YouTube.accountInfo().onSuccess { info ->
                                    accountName.value = info.name
                                    accountImageUrl.value = info.thumbnailUrl
                                }.onFailure { e ->
                                    Timber.w(e, "Failed to fetch account info")
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "Exception fetching account info")
                            }
                        } else {
                            accountName.value = ""
                            accountImageUrl.value = null
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error processing cookie change")
                        accountName.value = "Guest"
                        accountImageUrl.value = null
                    } finally {
                        isProcessingAccountData = false
                    }
                }
        }
    }
}
