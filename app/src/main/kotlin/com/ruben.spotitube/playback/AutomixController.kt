package com.ruben.spotitube.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player.STATE_IDLE
import androidx.media3.exoplayer.ExoPlayer
import com.ruben.spotitube.R
import com.ruben.spotitube.constants.AutoLoadMoreKey
import com.ruben.spotitube.constants.HideVideoKey
import com.ruben.spotitube.extensions.SilentHandler
import com.ruben.spotitube.extensions.currentMetadata
import com.ruben.spotitube.extensions.toMediaItem
import com.ruben.spotitube.innertube.YouTube
import com.ruben.spotitube.innertube.models.WatchEndpoint
import com.ruben.spotitube.playback.queues.filterVideo
import com.ruben.spotitube.utils.dataStore
import com.ruben.spotitube.utils.get
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AutomixController(
    private val context: Context,
    private val player: () -> ExoPlayer,
    private val scope: () -> CoroutineScope,
    private val isSuppressed: () -> Boolean,
    private val addToQueue: (List<MediaItem>) -> Unit,
    private val playNext: (List<MediaItem>) -> Unit,
) {
    val automixItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val automixLoading = MutableStateFlow(false)
    val automixError = MutableStateFlow<String?>(null)
    private var automixJob: Job? = null
    var automixSeedMediaId: String? = null

    val autoAddedMediaIds: MutableSet<String> = java.util.Collections.synchronizedSet(mutableSetOf())

    fun getAutomixAlbum(albumId: String) {
        scope().launch(Dispatchers.IO + SilentHandler) {
            YouTube
                .album(albumId)
                .onSuccess {
                    getAutomix(it.album.playlistId)
                }
        }
    }

    fun getAutomix(playlistId: String) {
        if (context.dataStore.get(AutoLoadMoreKey, true) &&
            player().repeatMode == androidx.media3.common.Player.REPEAT_MODE_OFF) {
            scope().launch(Dispatchers.IO + SilentHandler) {
                val seedAtRequest =
                    withContext(Dispatchers.Main) {
                        player().currentMetadata?.id?.trim()?.takeIf { it.isNotBlank() }
                    }
                YouTube
                    .next(WatchEndpoint(playlistId = playlistId))
                    .onSuccess {
                        YouTube
                            .next(WatchEndpoint(playlistId = it.endpoint.playlistId))
                            .onSuccess { it ->
                                val mediaItems = it.items.map { song -> song.toMediaItem() }
                                withContext(Dispatchers.Main) {
                                    val currentSeed =
                                        player().currentMetadata?.id?.trim()?.takeIf { it.isNotBlank() }
                                    if (seedAtRequest != null && currentSeed != seedAtRequest) return@withContext
                                    automixItems.value = mediaItems
                                    automixSeedMediaId = currentSeed
                                }
                            }
                    }
            }
        }
    }

    fun addToQueueAutomix(
        item: MediaItem,
        position: Int,
    ) {
        automixItems.value =
            automixItems.value.toMutableList().apply {
                removeAt(position)
            }
        addToQueue(listOf(item))
    }

    fun playNextAutomix(
        item: MediaItem,
        position: Int,
    ) {
        automixItems.value =
            automixItems.value.toMutableList().apply {
                removeAt(position)
            }
        playNext(listOf(item))
    }

    fun clearAutomix() {
        automixJob?.cancel()
        automixJob = null
        automixItems.value = emptyList()
        automixLoading.value = false
        automixError.value = null
        automixSeedMediaId = null
        autoAddedMediaIds.clear()
    }

    fun refreshAutomixForCurrentMedia(force: Boolean) {
        if (!context.dataStore.get(AutoLoadMoreKey, true)) return
        if (player().repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF) return
        if (isSuppressed()) return
        if (player().mediaItemCount == 0) return

        val currentMeta = player().currentMetadata ?: return
        val seedMediaId = currentMeta.id.trim().ifBlank { return }

        if (!force && automixSeedMediaId == seedMediaId && automixItems.value.isNotEmpty() && automixJob?.isActive == true) return

        automixJob?.cancel()
        automixJob = null
        automixItems.value = emptyList()
        automixLoading.value = true
        automixError.value = null
        automixSeedMediaId = seedMediaId

        val hideVideo = context.dataStore.get(HideVideoKey, false)

        automixJob = scope().launch {
            try {
                val nextResult = withContext(Dispatchers.IO) {
                    YouTube.next(WatchEndpoint(videoId = seedMediaId))
                }

                nextResult
                    .onSuccess { result ->
                        if (automixSeedMediaId != seedMediaId) {
                            automixLoading.value = false
                            return@onSuccess
                        }

                        val queueIds =
                            (0 until player().mediaItemCount)
                                .map { player().getMediaItemAt(it).mediaId }
                                .toSet()

                        val fromNext =
                            result.items
                                .map { it.toMediaItem() }
                                .filter { it.mediaId !in queueIds }
                                .filterVideo(hideVideo)

                        val relatedCandidates =
                            result.relatedEndpoint
                                ?.let { endpoint ->
                                    withContext(Dispatchers.IO) { YouTube.related(endpoint) }
                                        .getOrNull()
                                        ?.songs
                                        .orEmpty()
                                }
                                .orEmpty()

                        val related =
                            relatedCandidates
                                .map { it.toMediaItem() }
                                .filter { it.mediaId !in queueIds }
                                .filterVideo(hideVideo)

                        val poolBase =
                            (fromNext + related)
                                .asSequence()
                                .distinctBy { it.mediaId }
                                .take(50)
                                .toList()

                        val pool =
                            if (poolBase.size >= 25 || result.endpoint.playlistId.isNullOrBlank()) {
                                poolBase
                            } else {
                                val playlistId = result.endpoint.playlistId
                                val extra =
                                    withContext(Dispatchers.IO) {
                                        YouTube.next(WatchEndpoint(playlistId = playlistId))
                                    }.getOrNull()
                                        ?.items
                                        .orEmpty()
                                        .map { it.toMediaItem() }
                                        .filter { it.mediaId !in queueIds }
                                        .filterVideo(hideVideo)

                                (poolBase + extra)
                                    .asSequence()
                                    .distinctBy { it.mediaId }
                                    .take(75)
                                    .toList()
                            }

                        if (automixSeedMediaId != seedMediaId) {
                            automixLoading.value = false
                            return@onSuccess
                        }

                        automixItems.value = pool
                        if (pool.isEmpty()) {
                            automixError.value = context.getString(R.string.error_no_similar_songs)
                        }
                        automixLoading.value = false
                    }
                    .onFailure { throwable ->
                        if (automixSeedMediaId == seedMediaId) {
                            automixLoading.value = false
                            automixError.value =
                                throwable.localizedMessage ?: context.getString(R.string.error_automix_failed)
                        }
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (automixSeedMediaId == seedMediaId) {
                    automixLoading.value = false
                    automixError.value = e.localizedMessage ?: context.getString(R.string.error_automix_failed)
                }
            }
        }
    }

    fun onInfiniteQueueDisabled() {
        automixJob?.cancel()
        automixJob = null
        automixLoading.value = false
        automixError.value = null
        val currentIndex = player().currentMediaItemIndex
        val idsToRemove = synchronized(autoAddedMediaIds) { autoAddedMediaIds.toSet() }
        if (idsToRemove.isEmpty()) {
            clearAutomix()
            return
        }
        for (i in player().mediaItemCount - 1 downTo 0) {
            if (i == currentIndex) continue
            val item = player().getMediaItemAt(i)
            if (item.mediaId in idsToRemove) {
                player().removeMediaItem(i)
            }
        }
        autoAddedMediaIds.clear()
        clearAutomix()
    }

    fun onInfiniteQueueEnabled() {
        val currentMeta = player().currentMetadata
        if (currentMeta == null) {
            automixError.value = context.getString(R.string.error_no_song_playing)
            return
        }

        automixJob?.cancel()
        automixLoading.value = true
        automixError.value = null
        automixItems.value = emptyList()
        automixSeedMediaId = currentMeta.id.trim().ifBlank { null }

        val hideVideo = context.dataStore.get(HideVideoKey, false)

        automixJob = scope().launch {
            try {
                val nextResult = withContext(Dispatchers.IO) {
                    YouTube.next(WatchEndpoint(videoId = currentMeta.id))
                }

                nextResult
                    .onSuccess { result ->
                        if (isSuppressed() || player().playbackState == STATE_IDLE || player().mediaItemCount == 0) {
                            automixLoading.value = false
                            return@onSuccess
                        }
                        val initialQueueIds = (0 until player().mediaItemCount).map { player().getMediaItemAt(it).mediaId }.toSet()
                        val filteredFromNext =
                            result.items
                                .map { it.toMediaItem() }
                                .filter { it.mediaId !in initialQueueIds }
                                .filterVideo(hideVideo)

                        val addedNow = ArrayList<MediaItem>(32)

                        if (filteredFromNext.isNotEmpty()) {
                            val toAdd = filteredFromNext.take(25)
                            player().addMediaItems(toAdd)
                            toAdd.forEach { autoAddedMediaIds.add(it.mediaId) }
                            addedNow.addAll(toAdd)
                        }

                        val queueIdsAfterNext = (0 until player().mediaItemCount).map { player().getMediaItemAt(it).mediaId }.toSet()
                        val relatedCandidates =
                            result.relatedEndpoint?.let { relatedEndpoint ->
                                withContext(Dispatchers.IO) {
                                    YouTube.related(relatedEndpoint)
                                }.getOrNull()?.songs.orEmpty()
                            }.orEmpty()

                        val filteredRelated =
                            relatedCandidates
                                .map { it.toMediaItem() }
                                .filter { it.mediaId !in queueIdsAfterNext }
                                .filterVideo(hideVideo)

                        if (addedNow.isEmpty() && filteredRelated.isNotEmpty()) {
                            val toAdd = filteredRelated.take(25)
                            player().addMediaItems(toAdd)
                            toAdd.forEach { autoAddedMediaIds.add(it.mediaId) }
                            addedNow.addAll(toAdd)
                        }

                        val queueIdsAfterAdds = (0 until player().mediaItemCount).map { player().getMediaItemAt(it).mediaId }.toSet()
                        val playlistId = result.endpoint.playlistId
                        val automixCandidates =
                            if (playlistId.isNullOrBlank()) {
                                emptyList()
                            } else {
                                withContext(Dispatchers.IO) {
                                    YouTube.next(WatchEndpoint(playlistId = playlistId))
                                }.getOrNull()?.items.orEmpty()
                            }

                        val filteredAutomix =
                            automixCandidates
                                .map { it.toMediaItem() }
                                .filter { it.mediaId !in queueIdsAfterAdds }
                                .filterVideo(hideVideo)

                        val addedIds = addedNow.map { it.mediaId }.toSet()
                        val pool =
                            (filteredFromNext + filteredRelated + filteredAutomix)
                                .asSequence()
                                .distinctBy { it.mediaId }
                                .filter { it.mediaId !in addedIds }
                                .take(75)
                                .toList()

                        automixItems.value = pool

                        if (addedNow.isEmpty() && pool.isEmpty()) {
                            automixError.value = context.getString(R.string.error_no_similar_songs)
                        }
                        automixLoading.value = false
                    }
                    .onFailure { throwable ->
                        automixLoading.value = false
                        automixError.value = throwable.localizedMessage ?: context.getString(R.string.error_automix_failed)
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                automixLoading.value = false
                automixError.value = e.localizedMessage ?: context.getString(R.string.error_automix_failed)
            }
        }
    }
}
