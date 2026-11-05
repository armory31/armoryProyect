/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ruben.spotitube.innertube.models.body

import com.ruben.spotitube.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class BrowseBody(
    val context: Context,
    val browseId: String?,
    val params: String? = null,
    val continuation: String? = null,
    val formData: FormData? = null,
) {
    @Serializable
    data class FormData(
        val selectedValues: List<String>
    )
}
