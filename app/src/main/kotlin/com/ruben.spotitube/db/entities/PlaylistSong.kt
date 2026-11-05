/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ruben.spotitube.db.entities

import androidx.compose.runtime.Immutable

@Immutable
data class PlaylistSong(
    val playlistId: String,
    val song: Song,
    val position: Int = 0
)
