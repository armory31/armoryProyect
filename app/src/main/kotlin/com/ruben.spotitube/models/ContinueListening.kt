package com.ruben.spotitube.models

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.ruben.spotitube.models.MediaMetadata

data class ContinueListeningEntry(
    val originType: String,
    val originId: String,
    val title: String,
    val thumbnailUrl: String?,
    val items: ArrayList<MediaMetadata>,
    val currentSongId: String,
    val updatedAt: Long,
) : java.io.Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

data class ContinueListeningState(
    val entries: ArrayList<ContinueListeningEntry> = ArrayList(),
) : java.io.Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

object ContinueListeningStore {
    private const val FILE = "continue_listening.data"
    private const val MAX_ENTRIES = 10
    private var store: com.ruben.spotitube.playback.PersistentStateStore? = null

    private val _entries = MutableStateFlow<List<ContinueListeningEntry>>(emptyList())
    val entries: StateFlow<List<ContinueListeningEntry>> = _entries

    fun init(filesDir: java.io.File) {
        if (store != null) return
        val persistentStore = com.ruben.spotitube.playback.PersistentStateStore(filesDir)
        store = persistentStore
        val state = persistentStore.readPersistentObject<ContinueListeningState>(FILE)
        _entries.value = state?.entries ?: emptyList()
    }

    @Synchronized
    fun upsert(entry: ContinueListeningEntry) {
        val currentStore = store ?: return
        val currentList = _entries.value

        val existing = currentList.firstOrNull { it.originType == entry.originType && it.originId == entry.originId }
        if (existing != null) {
            val sameCurrentSong = existing.currentSongId == entry.currentSongId
            val sameItems = existing.items.map { it.id } == entry.items.map { it.id }
            if (sameCurrentSong && sameItems) {
                return
            }
        }

        val updatedList = ArrayList<ContinueListeningEntry>()
        updatedList.add(entry)
        for (item in currentList) {
            if (item.originType != entry.originType || item.originId != entry.originId) {
                updatedList.add(item)
            }
        }

        val trimmedList = if (updatedList.size > MAX_ENTRIES) {
            ArrayList(updatedList.take(MAX_ENTRIES))
        } else {
            updatedList
        }

        _entries.value = trimmedList
        currentStore.writePersistentObject(FILE, ContinueListeningState(trimmedList))
    }

    @Synchronized
    fun clear() {
        val currentStore = store ?: return
        _entries.value = emptyList()
        currentStore.writePersistentObject(FILE, ContinueListeningState())
    }
}
