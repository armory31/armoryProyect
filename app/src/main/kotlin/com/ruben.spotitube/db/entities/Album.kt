/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ruben.spotitube.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

@Immutable
data class Album(
    @Embedded
    val album: SavedAlbumEntity,
    @Relation(
        entity = SavedArtistEntity::class,
        entityColumn = "id",
        parentColumn = "id",
        associateBy =
        Junction(
            value = AlbumArtistMap::class,
            parentColumn = "albumId",
            entityColumn = "artistId",
        ),
    )
    val artists: List<SavedArtistEntity> = emptyList(),
    val songCountListened: Int? = 0,
    val timeListened: Long? = 0
) : LocalItem() {
    override val id: String
        get() = album.id
    override val title: String
        get() = album.title
    override val thumbnailUrl: String?
        get() = album.thumbnailUrl

    companion object {
        fun from(savedAlbum: SavedAlbumEntity): Album = savedAlbum.toAlbum()
    }
}
