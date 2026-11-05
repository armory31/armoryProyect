/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ruben.spotitube.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ruben.spotitube.constants.statToPeriod
import com.ruben.spotitube.db.MusicDatabase
import com.ruben.spotitube.db.entities.SongWithStats
import com.ruben.spotitube.sync.LibrarySummary
import com.ruben.spotitube.ui.screens.OptionStats
import com.ruben.spotitube.utils.HistoryMetadataLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel
@Inject
constructor(
    val database: MusicDatabase,
) : ViewModel() {
    val librarySummary = database.getLibrarySummary()
        .stateIn(viewModelScope, SharingStarted.Lazily, LibrarySummary())

    val selectedOption = MutableStateFlow(OptionStats.CONTINUOUS)
    val indexChips = MutableStateFlow(0)

    val mostPlayedSongsStats =
        combine(
            selectedOption,
            indexChips,
        ) { first, second -> Pair(first, second) }
            .flatMapLatest { (selection, t) ->
                database
                    .mostPlayedSongsStats(
                        fromTimeStamp = statToPeriod(selection, t),
                        limit = 20,
                        toTimeStamp =
                        if (selection == OptionStats.CONTINUOUS || t == 0) {
                            LocalDateTime
                                .now()
                                .toInstant(
                                    ZoneOffset.UTC,
                                ).toEpochMilli()
                        } else {
                            statToPeriod(selection, t - 1)
                        },
                    )
            }.mapLatest { rows ->
                val metadataMap = HistoryMetadataLoader.resolve(rows.map { it.id })
                rows.map { row ->
                    val item = metadataMap[row.id]
                    SongWithStats(
                        id = row.id,
                        title = item?.title ?: "ID: ${row.id}",
                        thumbnailUrl = item?.thumbnail ?: "",
                        songCountListened = row.songCountListened,
                        timeListened = row.timeListened,
                        songItem = item
                    )
                }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val mostPlayedArtists =
        combine(
            selectedOption,
            indexChips,
        ) { first, second -> Pair(first, second) }
            .flatMapLatest { (selection, t) ->
                database
                    .mostPlayedArtists(
                        statToPeriod(selection, t),
                        limit = 20,
                        toTimeStamp =
                        if (selection == OptionStats.CONTINUOUS || t == 0) {
                            LocalDateTime
                                .now()
                                .toInstant(
                                    ZoneOffset.UTC,
                                ).toEpochMilli()
                        } else {
                            statToPeriod(selection, t - 1)
                        },
                    )
            }.mapLatest { artists ->
                val metaMap = HistoryMetadataLoader.resolveArtists(artists.map { it.id })
                artists.map { artistObj ->
                    val meta = metaMap[artistObj.id]
                    com.ruben.spotitube.db.entities.Artist(
                        artist = com.ruben.spotitube.db.entities.SavedArtistEntity(
                            id = artistObj.id,
                            name = meta?.name ?: artistObj.artist.name,
                            thumbnailUrl = meta?.thumbnailUrl ?: artistObj.artist.thumbnailUrl
                        ),
                        songCount = artistObj.songCount
                    )
                }.filter { it.artist.isYouTubeArtist }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val mostPlayedAlbums =
        combine(
            selectedOption,
            indexChips,
        ) { first, second -> Pair(first, second) }
            .flatMapLatest { (selection, t) ->
                database.mostPlayedAlbums(
                    statToPeriod(selection, t),
                    limit = 20,
                    toTimeStamp =
                    if (selection == OptionStats.CONTINUOUS || t == 0) {
                        LocalDateTime
                            .now()
                            .toInstant(
                                ZoneOffset.UTC,
                            ).toEpochMilli()
                    } else {
                        statToPeriod(selection, t - 1)
                    },
                )
            }.mapLatest { albums ->
                val metaMap = HistoryMetadataLoader.resolveAlbums(albums.map { it.id })
                albums.map { albumObj ->
                    val meta = metaMap[albumObj.id]
                    com.ruben.spotitube.db.entities.Album(
                        album = com.ruben.spotitube.db.entities.SavedAlbumEntity(
                            id = albumObj.id,
                            title = meta?.title ?: albumObj.album.title,
                            artistName = meta?.artistName,
                            songCount = meta?.songCount ?: albumObj.album.songCount,
                            thumbnailUrl = meta?.thumbnailUrl ?: albumObj.album.thumbnailUrl
                        ),
                        artists = emptyList(),
                        songCountListened = albumObj.songCountListened
                    )
                }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val firstEvent =
        database
            .firstEvent()
            .stateIn(viewModelScope, SharingStarted.Lazily, null)


}
