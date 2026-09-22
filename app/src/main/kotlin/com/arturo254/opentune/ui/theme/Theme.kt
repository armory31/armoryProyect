/*
 * ARMORY Project - Branding System (2026)
 * Licensed Under GPL-3.0
 */

package com.arturo254.opentune.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.arturo254.opentune.OpenTuneColors


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ArmoryTheme(
//    darkTheme: Boolean = true, // Forzamos oscuro para ARMORY
    pureBlack: Boolean = true, // Forzamos pureBlack para ARMORY
    themeColor: Color? = null,
    seedPalette: ThemeSeedPalette? = null,
    content: @Composable () -> Unit,
) {
    val typography = AppTypography
    val bg = OpenTuneColors.AbsoluteBlack // Inactivado DeepBlack para asegurar pure black total

    val colorScheme = ColorScheme(
        primary = themeColor ?: seedPalette?.primary ?: OpenTuneColors.Gold3,
        onPrimary = OpenTuneColors.AbsoluteBlack,
        primaryContainer = OpenTuneColors.Gold5,
        onPrimaryContainer = OpenTuneColors.Gold1,
        inversePrimary = OpenTuneColors.Gold2,

        secondary = seedPalette?.secondary ?: OpenTuneColors.Silver2,
        onSecondary = OpenTuneColors.AbsoluteBlack,
        secondaryContainer = OpenTuneColors.AbsoluteBlack, // Inactivado SurfaceBlack
        onSecondaryContainer = OpenTuneColors.Silver1,

        tertiary = seedPalette?.tertiary ?: OpenTuneColors.Copper2,
        onTertiary = OpenTuneColors.AbsoluteBlack,
        tertiaryContainer = OpenTuneColors.Copper4,
        onTertiaryContainer = OpenTuneColors.Copper1,

        background = bg,
        onBackground = OpenTuneColors.TextPrimary,
        surface = OpenTuneColors.AbsoluteBlack, // Inactivado SurfaceBlack para obligar pure black
        onSurface = OpenTuneColors.TextPrimary,
        surfaceVariant = OpenTuneColors.AbsoluteBlack, // Inactivado CardBlack para obligar pure black
        onSurfaceVariant = OpenTuneColors.Silver2,
        inverseSurface = OpenTuneColors.Silver1,
        inverseOnSurface = OpenTuneColors.AbsoluteBlack,

        outline = OpenTuneColors.Gold4,
        outlineVariant = OpenTuneColors.Gold5,
        scrim = OpenTuneColors.AbsoluteBlack,
        surfaceTint = OpenTuneColors.Gold3,
        
        error = OpenTuneColors.Rust2,
        onError = OpenTuneColors.AbsoluteBlack,
        errorContainer = OpenTuneColors.Rust4,
        onErrorContainer = OpenTuneColors.Rust1,

        surfaceBright = OpenTuneColors.AbsoluteBlack, // Inactivado gris claro
        surfaceDim = bg,
        surfaceContainer = OpenTuneColors.AbsoluteBlack, // Inactivado SurfaceBlack
        surfaceContainerLow = bg,
        surfaceContainerLowest = OpenTuneColors.AbsoluteBlack,
        surfaceContainerHigh = OpenTuneColors.AbsoluteBlack, // Inactivado gris
        surfaceContainerHighest = OpenTuneColors.AbsoluteBlack, // Inactivado gris
    )


    val animatedColorScheme = animateColorScheme(colorScheme)

    MaterialExpressiveTheme(
        colorScheme = animatedColorScheme,
        typography = typography,
        content = content
    )
}

