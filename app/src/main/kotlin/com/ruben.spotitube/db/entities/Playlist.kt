/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ruben.spotitube.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.Embedded

@Immutable
data class Playlist(
    @Embedded
    val playlist: PlaylistEntity,
    val songCount: Int = 0,
    val songThumbnails: List<String?> = emptyList(),
) : LocalItem() {
    override val id: String
        get() = playlist.id
    override val title: String
        get() = playlist.name
    override val thumbnailUrl: String?
        get() = playlist.thumbnailUrl ?: songThumbnails.filterNotNull().firstOrNull()
    
    val thumbnails: List<String>
        get() {
            return if (playlist.thumbnailUrl != null)
                listOf(playlist.thumbnailUrl)
            else songThumbnails.filterNotNull()
        }

    companion object {
        fun from(savedPlaylist: SavedPlaylistEntity): Playlist = savedPlaylist.toPlaylist()
    }
}
