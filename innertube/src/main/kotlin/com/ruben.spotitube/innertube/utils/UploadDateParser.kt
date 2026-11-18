/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ruben.spotitube.innertube.utils

import java.util.Locale

private val monthMap = mapOf(
    "ene" to 1, "jan" to 1,
    "feb" to 2,
    "mar" to 3,
    "abr" to 4, "apr" to 4,
    "may" to 5,
    "jun" to 6,
    "jul" to 7,
    "ago" to 8, "aug" to 8,
    "sep" to 9,
    "oct" to 10,
    "nov" to 11,
    "dic" to 12, "dec" to 12,
)

private val yearRegex = Regex("(19|20)\\d{2}")
private val monthWordRegex = Regex("[A-Za-zÁ-ú]{3,}")

fun parseUploadDate(text: String?): String? {
    if (text.isNullOrBlank()) return null

    val yearMatch = yearRegex.find(text)?.value ?: return null

    var monthNum: Int? = null
    for (match in monthWordRegex.findAll(text)) {
        val word = match.value.lowercase(Locale.ROOT).replace(".", "")
        if (word.length >= 3) {
            val prefix = word.substring(0, 3)
            val found = monthMap[prefix]
            if (found != null) {
                monthNum = found
                break
            }
        }
    }

    if (monthNum == null) return null

    return String.format(Locale.ROOT, "%02d/%s", monthNum, yearMatch)
}
