/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ruben.spotitube.db.entities

import androidx.compose.runtime.Immutable
import java.time.LocalDateTime

@Immutable
data class Event(
    val id: Long = 0,
    val songId: String,
    val timestamp: LocalDateTime,
    val playTime: Long,
)
