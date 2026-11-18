/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ruben.spotitube.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ruben.spotitube.R
import com.ruben.spotitube.constants.HideVideoKey
import com.ruben.spotitube.constants.PlaylistSongSortDescendingKey
import com.ruben.spotitube.constants.PlaylistSongSortType
import com.ruben.spotitube.constants.PlaylistSongSortTypeKey
import com.ruben.spotitube.db.MusicDatabase
import com.ruben.spotitube.db.entities.Song
import com.ruben.spotitube.extensions.reversed
import com.ruben.spotitube.extensions.toEnum
import com.ruben.spotitube.innertube.YouTube
import com.ruben.spotitube.innertube.models.YTItem
import com.ruben.spotitube.innertube.models.filterVideo
import com.ruben.spotitube.models.PlaylistSuggestion
import com.ruben.spotitube.sync.LibraryStateHolder
import com.ruben.spotitube.sync.SyncManager
import com.ruben.spotitube.utils.PlaylistSuggestionQueryBuilder
import com.ruben.spotitube.utils.dataStore
import com.ruben.spotitube.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class LocalPlaylistViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    libraryStateHolder: LibraryStateHolder,
    private val syncManager: SyncManager,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val playlistId = savedStateHandle.get<String>("playlistId")!!
    val playlist =
        database
            .playlist(playlistId)
            .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val isSaved = libraryStateHolder.isSavedPlaylist(playlistId)
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    val playlistSongs: StateFlow<List<Song>> =
        combine(
            database.playlistSongs(playlistId),
            context.dataStore.data
                .map {
                    it[PlaylistSongSortTypeKey].toEnum(PlaylistSongSortType.CUSTOM) to (it[PlaylistSongSortDescendingKey]
                        ?: true)
                }.distinctUntilChanged(),
        ) { songs, (sortType, sortDescending) ->
            when (sortType) {
                PlaylistSongSortType.CUSTOM -> songs
                PlaylistSongSortType.CREATE_DATE -> songs
                PlaylistSongSortType.NAME -> {
                    val collator = Collator.getInstance(Locale.getDefault())
                    collator.strength = Collator.PRIMARY
                    songs.sortedWith(compareBy(collator) { it.song.title })
                }
                PlaylistSongSortType.ARTIST -> {
                    val collator = Collator.getInstance(Locale.getDefault())
                    collator.strength = Collator.PRIMARY
                    songs.sortedWith(compareBy(collator) { song -> song.artists.joinToString("") { artist -> artist.name } })
                }
                PlaylistSongSortType.PLAY_TIME -> songs
            }.reversed(sortDescending && sortType != PlaylistSongSortType.CUSTOM)
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val playlistSongsDbOrder: StateFlow<List<Song>> =
        database
            .playlistSongs(playlistId)
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Playlist Suggestions State
    private val _playlistSuggestions = MutableStateFlow<PlaylistSuggestion?>(null)
    val playlistSuggestions = combine(_playlistSuggestions, playlistSongs) { suggestions, songs ->
        val songIds = songs.map { it.id }.toSet()
        val filteredItems = suggestions?.items?.filter { it.id !in songIds } ?: emptyList()
        suggestions?.copy(
            items = filteredItems
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)
    
    private val _isLoadingSuggestions = MutableStateFlow(false)
    val isLoadingSuggestions = _isLoadingSuggestions.asStateFlow()
    
    private val suggestionsCacheTimestamp = MutableStateFlow(0L)

    private var allPackItems: List<YTItem> = emptyList()
    private var shownCount = 10
    private val excludedSongIds = mutableSetOf<String>()
    
    // Mutex to prevent concurrent suggestion loading
    private val suggestionLoadMutex = Mutex()

    init {
        // Autoload suggestions when playlist or songs change
        viewModelScope.launch {
            combine(playlist, playlistSongs) { playlist, songs ->
                Pair(playlist, songs)
            }.collect { (playlist, songs) ->
                playlist?.let { 
                    loadPlaylistSuggestions()
                }
            }
        }
    }

    fun toggleSaved() {
        val currentPlaylist = playlist.value?.playlist ?: return
        viewModelScope.launch {
            syncManager.togglePlaylistSaved(currentPlaylist)
        }
    }
    
    // Playlist Suggestions Functions
    
    fun loadPlaylistSuggestions(forceReset: Boolean = false) {
        viewModelScope.launch {
            suggestionLoadMutex.withLock {
                val currentSongs = playlistSongs.first()
                val currentPlaylist = playlist.first() ?: return@withLock
                
                val shouldRefresh = forceReset || PlaylistSuggestionQueryBuilder.shouldRefreshSuggestions(
                    suggestionsCacheTimestamp.value
                )
                
                if (!shouldRefresh && _playlistSuggestions.value != null) {
                    return@withLock // Use cached suggestions
                }
                
                _isLoadingSuggestions.value = true
                
                _playlistSuggestions.value = null
                allPackItems = emptyList()
                shownCount = 10
                suggestionsCacheTimestamp.value = 0L

                try {
                    val queries = PlaylistSuggestionQueryBuilder.buildSuggestionQueries(
                        playlistName = currentPlaylist.playlist.name,
                        playlistSongs = currentSongs
                    )

                    if (queries.isEmpty()) {
                        _playlistSuggestions.value = PlaylistSuggestion(
                            items = emptyList(),
                            continuation = null,
                            currentQueryIndex = 0,
                            totalQueries = 0,
                            query = "",
                            hasMore = false
                        )
                        return@withLock
                    }
                    
                    val currentSongIds = currentSongs.map { it.id }.toSet()
                    val rawItems = coroutineScope {
                        queries.map { suggestionQuery ->
                            async {
                                YouTube.search(suggestionQuery.query, YouTube.SearchFilter.FILTER_SONG).getOrNull()?.items ?: emptyList()
                            }
                        }.awaitAll().flatten()
                    }

                    val filteredItems = filterSuggestionItems(rawItems, currentSongIds)
                    val shuffledPack = filteredItems.shuffled()

                    allPackItems = shuffledPack
                    shuffledPack.forEach { excludedSongIds.add(it.id) }

                    suggestionsCacheTimestamp.value = System.currentTimeMillis()
                    
                    _playlistSuggestions.value = PlaylistSuggestion(
                        items = allPackItems.take(shownCount),
                        continuation = null,
                        currentQueryIndex = 0,
                        totalQueries = queries.size,
                        query = queries.firstOrNull()?.query ?: "",
                        hasMore = allPackItems.size > shownCount
                    )
                } catch (e: Exception) {
                    reportException(e)
                    if (_playlistSuggestions.value == null) {
                        _playlistSuggestions.value = PlaylistSuggestion(
                            items = emptyList(),
                            continuation = null,
                            currentQueryIndex = 0,
                            totalQueries = 0,
                            query = "",
                            hasMore = false
                        )
                    }
                } finally {
                    _isLoadingSuggestions.value = false
                }
            }
        }
    }
    
    fun loadMoreSuggestions() {
        viewModelScope.launch {
            suggestionLoadMutex.withLock {
                if (_isLoadingSuggestions.value) return@withLock
                val currentSuggestions = _playlistSuggestions.value ?: return@withLock

                shownCount += 10
                val newItems = allPackItems.take(shownCount)
                _playlistSuggestions.value = currentSuggestions.copy(
                    items = newItems,
                    hasMore = allPackItems.size > shownCount
                )
            }
        }
    }
    
    fun resetAndLoadPlaylistSuggestions() {
        allPackItems = emptyList()
        shownCount = 10
        loadPlaylistSuggestions(forceReset = true)
    }
    
    /**
     * Mark a suggested song as added to prevent it from appearing again
     */
    fun markSuggestionAsAdded(songId: String) {
        excludedSongIds.add(songId)
        
        // Also remove from current suggestions list
        val currentSuggestions = _playlistSuggestions.value
        if (currentSuggestions != null) {
            _playlistSuggestions.value = currentSuggestions.copy(
                items = currentSuggestions.items.filter { it.id != songId }
            )
        }
    }
    
    suspend fun addSongToPlaylist(song: com.ruben.spotitube.innertube.models.SongItem, browseId: String?): Boolean {
        return try {
            if (browseId != null) {
                // Add to YouTube playlist
                withContext(Dispatchers.IO) {
                    YouTube.addToPlaylist(browseId, song.id)
                }
            }
            
            val added = database.withTransaction {
                // Ensure playlist exists in local database (it should, but just in case)
                val p = getPlaylistById(playlistId)
                if (p == null) {
                    // If not found, we can't add to it.
                    // This might happen if it's a special playlist that hasn't been created yet.
                    if (playlistId == com.ruben.spotitube.db.entities.PlaylistEntity.SAVED_PLAYLIST_ID) {
                        insertSavedPlaylist(
                            com.ruben.spotitube.db.entities.SavedPlaylistEntity(
                                id = playlistId,
                                name = context.getString(R.string.liked_songs),
                                isEditable = false
                            )
                        )
                        update(
                            com.ruben.spotitube.db.entities.PlaylistEntity(
                                id = playlistId,
                                name = context.getString(R.string.liked_songs),
                                isEditable = false,
                                bookmarkedAt = java.time.LocalDateTime.now()
                            )
                        )
                    } else {
                        return@withTransaction false
                    }
                }

                // Add to local playlist
                val item = com.ruben.spotitube.db.entities.PlaylistSongItem(
                    id = song.id,
                    title = song.title,
                    artistName = song.artists.joinToString(", ") { it.name }.ifEmpty { null },
                    thumbnailUrl = song.thumbnail,
                    duration = song.duration ?: -1,
                    albumId = song.album?.id,
                    albumName = song.album?.name,
                    explicit = song.explicit
                )
                addSongsToPlaylist(playlistId, listOf(item))
                true
            }
            
            if (!added) {
                return false
            }
            
            // Update excluded song IDs to avoid duplicates
            excludedSongIds.add(song.id)
            
            true
        } catch (e: Exception) {
            reportException(e)
            false
        }
    }

    private suspend fun filterSuggestionItems(items: List<YTItem>, currentSongIds: Set<String>): List<YTItem> {
        val hideVideos = context.dataStore.data.first()[HideVideoKey] ?: false

        var filteredItems = items
            .distinctBy { it.id }
            .filter { item ->
                item.id !in currentSongIds && item.id !in excludedSongIds
            }

        if (hideVideos) {
            filteredItems = filteredItems.filterVideo()
        }
        return filteredItems
    }
}
