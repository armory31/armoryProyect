/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ruben.spotitube.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.Embedded

@Immutable
data class Artist(
    @Embedded
    val artist: SavedArtistEntity,
    val songCount: Int,
    val timeListened: Int? = 0,
) : LocalItem() {
    override val id: String
        get() = artist.id
    override val title: String
        get() = artist.name
    override val thumbnailUrl: String?
        get() = artist.thumbnailUrl

    companion object {
        fun from(savedArtist: SavedArtistEntity): Artist = savedArtist.toArtist()
    }
}
