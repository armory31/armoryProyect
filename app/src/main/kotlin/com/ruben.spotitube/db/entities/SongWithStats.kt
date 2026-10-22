/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ruben.spotitube.db.entities

import androidx.compose.runtime.Immutable
import com.ruben.spotitube.innertube.models.SongItem

@Immutable
data class SongWithStats(
    val id: String,
    val title: String,
    val thumbnailUrl: String,
    val songCountListened: Int,
    val timeListened: Long?,
    val songItem: SongItem? = null,
)

@Immutable
data class SongStatRow(
    val id: String,
    val songCountListened: Int,
    val timeListened: Long,
)

@Immutable
data class ArtistStatRow(
    val artistId: String,
    val playCount: Int,
)

@Immutable
data class AlbumStatRow(
    val albumId: String,
    val playCount: Int,
)

@Immutable
data class HistoryRangeTotals(
    val totalTimeListened: Long,
    val totalSongsPlayed: Int,
)
