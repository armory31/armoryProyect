/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ruben.spotitube.viewmodels

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ruben.spotitube.constants.ContentRegionGroupsKey
import com.ruben.spotitube.constants.HideVideoKey
import com.ruben.spotitube.constants.RegionGroup
import com.ruben.spotitube.constants.parseRegionGroups
import com.ruben.spotitube.constants.serializeRegionGroups
import com.ruben.spotitube.db.MusicDatabase
import com.ruben.spotitube.innertube.YouTube
import com.ruben.spotitube.innertube.models.AlbumItem
import com.ruben.spotitube.innertube.models.filterVideo
import com.ruben.spotitube.sync.LibraryStateHolder
import com.ruben.spotitube.utils.dataStore
import com.ruben.spotitube.utils.get
import com.ruben.spotitube.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

data class NewReleaseGroupSection(
    val group: RegionGroup,
    val albums: List<AlbumItem>,
    val singles: List<AlbumItem>,
    val eps: List<AlbumItem>,
) {
    val isEmpty: Boolean get() = albums.isEmpty() && singles.isEmpty() && eps.isEmpty()
}

data class NewReleaseContent(
    val sections: List<NewReleaseGroupSection>,
) {
    val isEmpty: Boolean get() = sections.all { it.isEmpty }
}

sealed interface NewReleaseUiState {
    data object Loading : NewReleaseUiState
    data class Success(val content: NewReleaseContent) : NewReleaseUiState
    data object Empty : NewReleaseUiState
    data class Error(val message: String, val throwable: Throwable? = null) : NewReleaseUiState
}

@HiltViewModel
class NewReleaseViewModel
@Inject
constructor(
    @ApplicationContext val context: Context,
    private val database: MusicDatabase,
    private val libraryStateHolder: LibraryStateHolder,
) : ViewModel() {

    private val _uiState = MutableStateFlow<NewReleaseUiState>(NewReleaseUiState.Loading)
    val uiState: StateFlow<NewReleaseUiState> = _uiState.asStateFlow()

    private val _groups = MutableStateFlow<List<RegionGroup>>(emptyList())
    val groups: StateFlow<List<RegionGroup>> = _groups.asStateFlow()

    val content: StateFlow<NewReleaseUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.data
                .map { parseRegionGroups(it[ContentRegionGroupsKey], context) }
                .distinctUntilChanged()
                .collect { newGroups ->
                    _groups.value = newGroups
                    load()
                }
        }
    }

    fun setGroups(list: List<RegionGroup>) {
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.edit { prefs ->
                prefs[ContentRegionGroupsKey] = serializeRegionGroups(list)
            }
        }
    }

    fun retry() {
        load()
    }

    private fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { NewReleaseUiState.Loading }
            try {
                val currentGroups = _groups.value
                if (currentGroups.isEmpty()) {
                    _uiState.update { NewReleaseUiState.Empty }
                    return@launch
                }

                val artistRanking = getArtistRanking()
                val favouriteArtistIds = artistRanking.favourites
                val allArtistRank = artistRanking.all
                val hideVideo = context.dataStore.get(HideVideoKey, false)

                val semaphore = Semaphore(3)
                val sections = mutableListOf<NewReleaseGroupSection>()
                var anySuccessfulCall = false

                currentGroups.forEach { group ->
                    val deferreds = group.codes.map { code ->
                        async {
                            semaphore.withPermit {
                                runCatching {
                                    YouTube.newReleasesCategorized(gl = code).getOrNull()
                                }.getOrNull()
                            }
                        }
                    }
                    val responses = deferreds.awaitAll().filterNotNull()
                    if (responses.isNotEmpty()) {
                        anySuccessfulCall = true
                    }

                    val mergedAlbums = sortByArtistRelevance(
                        responses.flatMap { it.albums }.distinctBy { it.id },
                        favouriteArtistIds,
                        allArtistRank
                    ).filterVideo(hideVideo)

                    val mergedSingles = sortByArtistRelevance(
                        responses.flatMap { it.singles }.distinctBy { it.id },
                        favouriteArtistIds,
                        allArtistRank
                    ).filterVideo(hideVideo)

                    val mergedEps = sortByArtistRelevance(
                        responses.flatMap { it.eps }.distinctBy { it.id },
                        favouriteArtistIds,
                        allArtistRank
                    ).filterVideo(hideVideo)

                    val groupSection = NewReleaseGroupSection(
                        group = group,
                        albums = mergedAlbums,
                        singles = mergedSingles,
                        eps = mergedEps,
                    )

                    if (!groupSection.isEmpty) {
                        sections.add(groupSection)
                        _uiState.update {
                            NewReleaseUiState.Success(NewReleaseContent(ArrayList(sections)))
                        }
                    }
                }

                if (sections.isEmpty()) {
                    _uiState.update {
                        if (!anySuccessfulCall) {
                            NewReleaseUiState.Error("Error al cargar novedades", null)
                        } else {
                            NewReleaseUiState.Empty
                        }
                    }
                }
            } catch (t: Throwable) {
                reportException(t)
                _uiState.update {
                    NewReleaseUiState.Error(
                        message = t.message ?: "Unknown error",
                        throwable = t
                    )
                }
            }
        }
    }

    private suspend fun getArtistRanking(): ArtistRanking {
        val allArtists = mutableMapOf<Int, String>()
        val favouriteArtists = mutableMapOf<Int, String>()

        val bookmarkedArtistIds = libraryStateHolder.savedArtistIds.first()

        database.allArtistsByPlayTime().first().let { list ->
            var favIndex = 0
            for ((artistsIndex, artist) in list.withIndex()) {
                allArtists[artistsIndex] = artist.id
                if (artist.id in bookmarkedArtistIds) {
                    favouriteArtists[favIndex] = artist.id
                    favIndex++
                }
            }
        }

        return ArtistRanking(
            favourites = favouriteArtists.values.toSet(),
            all = allArtists
        )
    }

    private fun sortByArtistRelevance(
        albums: List<AlbumItem>,
        favouriteArtistIds: Set<String>,
        allArtistRank: Map<Int, String>,
    ): List<AlbumItem> {
        return albums.sortedWith(
            compareBy<AlbumItem> { album ->
                val artistIds = album.artists.orEmpty().mapNotNull { it.id }
                artistIds.none { it in favouriteArtistIds }
            }.thenBy { album ->
                val artistIds = album.artists.orEmpty().mapNotNull { it.id }
                artistIds.minOfOrNull { artistId ->
                    allArtistRank.entries.firstOrNull { it.value == artistId }?.key ?: Int.MAX_VALUE
                } ?: Int.MAX_VALUE
            }.thenBy { album ->
                album.title.lowercase()
            }
        )
    }

    private data class ArtistRanking(
        val favourites: Set<String>,
        val all: Map<Int, String>,
    )
}
