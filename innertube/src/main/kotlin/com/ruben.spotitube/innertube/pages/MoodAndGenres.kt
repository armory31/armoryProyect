/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ruben.spotitube.innertube.pages

import com.ruben.spotitube.innertube.models.BrowseEndpoint
import com.ruben.spotitube.innertube.models.GridRenderer
import com.ruben.spotitube.innertube.models.MusicNavigationButtonRenderer
import com.ruben.spotitube.innertube.models.SectionListRenderer

data class MoodAndGenres(
    val title: String,
    val items: List<Item>,
) {
    data class Item(
        val title: String,
        val stripeColor: Long,
        val endpoint: BrowseEndpoint,
    )

    companion object {
        fun fromSectionListRendererContent(content: SectionListRenderer.Content): MoodAndGenres? {
            val grid = content.gridRenderer ?: return null
            return MoodAndGenres(
                title =
                    grid.header
                        ?.gridHeaderRenderer
                        ?.title
                        ?.runs
                        ?.firstOrNull()
                        ?.text ?: "",
                items =
                    grid.items
                        .mapNotNull(GridRenderer.Item::musicNavigationButtonRenderer)
                        .mapNotNull(::fromMusicNavigationButtonRenderer),
            )
        }

        fun fromMusicNavigationButtonRenderer(renderer: MusicNavigationButtonRenderer): Item? {
            return Item(
                title =
                    renderer.buttonText.runs
                        ?.firstOrNull()
                        ?.text ?: return null,
                stripeColor = renderer.solid?.leftStripeColor ?: return null,
                endpoint = renderer.clickCommand.browseEndpoint ?: return null,
            )
        }
    }
}
