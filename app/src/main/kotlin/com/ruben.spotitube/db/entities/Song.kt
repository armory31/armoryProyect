/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ruben.spotitube.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

@Immutable
data class Song
@JvmOverloads
constructor(
    @Embedded val song: SavedSongEntity,

    @Relation(
        entity = SavedArtistEntity::class,
        entityColumn = "id",
        parentColumn = "id",
        associateBy =
        Junction(
            value = SortedSongArtistMap::class,
            parentColumn = "songId",
            entityColumn = "artistId",
        ),
    )
    val artists: List<SavedArtistEntity>,

    @Relation(
        entity = SavedAlbumEntity::class,
        entityColumn = "id",
        parentColumn = "id",
        associateBy =
        Junction(
            value = SongAlbumMap::class,
            parentColumn = "songId",
            entityColumn = "albumId",
        ),
    )
    val album: SavedAlbumEntity? = null,

    @Relation(
        parentColumn = "id",
        entityColumn = "id"
    )
    val format: FormatEntity? = null,
) : LocalItem() {
    override val id: String
        get() = song.id
    override val title: String
        get() = song.title
    override val thumbnailUrl: String?
        get() = song.thumbnailUrl

    fun toSavedSongEntity() = song

    companion object {
        fun from(savedSong: SavedSongEntity): Song = savedSong.toSong()
    }
}
