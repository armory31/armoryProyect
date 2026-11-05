/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ruben.spotitube.innertube.pages

import com.ruben.spotitube.innertube.models.Album
import com.ruben.spotitube.innertube.models.AlbumItem
import com.ruben.spotitube.innertube.models.Artist
import com.ruben.spotitube.innertube.models.MusicResponsiveHeaderRenderer
import com.ruben.spotitube.innertube.models.MusicResponsiveListItemRenderer
import com.ruben.spotitube.innertube.models.SongItem
import com.ruben.spotitube.innertube.models.getItems
import com.ruben.spotitube.innertube.models.oddElements
import com.ruben.spotitube.innertube.models.response.BrowseResponse
import com.ruben.spotitube.innertube.models.splitBySeparator
import com.ruben.spotitube.innertube.utils.parseTime

data class AlbumPage(
    val album: AlbumItem,
    val songs: List<SongItem>,
    val otherVersions: List<AlbumItem>,
) {
    companion object {
        fun getPlaylistId(response: BrowseResponse): String? {
            var playlistId = response.microformat?.microformatDataRenderer?.urlCanonical?.substringAfterLast('=')
            if (playlistId == null)
            {
                playlistId = response.header?.musicDetailHeaderRenderer?.menu?.menuRenderer?.topLevelButtons?.firstOrNull()
                    ?.buttonRenderer?.navigationEndpoint?.watchPlaylistEndpoint?.playlistId
            }
            return playlistId
        }

        fun getTitle(response: BrowseResponse): String? {
            val title = getHeader(response)?.title ?: response.header?.musicDetailHeaderRenderer?.title
            return title?.runs?.firstOrNull()?.text
        }

        fun getYear(response: BrowseResponse): Int? {
            val title = getHeader(response)?.subtitle ?: response.header?.musicDetailHeaderRenderer?.subtitle
            return title?.runs?.lastOrNull()?.text?.toIntOrNull()
        }

        fun getThumbnail(response: BrowseResponse): String? {
            return response.background?.musicThumbnailRenderer?.getThumbnailUrl() ?: response.header?.musicDetailHeaderRenderer?.thumbnail
                ?.croppedSquareThumbnailRenderer?.getThumbnailUrl()
        }

        fun getArtists(response: BrowseResponse): List<Artist> {
            val artists = getHeader(response)?.straplineTextOne?.runs?.oddElements()?.map {
                Artist(
                    name = it.text,
                    id = it.navigationEndpoint?.browseEndpoint?.browseId
                )
            } ?: response.header?.musicDetailHeaderRenderer?.subtitle?.runs?.splitBySeparator()?.getOrNull(1)?.oddElements()?.map {
                Artist(
                    name = it.text,
                    id = it.navigationEndpoint?.browseEndpoint?.browseId
                )
            } ?: emptyList()

            return artists
        }

        private fun getHeader(response: BrowseResponse): MusicResponsiveHeaderRenderer? {
            val tabs = response.contents?.singleColumnBrowseResultsRenderer?.tabs
                ?: response.contents?.twoColumnBrowseResultsRenderer?.tabs
            val section =
                tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()
            val header = section?.musicResponsiveHeaderRenderer
            return header
        }

        fun getSongs(response: BrowseResponse, album: AlbumItem): List<SongItem> {
            val tabs = response.contents?.singleColumnBrowseResultsRenderer?.tabs ?: response.contents?.twoColumnBrowseResultsRenderer?.tabs
            val shelfRenderer = tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()?.musicShelfRenderer ?:
                response.contents?.twoColumnBrowseResultsRenderer?.secondaryContents?.sectionListRenderer?.contents?.firstOrNull()?.musicShelfRenderer

            val songs = shelfRenderer?.contents?.getItems()?.mapNotNull {
                getSong(it, album)
            }
            return songs ?: emptyList()
        }

        fun getSong(renderer: MusicResponsiveListItemRenderer, album: AlbumItem? = null): SongItem? {
            val videoId = renderer.playlistItemData?.videoId
                ?: renderer.overlay?.musicItemThumbnailOverlayRenderer?.content?.musicPlayButtonRenderer
                    ?.playNavigationEndpoint?.watchEndpoint?.videoId
                ?: return null
            val title = PageHelper.extractRuns(renderer.flexColumns, "MUSIC_VIDEO").firstOrNull()?.text
                ?: renderer.flexColumns.firstOrNull()?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.text
                ?: return null
            val artists = PageHelper.extractRuns(renderer.flexColumns, "MUSIC_PAGE_TYPE_ARTIST").map {
                Artist(
                    name = it.text,
                    id = it.navigationEndpoint?.browseEndpoint?.browseId,
                )
            }.ifEmpty {
                album?.artists.orEmpty()
            }
            val albumObj = album?.let {
                Album(it.title, it.browseId)
            } ?: renderer.flexColumns.getOrNull(2)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.let {
                val albumId = it.navigationEndpoint?.browseEndpoint?.browseId
                if (albumId != null) Album(name = it.text, id = albumId) else null
            }
            val duration = renderer.fixedColumns?.firstOrNull()
                ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()
                ?.text?.parseTime()
            val thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl() ?: album?.thumbnail.orEmpty()

            return SongItem(
                id = videoId,
                title = title,
                artists = artists,
                album = albumObj,
                duration = duration,
                thumbnail = thumbnail,
                explicit = renderer.badges?.any {
                    it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                } == true,
            )
        }
    }
}
