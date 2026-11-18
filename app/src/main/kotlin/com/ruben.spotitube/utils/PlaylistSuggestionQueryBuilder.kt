/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */


package com.ruben.spotitube.utils

import com.ruben.spotitube.db.entities.Song
import com.ruben.spotitube.models.PlaylistSuggestionQuery

object PlaylistSuggestionQueryBuilder {

    fun buildSuggestionQueries(
        playlistName: String,
        playlistSongs: List<Song>
    ): List<PlaylistSuggestionQuery> {
        val queries = mutableListOf<PlaylistSuggestionQuery>()

        val artists = extractArtists(playlistSongs)
        artists.take(3).forEachIndexed { index, artist ->
            queries.add(PlaylistSuggestionQuery("songs like $artist", index + 1))
        }

        return queries
    }

    private fun extractArtists(playlistSongs: List<Song>): List<String> {
        return playlistSongs
            .flatMap { it.artists }
            .map { it.name }
            .groupBy { it }
            .mapValues { it.value.size }
            .entries
            .sortedByDescending { it.value }
            .map { it.key }
            .filter { it.isNotBlank() }
            .distinct()
            .shuffled()
            .take(5)
    }

    fun shouldRefreshSuggestions(lastTimestamp: Long, cacheExpiryHours: Long = 12): Boolean {
        val currentTime = System.currentTimeMillis()
        val timeDiff = currentTime - lastTimestamp
        val expiryTime = cacheExpiryHours * 60 * 60 * 1000 // Convert hours to milliseconds

        return timeDiff > expiryTime
    }
}
