/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ruben.spotitube.viewmodels


import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ruben.spotitube.constants.ArtistSongSortType
import com.ruben.spotitube.constants.HideExplicitKey
import com.ruben.spotitube.db.MusicDatabase
import com.ruben.spotitube.db.entities.SavedArtistEntity
import com.ruben.spotitube.innertube.YouTube
import com.ruben.spotitube.innertube.pages.ArtistPage
import com.ruben.spotitube.sync.LibraryStateHolder
import com.ruben.spotitube.sync.SyncManager
import com.ruben.spotitube.utils.dataStore
import com.ruben.spotitube.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ArtistViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
    libraryStateHolder: LibraryStateHolder,
    private val syncManager: SyncManager
) : ViewModel() {
    val artistId = savedStateHandle.get<String>("artistId")!!
    var artistPage by mutableStateOf<ArtistPage?>(null)
    
    val isBookmarked = libraryStateHolder.isSavedArtist(artistId)
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    val libraryArtist = database.artist(artistId)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)
    val librarySongs = context.dataStore.data
        .map { it[HideExplicitKey] ?: false }
        .distinctUntilChanged()
        .flatMapLatest { hideExplicit ->
            database.artistSongs(artistId, ArtistSongSortType.CREATE_DATE, false)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val libraryAlbums = context.dataStore.data
        .map { it[HideExplicitKey] ?: false }
        .distinctUntilChanged()
        .flatMapLatest { hideExplicit ->
            database.artistAlbumsPreview(artistId)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        // Load artist page and reload when hide explicit setting changes
        viewModelScope.launch {
            context.dataStore.data
                .map { it[HideExplicitKey] ?: false }
                .distinctUntilChanged()
                .collect {
                    fetchArtistsFromYTM()
                }
        }
    }

    fun fetchArtistsFromYTM() {
        viewModelScope.launch(Dispatchers.IO) {
            var targetId = artistId
            if (!targetId.startsWith("UC") && !targetId.startsWith("FE")) {
                val dbArtist = database.getArtistById(artistId) ?: database.artistByName(artistId)
                val chId = dbArtist?.channelId
                if (!chId.isNullOrBlank() && (chId.startsWith("UC") || chId.startsWith("FE"))) {
                    targetId = chId
                }
            }

            YouTube.artist(targetId)
                .onSuccess { page ->
                    artistPage = page
                }.onFailure {
                    reportException(it)
                }
        }
    }

    fun toggleLike() {
        Timber.d("[LOCAL_SAVE_DEBUG] ArtistViewModel: Intentando cambiar estado de suscripción para ${artistPage?.artist?.title ?: "local artist"}")
        val artist = libraryArtist.value?.artist ?: artistPage?.artist?.let {
            SavedArtistEntity(
                id = it.id,
                name = it.title,
                thumbnailUrl = it.thumbnail,
                channelId = it.channelId,
                isBookmarked = false
            )
        } ?: return

        viewModelScope.launch {
            syncManager.toggleArtistLike(artist)
        }
    }
}
