/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ruben.spotitube.utils

import android.content.Context
import com.ruben.spotitube.db.MusicDatabase
import com.ruben.spotitube.sync.LibraryStateHolder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncUtils @Inject constructor(
    private val database: MusicDatabase,
    @Suppress("unused")
    @ApplicationContext private val context: Context,
    @Suppress("unused")
    private val libraryStateHolder: LibraryStateHolder
) {
    suspend fun cleanupDuplicatePlaylists() = withContext(Dispatchers.IO) {
        try {
            val allPlaylists = database.playlistsByNameAsc().first()
            val browseIdGroups = allPlaylists
                .filter { it.playlist.browseId != null }
                .groupBy { it.playlist.browseId }
            
            for ((browseId, playlists) in browseIdGroups) {
                if (playlists.size > 1) {
                    Timber.w("Found ${playlists.size} duplicate playlists for browseId: $browseId")
                    val toKeep = playlists.maxByOrNull { it.songCount }
                        ?: playlists.first()
                    
                    playlists.filter { it.id != toKeep.id }.forEach { duplicate ->
                        Timber.d("Removing duplicate playlist: ${duplicate.playlist.name} (${duplicate.id})")
                        database.clearPlaylist(duplicate.id)
                        database.deleteSavedPlaylist(duplicate.id)
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error cleaning up duplicate playlists")
        }
    }
}
