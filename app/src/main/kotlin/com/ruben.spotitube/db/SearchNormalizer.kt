package com.ruben.spotitube.db

import java.text.Normalizer
import java.util.Locale

private val DIACRITICS_REGEX = Regex("\\p{Mn}")

fun normalizeForSearch(text: String?): String {
    if (text == null) return ""
    val lowercase = text.lowercase(Locale.ROOT)
    val normalized = Normalizer.normalize(lowercase, Normalizer.Form.NFD)
    return DIACRITICS_REGEX.replace(normalized, "").trim()
}
