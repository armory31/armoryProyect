/*
 * OpenTune Project - Branding System (2026)
 * Licensed Under GPL-3.0
 */

package com.arturo254.opentune.ui.theme

import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.arturo254.opentune.OpenTuneColors

object PlayerSliderColors {

    @Composable
    fun getSliderColors(
        activeColor: Color,
        inactiveAlpha: Float = 0.15f
    ): SliderColors {
        return SliderDefaults.colors(
            activeTrackColor = activeColor,
            activeTickColor = activeColor,
            thumbColor = activeColor,
            inactiveTrackColor = Color.White.copy(alpha = inactiveAlpha)
        )
    }

    @Composable
    fun standardSliderColors(buttonColor: Color): SliderColors {
        // La barra de progreso en ARMORY siempre usa Cobre Acción[cite: 6]
        return getSliderColors(
            activeColor = OpenTuneColors.Copper2,
            inactiveAlpha = Config.INACTIVE_TRACK_ALPHA
        )
    }

    @Composable
    fun wavySliderColors(buttonColor: Color): SliderColors {
        return SliderDefaults.colors(
            activeTrackColor = OpenTuneColors.Copper2,
            activeTickColor = OpenTuneColors.Copper2,
            thumbColor = Color.Transparent,
            inactiveTrackColor = Color.White.copy(alpha = Config.INACTIVE_TRACK_ALPHA),
            inactiveTickColor = Color.White.copy(alpha = Config.INACTIVE_TICK_ALPHA)
        )
    }

    @Composable
    fun thickSliderColors(buttonColor: Color): SliderColors {
        return getSliderColors(
            activeColor = OpenTuneColors.Copper2,
            inactiveAlpha = Config.THICK_INACTIVE_TRACK_ALPHA
        )
    }

    @Composable
    fun circularSliderColors(buttonColor: Color): SliderColors {
        return SliderDefaults.colors(
            activeTrackColor = OpenTuneColors.Copper2,
            activeTickColor = OpenTuneColors.Copper2,
            thumbColor = OpenTuneColors.Copper2,
            inactiveTrackColor = Color.White.copy(alpha = Config.INACTIVE_TRACK_ALPHA)
        )
    }

    @Composable
    fun simpleSliderColors(buttonColor: Color): SliderColors {
        return SliderDefaults.colors(
            activeTrackColor = OpenTuneColors.Copper2.copy(alpha = Config.SIMPLE_ACTIVE_TRACK_ALPHA),
            activeTickColor = OpenTuneColors.Copper2.copy(alpha = Config.SIMPLE_ACTIVE_TRACK_ALPHA),
            thumbColor = Color.Transparent,
            inactiveTrackColor = Color.White.copy(alpha = Config.SIMPLE_INACTIVE_TRACK_ALPHA),
            inactiveTickColor = Color.White.copy(alpha = Config.SIMPLE_INACTIVE_TRACK_ALPHA)
        )
    }

    object Config {
        const val INACTIVE_TRACK_ALPHA = 0.20f
        const val THICK_INACTIVE_TRACK_ALPHA = 0.25f
        const val SIMPLE_ACTIVE_TRACK_ALPHA = 0.8f
        const val SIMPLE_INACTIVE_TRACK_ALPHA = 0.15f
        const val INACTIVE_TICK_ALPHA = 0.2f
        val DEFAULT_ACTIVE_COLOR = OpenTuneColors.Copper2
        val DEFAULT_INACTIVE_COLOR = Color.White.copy(alpha = INACTIVE_TRACK_ALPHA)
    }
}