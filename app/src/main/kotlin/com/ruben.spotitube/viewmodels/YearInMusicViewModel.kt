/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ruben.spotitube.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ruben.spotitube.db.MusicDatabase
import com.ruben.spotitube.db.entities.SavedAlbumEntity
import com.ruben.spotitube.db.entities.SavedArtistEntity
import com.ruben.spotitube.ui.utils.resize
import com.ruben.spotitube.innertube.YouTube
import com.ruben.spotitube.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
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
        val startYear = event?.playedAt?.let { LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(it), java.time.ZoneId.systemDefault()).year } ?: LocalDateTime.now().year
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

    val topSongsStats = selectedYear.flatMapLatest { year ->
        database.mostPlayedSongsStats(
            fromTimeStamp = getYearStartTimestamp(year),
            limit = 5,
            toTimeStamp = getYearEndTimestamp(year)
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val topSongs = selectedYear.flatMapLatest { year ->
        database.mostPlayedSongs(
            fromTimeStamp = getYearStartTimestamp(year),
            limit = 5,
            toTimeStamp = getYearEndTimestamp(year)
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val topArtists = selectedYear.flatMapLatest { year ->
        database.mostPlayedArtists(
            fromTimeStamp = getYearStartTimestamp(year),
            limit = 5,
            toTimeStamp = getYearEndTimestamp(year)
        ).map { artists ->
            artists.filter { it.artist.isYouTubeArtist }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val topAlbums = selectedYear.flatMapLatest { year ->
        database.mostPlayedAlbums(
            fromTimeStamp = getYearStartTimestamp(year),
            limit = 5,
            toTimeStamp = getYearEndTimestamp(year)
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val totalListeningTime = topSongsStats.map { songs ->
        songs.sumOf { stat -> stat.timeListened ?: 0L }
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0L)

    val totalSongsPlayed = topSongsStats.map { songs ->
        songs.sumOf { stat -> stat.songCountListened.toLong() }
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0L)

    init {
        viewModelScope.launch {
            topArtists.collect { artists ->
                artists
                    .map { it.artist }
                    .filter {
                        it.thumbnailUrl == null || Duration.between(
                            it.lastUpdateTime,
                            LocalDateTime.now()
                        ) > Duration.ofDays(10)
                    }.forEach { artist ->
                        YouTube.artist(artist.id).onSuccess { artistPage ->
                            database.query {
                                database.insertSavedArtist(
                                    SavedArtistEntity(
                                        id = artist.id,
                                        name = artistPage.artist.title,
                                        thumbnailUrl = artistPage.artist.thumbnail?.resize(544, 544),
                                        channelId = artist.channelId,
                                        savedAt = artist.bookmarkedAt ?: LocalDateTime.now()
                                    )
                                )
                            }
                        }
                    }
            }
        }

        viewModelScope.launch {
            topAlbums.collect { albums ->
                albums
                    .filter { it.album.songCount == 0 }
                    .forEach { album ->
                        YouTube
                            .album(album.id)
                            .onSuccess { albumPage ->
                                database.query {
                                    val albumArtist = album.artists.firstOrNull()
                                    database.insertSavedAlbum(
                                        SavedAlbumEntity(
                                            id = albumPage.album.browseId,
                                            title = albumPage.album.title,
                                            artistId = albumArtist?.id,
                                            artistName = albumArtist?.name,
                                            songCount = albumPage.songs.size,
                                            duration = albumPage.songs.sumOf { song -> song.duration ?: 0 },
                                            thumbnailUrl = albumPage.album.thumbnail,
                                            year = albumPage.album.year
                                        )
                                    )
                                }
                            }.onFailure {
                                reportException(it)
                                if (it.message?.contains("NOT_FOUND") == true) {
                                    database.query {
                                        database.deleteSavedAlbum(album.album.id)
                                    }
                                }
                            }
                    }
            }
        }
    }
}
