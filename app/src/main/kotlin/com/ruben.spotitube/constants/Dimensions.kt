/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ruben.spotitube.constants

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

const val CONTENT_TYPE_HEADER = 0
const val CONTENT_TYPE_LIST = 1
const val CONTENT_TYPE_SONG = 2
const val CONTENT_TYPE_ARTIST = 3
const val CONTENT_TYPE_ALBUM = 4

val QueuePeekHeight = 64.dp
val AppBarHeight = 64.dp

val ListItemHeight = 64.dp
val SearchFilterHeight = 48.dp
val ListThumbnailSize = 48.dp
val GridThumbnailHeight = 128.dp

val ThumbnailCornerRadius = 10.dp
val GridThumbnailCornerRadius = 8.dp

val PlayerHorizontalPadding = 32.dp

val BottomSheetAnimationSpec = spring<Dp>(
	dampingRatio = Spring.DampingRatioNoBouncy,
	stiffness = Spring.StiffnessMediumLow
)

val BottomSheetSoftAnimationSpec = spring<Dp>(
	dampingRatio = Spring.DampingRatioNoBouncy,
	stiffness = Spring.StiffnessLow
)
