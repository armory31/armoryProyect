/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ruben.spotitube.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import org.apache.commons.lang3.RandomStringUtils
import java.time.LocalDateTime

@Immutable
@Entity(tableName = "artist")
data class ArtistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val thumbnailUrl: String? = null,
    val channelId: String? = null,
    val lastUpdateTime: LocalDateTime = LocalDateTime.now(),
    @ColumnInfo(name = "isLocal", defaultValue = "0")
    val isLocal: Boolean = false,
    @ColumnInfo(name = "isBookmarked", defaultValue = "0")
    val isBookmarked: Boolean = false,
    val bookmarkedAt: LocalDateTime? = null
) {
    val isYouTubeArtist: Boolean
        get() = id.startsWith("UC") || id.startsWith("FEmusic_library_privately_owned_artist")

    val isPrivatelyOwnedArtist: Boolean
        get() = id.startsWith("FEmusic_library_privately_owned_artist")

    companion object {
        fun generateArtistId() = "LA" + RandomStringUtils.insecure().next(8, true, false)
    }
}
