/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



@file:OptIn(ExperimentalSerializationApi::class)

package com.ruben.spotitube.innertube.models

import com.ruben.spotitube.innertube.models.BrowseEndpoint.BrowseEndpointContextSupportedConfigs.BrowseEndpointContextMusicConfig.Companion.MUSIC_PAGE_TYPE_ALBUM
import com.ruben.spotitube.innertube.models.BrowseEndpoint.BrowseEndpointContextSupportedConfigs.BrowseEndpointContextMusicConfig.Companion.MUSIC_PAGE_TYPE_ARTIST
import com.ruben.spotitube.innertube.models.BrowseEndpoint.BrowseEndpointContextSupportedConfigs.BrowseEndpointContextMusicConfig.Companion.MUSIC_PAGE_TYPE_AUDIOBOOK
import com.ruben.spotitube.innertube.models.BrowseEndpoint.BrowseEndpointContextSupportedConfigs.BrowseEndpointContextMusicConfig.Companion.MUSIC_PAGE_TYPE_LIBRARY_ARTIST
import com.ruben.spotitube.innertube.models.BrowseEndpoint.BrowseEndpointContextSupportedConfigs.BrowseEndpointContextMusicConfig.Companion.MUSIC_PAGE_TYPE_PLAYLIST
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/**
 * Typical list item
 * Used in [MusicCarouselShelfRenderer], [MusicShelfRenderer]
 * Appears in quick picks, search results, table items, etc.
 */
@Serializable
data class MusicResponsiveListItemRenderer(
    val badges: List<Badges>?,
    val fixedColumns: List<FlexColumn>?,
    val flexColumns: List<FlexColumn>,
    val thumbnail: ThumbnailRenderer?,
    val menu: Menu?,
    val playlistItemData: PlaylistItemData?,
    val overlay: Overlay?,
    val navigationEndpoint: NavigationEndpoint?,
    val customIndexColumn: CustomIndexColumn? = null,
) {
    val isSong: Boolean
        get() = navigationEndpoint == null || navigationEndpoint.watchEndpoint != null || navigationEndpoint.watchPlaylistEndpoint != null
    val isPlaylist: Boolean
        get() = navigationEndpoint?.browseEndpoint?.browseEndpointContextSupportedConfigs?.browseEndpointContextMusicConfig?.pageType == MUSIC_PAGE_TYPE_PLAYLIST
    val isAlbum: Boolean
        get() = navigationEndpoint?.browseEndpoint?.browseEndpointContextSupportedConfigs?.browseEndpointContextMusicConfig?.pageType == MUSIC_PAGE_TYPE_ALBUM ||
                navigationEndpoint?.browseEndpoint?.browseEndpointContextSupportedConfigs?.browseEndpointContextMusicConfig?.pageType == MUSIC_PAGE_TYPE_AUDIOBOOK
    val isArtist: Boolean
        get() = navigationEndpoint?.browseEndpoint?.browseEndpointContextSupportedConfigs?.browseEndpointContextMusicConfig?.pageType == MUSIC_PAGE_TYPE_ARTIST
                || navigationEndpoint?.browseEndpoint?.browseEndpointContextSupportedConfigs?.browseEndpointContextMusicConfig?.pageType == MUSIC_PAGE_TYPE_LIBRARY_ARTIST

    @Serializable
    data class FlexColumn(
        @JsonNames("musicResponsiveListItemFixedColumnRenderer")
        val musicResponsiveListItemFlexColumnRenderer: MusicResponsiveListItemFlexColumnRenderer,
    ) {
        @Serializable
        data class MusicResponsiveListItemFlexColumnRenderer(
            val text: Runs?,
        )
    }

    @Serializable
    data class PlaylistItemData(
        val playlistSetVideoId: String?,
        val videoId: String,
    )

    @Serializable
    data class CustomIndexColumn(
        val musicCustomIndexColumnRenderer: MusicCustomIndexColumnRenderer? = null,
    ) {
        @Serializable
        data class MusicCustomIndexColumnRenderer(
            val text: Runs? = null,
            val icon: Icon? = null,
        )
    }

    @Serializable
    data class Overlay(
        val musicItemThumbnailOverlayRenderer: MusicItemThumbnailOverlayRenderer,
    ) {
        @Serializable
        data class MusicItemThumbnailOverlayRenderer(
            val content: Content,
        ) {
            @Serializable
            data class Content(
                val musicPlayButtonRenderer: MusicPlayButtonRenderer,
            ) {
                @Serializable
                data class MusicPlayButtonRenderer(
                    val playNavigationEndpoint: NavigationEndpoint?,
                )
            }
        }
    }
}
