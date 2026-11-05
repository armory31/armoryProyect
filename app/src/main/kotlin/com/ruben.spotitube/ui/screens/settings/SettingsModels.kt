/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ruben.spotitube.ui.screens.settings

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter

@Immutable
data class SettingsQuickAction(
    val icon: Painter,
    val label: String,
    val onClick: () -> Unit,
    val accentColor: Color,
)

@Immutable
data class SettingsGroup(
    val title: String,
    val items: List<SettingsItem>,
)

@Immutable
data class SettingsItem(
    val icon: Painter,
    val title: String,
    val subtitle: String? = null,
    val badge: String? = null,
    val showUpdateIndicator: Boolean = false,
    val accentColor: Color = Color.Unspecified,
    val keywords: List<String> = emptyList(),
    val onClick: () -> Unit,
)

@Immutable
data class SettingsIntegrationAction(
    val icon: Painter,
    val label: String,
    val onClick: () -> Unit,
    val accentColor: Color,
)
