/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.arturo254.opentune.constants

import androidx.annotation.StringRes
import com.arturo254.opentune.R

enum class DarkMode(@StringRes val label: Int) {
    ON(R.string.dark_theme_on),
    OFF(R.string.dark_theme_off),
    AUTO(R.string.dark_theme_follow_system)
}

enum class LyricsPosition {
    LEFT,
    CENTER,
    RIGHT
}

enum class NavigationTab {
    HOME,
    LIBRARY,
    SEARCH
}
