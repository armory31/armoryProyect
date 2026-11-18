/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ruben.spotitube.utils

import android.content.Context
import com.ruben.spotitube.constants.ContentRegionGroupsKey
import com.ruben.spotitube.constants.parseRegionGroups
import com.ruben.spotitube.db.MusicDatabase
import com.ruben.spotitube.innertube.YouTube
import com.ruben.spotitube.innertube.models.SongItem
import com.ruben.spotitube.innertube.pages.ChartsPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data class QuickPickEntry(
    val id: String,
    val title: String,
    val artistId: String?,
    val artistName: String?,
    val thumbnailUrl: String?,
    val duration: Int,
)

object QuickPicksPoolBuilder {
    suspend fun build(context: Context, database: MusicDatabase): List<QuickPickEntry> = withContext(Dispatchers.IO) {
        val semaphore = Semaphore(3)

        val savedArtistsDeferred = async {
            val savedArtists = runCatching {
                database.allSavedArtists().first().filter { it.isYouTubeArtist }
            }.getOrDefault(emptyList())

            val artistSongs = savedArtists.map { artistEntity ->
                async {
                    semaphore.withPermit {
                        runCatching {
                            val artistPage = YouTube.artist(artistEntity.id).getOrNull()
                            val section = artistPage?.sections?.firstOrNull { sec -> sec.items.any { it is SongItem } }
                            val itemsFromMore = section?.moreEndpoint?.let { endpoint ->
                                runCatching { YouTube.artistItems(endpoint).getOrNull()?.items }.getOrNull()
                            }
                            val rawItems = if (!itemsFromMore.isNullOrEmpty()) itemsFromMore else section?.items.orEmpty()
                            rawItems.filterIsInstance<SongItem>()
                                .distinctBy { it.id }
                                .take(20)
                        }.getOrDefault(emptyList())
                    }
                }
            }.awaitAll().flatten()

            artistSongs.distinctBy { it.id }
        }



        val groupsDataDeferred = async {
            val rawGroupsString = runCatching {
                context.dataStore.data.first()[ContentRegionGroupsKey]
            }.getOrNull()

            val groups = parseRegionGroups(rawGroupsString, context)

            val cSongs = mutableListOf<SongItem>()
            val dSongs = mutableListOf<SongItem>()

            for (group in groups) {
                val groupChartsDeferreds = group.codes.map { code ->
                    async {
                        semaphore.withPermit {
                            runCatching {
                                YouTube.getChartsPage(country = code).getOrNull()
                            }.getOrNull()
                        }
                    }
                }
                val groupChartsPages = groupChartsDeferreds.awaitAll().filterNotNull()
                val groupTrendingSongs = groupChartsPages
                    .flatMap { page -> page.sections }
                    .filter { it.chartType == ChartsPage.ChartType.TRENDING }
                    .flatMap { it.items }
                    .filterIsInstance<SongItem>()
                    .distinctBy { it.id }
                    .take(20)

                cSongs.addAll(groupTrendingSongs)

                val categorizedDeferreds = group.codes.map { code ->
                    async {
                        semaphore.withPermit {
                            runCatching {
                                YouTube.newReleasesCategorized(gl = code).getOrNull()
                            }.getOrNull()
                        }
                    }
                }
                val categorizedPages = categorizedDeferreds.awaitAll().filterNotNull()
                val combinedReleaseItems = categorizedPages.flatMap { page ->
                    page.albums + page.singles + page.eps
                }.distinctBy { it.id }

                val selectedReleases = combinedReleaseItems.shuffled().take(10)

                val albumSongDeferreds = selectedReleases.map { releaseItem ->
                    async {
                        semaphore.withPermit {
                            runCatching {
                                YouTube.album(releaseItem.browseId).getOrNull()?.songs?.randomOrNull()
                            }.getOrNull()
                        }
                    }
                }
                val releaseSongs = albumSongDeferreds.awaitAll().filterNotNull()
                dSongs.addAll(releaseSongs)
            }

            Pair(cSongs, dSongs)
        }

        val aSongs = savedArtistsDeferred.await()
        val (cSongs, dSongs) = groupsDataDeferred.await()

        (aSongs + cSongs + dSongs)
            .distinctBy { it.id }
            .map { song ->
                val firstArtist = song.artists?.firstOrNull()
                QuickPickEntry(
                    id = song.id,
                    title = song.title,
                    artistId = firstArtist?.id,
                    artistName = firstArtist?.name,
                    thumbnailUrl = song.thumbnail,
                    duration = song.duration ?: 0,
                )
            }
    }
}
