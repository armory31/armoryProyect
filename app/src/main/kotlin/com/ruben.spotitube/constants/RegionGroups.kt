/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ruben.spotitube.constants

import android.content.Context
import com.ruben.spotitube.R
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Locale
import java.util.UUID

@Serializable
data class RegionGroup(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val codes: List<String>,
)

val ALL_SELECTABLE_COUNTRY_CODES: List<String> = listOf(
    "ES", "GB", "DE", "FR", "IT", "NL", "PL", "MX", "AR", "CO", "CL", "PE", "BR",
    "US", "CA", "PT", "SE", "NO", "DK", "FI", "IE", "BE", "AT", "CH", "UY", "VE",
    "EC", "BO", "PY", "CR", "PA", "DO", "GT", "JP", "KR", "AU"
).distinct()

private val jsonFormat = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun getDefaultRegionGroups(context: Context): List<RegionGroup> = listOf(
    RegionGroup(
        id = "spain",
        name = context.getString(R.string.region_spain),
        codes = listOf("ES")
    ),
    RegionGroup(
        id = "europe",
        name = context.getString(R.string.region_europe),
        codes = listOf("GB", "DE", "FR", "IT", "NL", "PL")
    ),
    RegionGroup(
        id = "latam",
        name = context.getString(R.string.region_latin_america),
        codes = listOf("MX", "AR", "CO", "CL", "PE", "BR")
    )
)

fun parseRegionGroups(raw: String?, context: Context): List<RegionGroup> {
    if (raw.isNullOrBlank()) return getDefaultRegionGroups(context)
    return try {
        val list = jsonFormat.decodeFromString<List<RegionGroup>>(raw)
        if (list.isEmpty()) getDefaultRegionGroups(context) else list
    } catch (_: Exception) {
        getDefaultRegionGroups(context)
    }
}

fun serializeRegionGroups(groups: List<RegionGroup>): String {
    return jsonFormat.encodeToString(groups)
}

fun getGroupsSummaryText(context: Context, groups: List<RegionGroup>): String {
    if (groups.isEmpty()) return context.getString(R.string.select_countries)
    return groups.joinToString(", ") { it.name }
}

fun getCountryDisplayName(code: String): String {
    val locale = runCatching {
        Locale.Builder().setRegion(code).build()
    }.getOrDefault(Locale.getDefault())
    val name = locale.getDisplayCountry(Locale.getDefault())
    return if (name.isBlank() || name.equals(code, ignoreCase = true)) code else name
}
