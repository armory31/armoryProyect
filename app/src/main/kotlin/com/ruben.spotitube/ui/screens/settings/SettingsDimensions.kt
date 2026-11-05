/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ruben.spotitube.ui.screens.settings

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.dp

object SettingsDimensions {
    val GroupCardCornerRadius = 16.dp
    val QuickActionCardCornerRadius = 20.dp
    val IntegrationPillCornerRadius = 14.dp
    val BannerCardCornerRadius = 20.dp
    val HeroCardCornerRadius = 24.dp
    val RowIconCornerRadius = 12.dp

    val ScreenHorizontalPadding = 16.dp
    val SectionSpacing = 14.dp
    val RowVerticalPadding = 14.dp
    val RowHorizontalPadding = 16.dp

    val RowIconSize = 36.dp
    val RowIconInnerSize = 20.dp
    val QuickActionIconSize = 40.dp
    val QuickActionIconInnerSize = 22.dp
    val HeroIconSize = 56.dp
    val HeroIconInnerSize = 30.dp
    val IntegrationIconSize = 28.dp
    val IntegrationIconInnerSize = 16.dp
    val BannerIconSize = 44.dp
    val BannerIconInnerSize = 22.dp
    val ChevronSize = 18.dp

    val DividerThickness = 0.5.dp
    val DividerStartIndent = 60.dp

    val SectionHeaderBottomPadding = 6.dp
    val SectionHeaderHorizontalPadding = 20.dp

    const val QUICK_ACTION_TILE_ASPECT_RATIO = 1.4f

    const val COMPACT_COLUMNS = 2
    const val MEDIUM_COLUMNS = 4
    const val EXPANDED_COLUMNS = 4

    const val MEDIUM_PANE_LEFT_WEIGHT = 0.42f
    const val MEDIUM_PANE_RIGHT_WEIGHT = 0.58f
    val ExpandedListPaneWidth = 380.dp
}

object SettingsAnimations {
    const val PRESS_SCALE = 0.97f
    const val TILE_PRESS_SCALE = 0.94f
    const val PILL_PRESS_SCALE = 0.95f
    const val ICON_PRESS_ROTATION = 5f
    val PillPressLift = (-2).dp
    const val ENTRANCE_SLIDE_DURATION = 350
    const val STAGGER_DELAY_PER_ITEM = 80

    fun <T> pressSpring() = spring<T>(stiffness = Spring.StiffnessHigh)
    fun <T> entranceSpring() = spring<T>(
        stiffness = Spring.StiffnessLow,
        dampingRatio = 0.85f,
    )
}
