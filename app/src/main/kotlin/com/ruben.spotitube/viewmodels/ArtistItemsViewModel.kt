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
import com.ruben.spotitube.constants.HideVideoKey
import com.ruben.spotitube.innertube.YouTube
import com.ruben.spotitube.innertube.models.BrowseEndpoint
import com.ruben.spotitube.innertube.models.filterVideo
import com.ruben.spotitube.models.ItemsPage
import com.ruben.spotitube.utils.dataStore
import com.ruben.spotitube.utils.get
import com.ruben.spotitube.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ArtistItemsViewModel
@Inject
constructor(
    @ApplicationContext val context: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val browseId = savedStateHandle.get<String>("browseId")!!
    private val params = savedStateHandle.get<String>("params")
    private val rawPassedTitle = savedStateHandle.get<String>("title")
    val passedTitle = rawPassedTitle?.let {
        runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrDefault(it)
    }

    val title = MutableStateFlow(passedTitle.orEmpty())
    val itemsPage = MutableStateFlow<ItemsPage?>(null)

    init {
        android.util.Log.d("ARMORY_DEBUG", "ArtistItemsViewModel init: browseId=$browseId, params=$params, passedTitle=$passedTitle")
        itemsPage.value = null
        viewModelScope.launch {
            YouTube
                .artistItems(
                    BrowseEndpoint(
                        browseId = browseId,
                        params = params,
                    ),
                ).onSuccess { artistItemsPage ->
                    android.util.Log.d("ARMORY_DEBUG", "ArtistItemsViewModel éxito: title='${artistItemsPage.title}', itemsCount=${artistItemsPage.items.size}")
                    if (artistItemsPage.title.isNotBlank()) {
                        title.value = artistItemsPage.title
                    }
                    itemsPage.value =
                        ItemsPage(
                            items = artistItemsPage.items
                                .distinctBy { it.id }
                                .filterVideo(context.dataStore.get(HideVideoKey, false)),
                            continuation = artistItemsPage.continuation,
                        )
                }.onFailure {
                    android.util.Log.e("ARMORY_DEBUG", "ArtistItemsViewModel fallo al cargar items para browseId=$browseId, params=$params", it)
                    reportException(it)
                    itemsPage.value = ItemsPage(items = emptyList(), continuation = null)
                }
        }
    }

    fun loadMore() {
        viewModelScope.launch {
            val oldItemsPage = itemsPage.value ?: return@launch
            val continuation = oldItemsPage.continuation ?: return@launch
            YouTube
                .artistItemsContinuation(continuation)
                .onSuccess { artistItemsContinuationPage ->
                    itemsPage.update {
                        ItemsPage(
                            items =
                            (oldItemsPage.items + artistItemsContinuationPage.items)
                                .distinctBy { it.id },
                            continuation = artistItemsContinuationPage.continuation,
                        )
                    }
                }.onFailure {
                    reportException(it)
                }
        }
    }
}
