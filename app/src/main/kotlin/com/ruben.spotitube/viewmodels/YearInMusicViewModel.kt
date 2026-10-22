/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ruben.spotitube.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ruben.spotitube.db.MusicDatabase
import com.ruben.spotitube.db.entities.HistoryRangeTotals
import com.ruben.spotitube.db.entities.SongWithStats
import com.ruben.spotitube.models.toSavedSongEntity
import com.ruben.spotitube.utils.HistoryMetadataLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class YearInMusicViewModel
@Inject
constructor(
    val database: MusicDatabase,
) : ViewModel() {

    val selectedYear = MutableStateFlow(LocalDateTime.now().year)

    val firstEvent = database
        .firstEvent()
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val availableYears = firstEvent.map { event ->
        val startYear = event?.playedAt?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()).year } ?: LocalDateTime.now().year
        val currentYear = LocalDateTime.now().year
        (currentYear downTo startYear).toList()
    }.stateIn(viewModelScope, SharingStarted.Lazily, listOf(LocalDateTime.now().year))

    private fun getYearStartTimestamp(year: Int): Long {
        return LocalDateTime.of(year, 1, 1, 0, 0, 0)
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli()
    }

    private fun getYearEndTimestamp(year: Int): Long {
        return LocalDateTime.of(year, 12, 31, 23, 59, 59)
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli()
    }

    val historyRangeTotals = selectedYear.flatMapLatest { year ->
        database.historyRangeTotals(
            fromTimeStamp = getYearStartTimestamp(year),
            toTimeStamp = getYearEndTimestamp(year)
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, HistoryRangeTotals(0L, 0))

    val topSongsStats = selectedYear.flatMapLatest { year ->
        database.mostPlayedSongsStats(
            fromTimeStamp = getYearStartTimestamp(year),
            limit = 10,
            toTimeStamp = getYearEndTimestamp(year)
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
                timeListened = row.timeListened
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val topSongs = selectedYear.flatMapLatest { year ->
        database.mostPlayedSongs(
            fromTimeStamp = getYearStartTimestamp(year),
            limit = 10,
            toTimeStamp = getYearEndTimestamp(year)
        )
    }.mapLatest { rows ->
        val metadataMap = HistoryMetadataLoader.resolve(rows.map { it.id })
        rows.mapNotNull { row ->
            metadataMap[row.id]?.toSavedSongEntity()?.toSong()
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val topArtists = selectedYear.flatMapLatest { year ->
        database.mostPlayedArtists(
            fromTimeStamp = getYearStartTimestamp(year),
            limit = 5,
            toTimeStamp = getYearEndTimestamp(year)
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

    val topAlbums = selectedYear.flatMapLatest { year ->
        database.mostPlayedAlbums(
            fromTimeStamp = getYearStartTimestamp(year),
            limit = 5,
            toTimeStamp = getYearEndTimestamp(year)
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

    val totalListeningTime = historyRangeTotals.map { it.totalTimeListened }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0L)

    val totalSongsPlayed = historyRangeTotals.map { it.totalSongsPlayed.toLong() }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0L)


}