@Composable
private fun animateColorScheme(targetColorScheme: ColorScheme): ColorScheme {
    val animationSpec = spring<Color>(stiffness = Spring.StiffnessLow)
    return ColorScheme(
        primary = animateColorAsState(targetColorScheme.primary, animationSpec, label = "primary").value,
        onPrimary = animateColorAsState(targetColorScheme.onPrimary, animationSpec, label = "onPrimary").value,
        primaryContainer = animateColorAsState(targetColorScheme.primaryContainer, animationSpec, label = "primaryContainer").value,
        onPrimaryContainer = animateColorAsState(targetColorScheme.onPrimaryContainer, animationSpec, label = "onPrimaryContainer").value,
        inversePrimary = animateColorAsState(targetColorScheme.inversePrimary, animationSpec, label = "inversePrimary").value,
        secondary = animateColorAsState(targetColorScheme.secondary, animationSpec, label = "secondary").value,
        onSecondary = animateColorAsState(targetColorScheme.onSecondary, animationSpec, label = "onSecondary").value,
        secondaryContainer = animateColorAsState(targetColorScheme.secondaryContainer, animationSpec, label = "secondaryContainer").value,
        onSecondaryContainer = animateColorAsState(targetColorScheme.onSecondaryContainer, animationSpec, label = "onSecondaryContainer").value,
        tertiary = animateColorAsState(targetColorScheme.tertiary, animationSpec, label = "tertiary").value,
        onTertiary = animateColorAsState(targetColorScheme.onTertiary, animationSpec, label = "onTertiary").value,
        tertiaryContainer = animateColorAsState(targetColorScheme.tertiaryContainer, animationSpec, label = "tertiaryContainer").value,
        onTertiaryContainer = animateColorAsState(targetColorScheme.onTertiaryContainer, animationSpec, label = "onTertiaryContainer").value,
        background = animateColorAsState(targetColorScheme.background, animationSpec, label = "background").value,
        onBackground = animateColorAsState(targetColorScheme.onBackground, animationSpec, label = "onBackground").value,
        surface = animateColorAsState(targetColorScheme.surface, animationSpec, label = "surface").value,
        onSurface = animateColorAsState(targetColorScheme.onSurface, animationSpec, label = "onSurface").value,
        surfaceVariant = animateColorAsState(targetColorScheme.surfaceVariant, animationSpec, label = "surfaceVariant").value,
        onSurfaceVariant = animateColorAsState(targetColorScheme.onSurfaceVariant, animationSpec, label = "onSurfaceVariant").value,
        surfaceTint = animateColorAsState(targetColorScheme.surfaceTint, animationSpec, label = "surfaceTint").value,
        inverseSurface = animateColorAsState(targetColorScheme.inverseSurface, animationSpec, label = "inverseSurface").value,
        inverseOnSurface = animateColorAsState(targetColorScheme.inverseOnSurface, animationSpec, label = "inverseOnSurface").value,
        error = animateColorAsState(targetColorScheme.error, animationSpec, label = "error").value,
        onError = animateColorAsState(targetColorScheme.onError, animationSpec, label = "onError").value,
        errorContainer = animateColorAsState(targetColorScheme.errorContainer, animationSpec, label = "errorContainer").value,
        onErrorContainer = animateColorAsState(targetColorScheme.onErrorContainer, animationSpec, label = "onErrorContainer").value,
        outline = animateColorAsState(targetColorScheme.outline, animationSpec, label = "outline").value,
        outlineVariant = animateColorAsState(targetColorScheme.outlineVariant, animationSpec, label = "outlineVariant").value,
        scrim = animateColorAsState(targetColorScheme.scrim, animationSpec, label = "scrim").value,
        surfaceBright = animateColorAsState(targetColorScheme.surfaceBright, animationSpec, label = "surfaceBright").value,
        surfaceDim = animateColorAsState(targetColorScheme.surfaceDim, animationSpec, label = "surfaceDim").value,
        surfaceContainer = animateColorAsState(targetColorScheme.surfaceContainer, animationSpec, label = "surfaceContainer").value,
        surfaceContainerLow = animateColorAsState(targetColorScheme.surfaceContainerLow, animationSpec, label = "surfaceContainerLow").value,
        surfaceContainerLowest = animateColorAsState(targetColorScheme.surfaceContainerLowest, animationSpec, label = "surfaceContainerLowest").value,
        surfaceContainerHigh = animateColorAsState(targetColorScheme.surfaceContainerHigh, animationSpec, label = "surfaceContainerHigh").value,
        surfaceContainerHighest = animateColorAsState(targetColorScheme.surfaceContainerHighest, animationSpec, label = "surfaceContainerHighest").value,
    )
}
