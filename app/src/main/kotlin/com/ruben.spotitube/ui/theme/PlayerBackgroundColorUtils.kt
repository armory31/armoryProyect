/*
 * OpenTune Project - Branding System (2026)
 * Licensed Under GPL-3.0
 */

package com.ruben.spotitube.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import com.ruben.spotitube.OpenTuneColors
import android.graphics.Color as AndroidColor

object PlayerBackgroundColorUtils {

    private const val DEFAULT_MIN_BRIGHTNESS = 0.08f // Reducido para encajar con el Fondo Oscuro Premium[cite: 6]
    private const val DEFAULT_MAX_BRIGHTNESS = 0.45f
    private const val DEFAULT_MIN_SATURATION = 0.40f

    fun ensureComfortableColor(
        color: Color,
        minBrightness: Float = DEFAULT_MIN_BRIGHTNESS,
        maxBrightness: Float = DEFAULT_MAX_BRIGHTNESS,
        minSaturation: Float = DEFAULT_MIN_SATURATION
    ): Color {
        val hsv = color.toHsv()
        hsv[1] = hsv[1].coerceAtLeast(minSaturation)
        hsv[2] = hsv[2].coerceIn(minBrightness, maxBrightness)
        return hsv.toColor()
    }

    fun darkenColor(color: Color, factor: Float): Color {
        val hsv = color.toHsv()
        hsv[2] = (hsv[2] * factor).coerceAtLeast(0f)
        return hsv.toColor()
    }

    fun buildColoringStops(baseColor: Color): Array<Pair<Float, Color>> {
        // Combinación de degradado recomendada: Dorado Principal hacia tonos oscuros[cite: 6]
        val comfortable = ensureComfortableColor(baseColor, minBrightness = 0.12f, maxBrightness = 0.35f)
        val mid = lerp(comfortable, OpenTuneColors.Copper2, 0.3f) // Transición Dorado-Cobre suave[cite: 6]
        return arrayOf(
            0f to comfortable.copy(alpha = 0.95f),
            0.4f to mid.copy(alpha = 0.92f),
            0.8f to OpenTuneColors.Silver4.copy(alpha = 0.96f),
            1f to OpenTuneColors.Silver4
        )
    }

    fun buildBlurOverlayStops(colors: List<Color>): Array<Pair<Float, Color>> {
        if (colors.isEmpty()) {
            return defaultBlurOverlayStops()
        }
        val comfortable = colors.map(::ensureComfortableColor)
        val first = comfortable[0]
        val second = comfortable.getOrNull(1) ?: OpenTuneColors.Copper2
        val third = comfortable.getOrNull(2) ?: OpenTuneColors.Silver4
        return arrayOf(
            0f to first.copy(alpha = 0.40f),
            0.5f to lerp(first, second, 0.5f).copy(alpha = 0.35f),
            1f to third.copy(alpha = 0.55f)
        )
    }

    fun buildBlurGradientStops(colors: List<Color>): Array<Pair<Float, Color>> {
        if (colors.isEmpty()) {
            return arrayOf(
                0f to Color.Transparent,
                1f to Color.Transparent
            )
        }
        val comfortable = colors.map(::ensureComfortableColor)
        val first = comfortable[0]
        val second = comfortable.getOrNull(1) ?: OpenTuneColors.Copper2
        val third = comfortable.getOrNull(2) ?: OpenTuneColors.Silver4
        return arrayOf(
            0f to first.copy(alpha = 0.50f),
            0.5f to second.copy(alpha = 0.40f), // <- Asegúrate de que tenga la 'f' aquí
            1f to third.copy(alpha = 0.35f)
        )
    }
    private fun defaultBlurOverlayStops(): Array<Pair<Float, Color>> = arrayOf(
        0f to OpenTuneColors.Silver4.copy(alpha = 0.50f),
        1f to Color.Black.copy(alpha = 0.80f)
    )

    private fun Color.toHsv(): FloatArray {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(this.toArgb(), hsv)
        return hsv
    }

    private fun FloatArray.toColor(): Color = Color(AndroidColor.HSVToColor(this))
}