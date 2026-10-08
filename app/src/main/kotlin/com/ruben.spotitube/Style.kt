/*
 * ARMORY Project - Centralized App Style & Branding System (2026)
 * Single Source of Truth for Fonts, Typography, Colors, Brushes, Themes, and Visual Components
 */

package com.ruben.spotitube

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette

// ── 1. TIPOGRAFÍA Y JERARQUÍA ────────────────────────────────────────────────
val GlobalFont = FontFamily(Font(R.font.baskerville))

val PremiumTitleFont = GlobalFont
val PremiumBodyFont = GlobalFont

object ArmoryFont {
    val Title = GlobalFont
    val Body = GlobalFont
}

private fun buildTypography() = Typography(
    displayLarge = TextStyle(fontFamily = GlobalFont, fontWeight = FontWeight.Black, fontSize = 32.sp, lineHeight = 40.sp),
    headlineLarge = TextStyle(fontFamily = GlobalFont, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp),
    headlineMedium = TextStyle(fontFamily = GlobalFont, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = GlobalFont, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = GlobalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = GlobalFont, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontFamily = GlobalFont, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = GlobalFont, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp)
)

val AppTypography = buildTypography()

// ── 2. COLORES Y SUPERFICIES (ArmoryColors) ──────────────────────────────────
object ArmoryColors {
    // Fondos y Superficies Neutras
    val AbsoluteBlack   = Color(0xFF000000)
    val Background      = Color(0xFF0A0A0A) // Fondo general profundo
    val SurfaceBlack    = Color(0xFF141414) // Tarjetas e ítems de lista
    val SurfaceElevated = Color(0xFF1F1F1F) // Diálogos, mini player y menús
    val SurfaceBorder   = Color(0xFF2E2A20) // Borde sutil dorado/oscuro
    val SurfaceBorderMuted = Color(0xFF222222) // Borde neutro para tarjetas secundarias

    // Dorado (Identidad Principal & Accent)
    val Gold1 = Color(0xFFE6C687) // Champagne / Brillo
    val Gold2 = Color(0xFFD4AF37) // Dorado Metálico Base
    val Gold3 = Color(0xFFB8860B) // Dorado Oscuro
    val Gold4 = Color(0xFF8B6914) // Bronce Dorado
    val Gold5 = Color(0xFF3D2B00) // Fondo / Contenedor Dorado Oscuro

    // Cobre y Plata (Metales Secundarios)
    val CopperAccent = Color(0xFFCD7F32)
    val SilverAccent = Color(0xFFB0B0B0)

    // Texto con Jerarquía por Opacidad
    val TextPrimary   = Color(0xFFFFFFFF)                        // 100% Opacidad - Títulos principales
    val TextSecondary = Color(0xFFFFFFFF).copy(alpha = 0.70f)  // 70% Opacidad - Artistas / Subtítulos
    val TextMuted     = Color(0xFFFFFFFF).copy(alpha = 0.45f)   // 45% Opacidad - Tiempos, metadatos, inactivos
    val TextGold      = Gold1                                   // Encabezados de sección (ej. "Selecciones rápidas")

    // Capa de Legibilidad (Scrim)
    val PlayerScrim   = Color(0xCC000000) // Sombra inferior de reproducción

    // Pinceles (Brushes) Requeridos
    val Gold = Brush.linearGradient(
        colors = listOf(
            Color(0xFF8C6126),
            Color(0xFFC89B48),
            Color(0xFFECC675),
            Color(0xFFFAF0BD),
            Color(0xFFC89B48)
        )
    )

    val Copper = Brush.linearGradient(
        colors = listOf(
            Color(0xFFA36B56),
            Color(0xFFD49A83),
            Color(0xFFF8D3C4),
            Color(0xFFD49A83)
        )
    )

    val Silver = Brush.linearGradient(
        colors = listOf(
            Color(0xFF828588),
            Color(0xFFB8BBBE),
            Color(0xFFE6E8EA),
            Color(0xFFB8BBBE)
        )
    )

    val JetBlack = Brush.linearGradient(
        colors = listOf(
            Color(0xFF0A0A0A),
            Color(0xFF141414),
            Color(0xFF2A2A2A),
            Color(0xFF141414)
        )
    )

    // Alias de Compatibilidad
    val GoldPrimary = Gold1
    val Champagne = Gold1
    val ObsidianSoft = SurfaceBlack
    val Divider = SurfaceBorder

    val Silver1 = Color(0xFFC0C0C0)
    val Silver2 = Color(0xFFB0B0B0)
    val Silver3 = Color(0xFF606060)
    val Silver4 = Color(0xFF2A2A2A)
    val Silver5 = Color(0xFF121212)

    val Copper1 = Color(0xFFE8956D)
    val Copper2 = Color(0xFFCD7F32)
    val Copper3 = Color(0xFFA0522D)
    val Copper4 = Color(0xFF3D1A00)
    val Copper5 = Color(0xFF1A0D00)

    val Rust1 = Color(0xFFE57373)
    val Rust2 = Color(0xFFC0392B)
    val Rust3 = Color(0xFF8B0000)
    val Rust4 = Color(0xFF2C0000)
    val Rust5 = Color(0xFF1A0000)

    val Diamond1 = Color(0xFFADD8E6)
    val Diamond2 = Color(0xFF4FC3F7)
    val Diamond3 = Color(0xFF0288D1)
    val Diamond4 = Color(0xFF01002A)
    val Diamond5 = Color(0xFF00051A)
}

val OpenTuneColors = ArmoryColors

// ── 3. GRADIENTES METÁLICOS (ArmoryGradients) ───────────────────────────────
object ArmoryGradients {
    val MetallicGold = Brush.linearGradient(
        colors = listOf(
            ArmoryColors.Gold1,
            ArmoryColors.Gold2,
            ArmoryColors.Gold3,
            ArmoryColors.Gold1
        )
    )

    val GoldGradient = MetallicGold

    val MetallicCopper = Brush.linearGradient(
        colors = listOf(
            Color(0xFFD49A83),
            Color(0xFFA36B56),
            Color(0xFF3D1A00)
        )
    )

    val MetallicSilver = Brush.linearGradient(
        colors = listOf(
            Color(0xFFE6E8EA),
            Color(0xFFB8BBBE),
            Color(0xFF606060)
        )
    )

    val GoldCopperGradient = Brush.linearGradient(
        colors = listOf(ArmoryColors.Gold3, ArmoryColors.CopperAccent)
    )

    val GoldSilverGradient = Brush.linearGradient(
        colors = listOf(ArmoryColors.Gold3, ArmoryColors.SilverAccent)
    )

    val GoldRustGradient = Brush.linearGradient(
        colors = listOf(ArmoryColors.Gold3, ArmoryColors.Rust2)
    )

    val GoldDiamondGradient = Brush.linearGradient(
        colors = listOf(ArmoryColors.Gold3, ArmoryColors.Diamond2)
    )

    val GlassGoldBackground = Brush.verticalGradient(
        colors = listOf(
            Color(0xCC0A0A0A),
            Color(0xE6000000)
        )
    )

    val ObsidianGradient = Brush.verticalGradient(
        colors = listOf(
            ArmoryColors.SurfaceBlack,
            ArmoryColors.AbsoluteBlack,
            Color(0xFF050505)
        )
    )

    val CardOverlay = Brush.verticalGradient(
        colors = listOf(
            Color.Transparent,
            Color(0xB3000000)
        )
    )
}

// Alias Top-Level de Gradientes
val GoldGradient = ArmoryGradients.GoldGradient
val GoldCopperGradient = ArmoryGradients.GoldCopperGradient
val GoldSilverGradient = ArmoryGradients.GoldSilverGradient
val GoldRustGradient = ArmoryGradients.GoldRustGradient
val GoldDiamondGradient = ArmoryGradients.GoldDiamondGradient
val GlassGoldBackground = ArmoryGradients.GlassGoldBackground
val ObsidianGradient = ArmoryGradients.ObsidianGradient

// ── 4. ESTILOS DE CRISTAL / VIDRIO (LyricsGlassStyle) ────────────────────────
data class LyricsGlassStyle(
    val name: String,
    val surfaceTint: Color,
    val surfaceAlpha: Float,
    val cloudyRadius: Int,
    val refraction: Float,
    val curve: Float,
    val dispersion: Float,
    val glassSaturation: Float,
    val glassContrast: Float,
    val glassEdge: Float,
    val glassCornerRadius: Float,
    val glassTint: Color,
    val textColor: Color,
    val secondaryTextColor: Color,
    val overlayColor: Color,
    val overlayAlpha: Float,
    val isDark: Boolean,
    val backgroundDimAlpha: Float = 0.3f,
) {
    companion object {
        val FrostedDark = LyricsGlassStyle(
            name = "Frosted Dark",
            surfaceTint = Color.Black,
            surfaceAlpha = 0.35f,
            cloudyRadius = 15,
            refraction = 0.20f,
            curve = 0.20f,
            dispersion = 0.0f,
            glassSaturation = 1.10f,
            glassContrast = 1.0f,
            glassEdge = 0.2f,
            glassCornerRadius = 40f,
            glassTint = Color.Black.copy(alpha = 0.35f),
            textColor = Color.White,
            secondaryTextColor = Color.White.copy(alpha = 0.7f),
            overlayColor = Color.Black,
            overlayAlpha = 0.25f,
            isDark = true,
            backgroundDimAlpha = 0.35f,
        )

        val FrostedLight = LyricsGlassStyle(
            name = "Frosted Light",
            surfaceTint = Color.White,
            surfaceAlpha = 0.45f,
            cloudyRadius = 15,
            refraction = 0.18f,
            curve = 0.18f,
            dispersion = 0.0f,
            glassSaturation = 1.05f,
            glassContrast = 1.0f,
            glassEdge = 0.25f,
            glassCornerRadius = 40f,
            glassTint = Color.White.copy(alpha = 0.15f),
            textColor = Color(0xFF1A1A1A),
            secondaryTextColor = Color(0xFF1A1A1A).copy(alpha = 0.65f),
            overlayColor = Color.White,
            overlayAlpha = 0.35f,
            isDark = false,
            backgroundDimAlpha = 0.15f,
        )

        val ClearGlass = LyricsGlassStyle(
            name = "Clear Glass",
            surfaceTint = Color.White,
            surfaceAlpha = 0.15f,
            cloudyRadius = 12,
            refraction = 0.25f,
            curve = 0.25f,
            dispersion = 0.0f,
            glassSaturation = 1.15f,
            glassContrast = 1.05f,
            glassEdge = 0.2f,
            glassCornerRadius = 40f,
            glassTint = Color.White.copy(alpha = 0.05f),
            textColor = Color.White,
            secondaryTextColor = Color.White.copy(alpha = 0.7f),
            overlayColor = Color.Black,
            overlayAlpha = 0.2f,
            isDark = true,
            backgroundDimAlpha = 0.25f,
        )

        val UltraClear = LyricsGlassStyle(
            name = "Ultra Clear",
            surfaceTint = Color.White,
            surfaceAlpha = 0.08f,
            cloudyRadius = 8,
            refraction = 0.30f,
            curve = 0.30f,
            dispersion = 0.0f,
            glassSaturation = 1.20f,
            glassContrast = 1.10f,
            glassEdge = 0.15f,
            glassCornerRadius = 40f,
            glassTint = Color.White.copy(alpha = 0.03f),
            textColor = Color.White,
            secondaryTextColor = Color.White.copy(alpha = 0.75f),
            overlayColor = Color.Black,
            overlayAlpha = 0.15f,
            isDark = true,
            backgroundDimAlpha = 0.20f,
        )

        val SmokyGlass = LyricsGlassStyle(
            name = "Smoky Glass",
            surfaceTint = Color.Black,
            surfaceAlpha = 0.55f,
            cloudyRadius = 20,
            refraction = 0.15f,
            curve = 0.15f,
            dispersion = 0.0f,
            glassSaturation = 1.0f,
            glassContrast = 0.95f,
            glassEdge = 0.15f,
            glassCornerRadius = 40f,
            glassTint = Color.Black.copy(alpha = 0.5f),
            textColor = Color.White,
            secondaryTextColor = Color.White.copy(alpha = 0.65f),
            overlayColor = Color.Black,
            overlayAlpha = 0.35f,
            isDark = true,
            backgroundDimAlpha = 0.45f,
        )

        val GoldenGlass = LyricsGlassStyle(
            name = "Golden Glass",
            surfaceTint = Color(0xFFFFD700),
            surfaceAlpha = 0.25f,
            cloudyRadius = 15,
            refraction = 0.22f,
            curve = 0.22f,
            dispersion = 0.0f,
            glassSaturation = 1.25f,
            glassContrast = 1.05f,
            glassEdge = 0.3f,
            glassCornerRadius = 40f,
            glassTint = Color(0xFFFFD700).copy(alpha = 0.15f),
            textColor = Color.White,
            secondaryTextColor = Color(0xFFFFF8DC).copy(alpha = 0.8f),
            overlayColor = Color.Black,
            overlayAlpha = 0.25f,
            isDark = true,
            backgroundDimAlpha = 0.3f,
        )

        val MidnightGlass = LyricsGlassStyle(
            name = "Midnight Glass",
            surfaceTint = Color(0xFF191970),
            surfaceAlpha = 0.40f,
            cloudyRadius = 18,
            refraction = 0.18f,
            curve = 0.18f,
            dispersion = 0.0f,
            glassSaturation = 1.15f,
            glassContrast = 1.0f,
            glassEdge = 0.2f,
            glassCornerRadius = 40f,
            glassTint = Color(0xFF191970).copy(alpha = 0.3f),
            textColor = Color.White,
            secondaryTextColor = Color(0xFFE6E6FA).copy(alpha = 0.75f),
            overlayColor = Color.Black,
            overlayAlpha = 0.3f,
            isDark = true,
            backgroundDimAlpha = 0.4f,
        )

        val EmeraldGlass = LyricsGlassStyle(
            name = "Emerald Glass",
            surfaceTint = Color(0xFF004D40),
            surfaceAlpha = 0.35f,
            cloudyRadius = 15,
            refraction = 0.20f,
            curve = 0.20f,
            dispersion = 0.0f,
            glassSaturation = 1.20f,
            glassContrast = 1.0f,
            glassEdge = 0.25f,
            glassCornerRadius = 40f,
            glassTint = Color(0xFF004D40).copy(alpha = 0.25f),
            textColor = Color.White,
            secondaryTextColor = Color(0xFFE0F2F1).copy(alpha = 0.75f),
            overlayColor = Color.Black,
            overlayAlpha = 0.25f,
            isDark = true,
            backgroundDimAlpha = 0.35f,
        )

        val SunsetGlass = LyricsGlassStyle(
            name = "Sunset Glass",
            surfaceTint = Color(0xFFFF4500),
            surfaceAlpha = 0.30f,
            cloudyRadius = 15,
            refraction = 0.20f,
            curve = 0.20f,
            dispersion = 0.0f,
            glassSaturation = 1.30f,
            glassContrast = 1.05f,
            glassEdge = 0.3f,
            glassCornerRadius = 40f,
            glassTint = Color(0xFFFF4500).copy(alpha = 0.2f),
            textColor = Color.White,
            secondaryTextColor = Color(0xFFFFE4E1).copy(alpha = 0.8f),
            overlayColor = Color.Black,
            overlayAlpha = 0.25f,
            isDark = true,
            backgroundDimAlpha = 0.3f,
        )

        val AllStyles = listOf(
            FrostedDark,
            FrostedLight,
            ClearGlass,
            UltraClear,
            SmokyGlass,
            GoldenGlass,
            MidnightGlass,
            EmeraldGlass,
            SunsetGlass,
        )

        val allPresets = AllStyles

        fun fromPalette(palette: Palette?): LyricsGlassStyle {
            if (palette == null) return FrostedDark
            val dominantColor = Color(palette.getDominantColor(0xFF000000.toInt()))
            val textColor = Color(palette.getLightVibrantColor(0xFFFFFFFF.toInt()))
            return FrostedDark.copy(
                name = "Dynamic Palette",
                surfaceTint = dominantColor,
                glassTint = dominantColor.copy(alpha = 0.35f),
                textColor = textColor,
                secondaryTextColor = textColor.copy(alpha = 0.7f)
            )
        }

        val Default = FrostedDark
    }
}

// ── 5. ESTRUCTURAS DE PLAYLISTS Y ICONOS ────────────────────────────────────
enum class PlaylistIconType { VINYL, CROWN, GUITAR, NOTES, DIAMOND, FLAME }

data class PlaylistItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val gradientColors: List<Color>,
    val iconType: PlaylistIconType
)

// ── 6. DESLIZADORES DE REPRODUCTOR (PlayerSliderColors) ─────────────────────
object PlayerSliderColors {
    @Composable
    fun getSliderColors(): SliderColors {
        return SliderDefaults.colors(
            activeTrackColor = ArmoryColors.Gold2,
            activeTickColor = ArmoryColors.Gold2,
            thumbColor = ArmoryColors.Gold1,
            inactiveTrackColor = Color.White.copy(alpha = 0.20f)
        )
    }

    @Composable
    fun standardSliderColors(buttonColor: Color): SliderColors = getSliderColors()

    @Composable
    fun wavySliderColors(buttonColor: Color): SliderColors = getSliderColors()

    @Composable
    fun thickSliderColors(buttonColor: Color): SliderColors = getSliderColors()

    @Composable
    fun circularSliderColors(buttonColor: Color): SliderColors = getSliderColors()

    @Composable
    fun simpleSliderColors(buttonColor: Color): SliderColors = getSliderColors()

    object Config {
        const val INACTIVE_TRACK_ALPHA = 0.20f
        const val THICK_INACTIVE_TRACK_ALPHA = 0.25f
        const val SIMPLE_ACTIVE_TRACK_ALPHA = 0.8f
        const val SIMPLE_INACTIVE_TRACK_ALPHA = 0.15f
        const val INACTIVE_TICK_ALPHA = 0.2f
        val DEFAULT_ACTIVE_COLOR = ArmoryColors.Copper2
        val DEFAULT_INACTIVE_COLOR = Color.White.copy(alpha = INACTIVE_TRACK_ALPHA)
    }
}

// ── 7. FONDO DINÁMICO DE REPRODUCTOR (DynamicBackground) ────────────────────
@Composable
fun DynamicPlayerBackground(
    dominantColor: Color = Color.Black,
    content: @Composable () -> Unit
) {
    val animatedBg by animateColorAsState(
        targetValue = if (dominantColor == Color.Black) ArmoryColors.Background else dominantColor.copy(alpha = 0.35f),
        animationSpec = tween(1500),
        label = "dynamicBg"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ArmoryColors.AbsoluteBlack)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        animatedBg,
                        ArmoryColors.Background,
                        ArmoryColors.AbsoluteBlack
                    )
                )
            )
    ) {
        content()
    }
}

@Composable
fun DynamicBackground(thumbnailUrl: String) {
    DynamicPlayerBackground(dominantColor = Color.Black) {
        // Empty placeholder content for background
    }
}

// ── 8. TEMA MATERIAL 3 (ArmoryTheme) ────────────────────────────────────────
@Composable
fun ArmoryTheme(
    pureBlack: Boolean = true,
    themeColor: Color? = null,
    seedPalette: Any? = null,
    content: @Composable () -> Unit
) {
    val colorScheme = ColorScheme(
        primary = themeColor ?: ArmoryColors.Gold2,
        onPrimary = ArmoryColors.AbsoluteBlack,
        primaryContainer = ArmoryColors.Gold5,
        onPrimaryContainer = ArmoryColors.Gold1,
        inversePrimary = ArmoryColors.Gold1,

        secondary = ArmoryColors.SilverAccent,
        onSecondary = ArmoryColors.AbsoluteBlack,
        secondaryContainer = ArmoryColors.SurfaceElevated,
        onSecondaryContainer = ArmoryColors.TextPrimary,

        tertiary = ArmoryColors.CopperAccent,
        onTertiary = ArmoryColors.AbsoluteBlack,
        tertiaryContainer = Color(0xFF3D1A00),
        onTertiaryContainer = Color(0xFFE8956D),

        background = ArmoryColors.Background,
        onBackground = ArmoryColors.TextPrimary,
        surface = ArmoryColors.SurfaceBlack,
        onSurface = ArmoryColors.TextPrimary,
        surfaceVariant = ArmoryColors.SurfaceElevated,
        onSurfaceVariant = ArmoryColors.TextSecondary,
        inverseSurface = ArmoryColors.TextPrimary,
        inverseOnSurface = ArmoryColors.AbsoluteBlack,

        outline = ArmoryColors.SurfaceBorder,
        outlineVariant = ArmoryColors.SurfaceBorderMuted,
        scrim = ArmoryColors.AbsoluteBlack,
        surfaceTint = ArmoryColors.Gold2,

        error = Color(0xFFC0392B),
        onError = ArmoryColors.AbsoluteBlack,
        errorContainer = Color(0xFF2C0000),
        onErrorContainer = Color(0xFFE57373),

        surfaceBright = ArmoryColors.SurfaceElevated,
        surfaceDim = ArmoryColors.Background,
        surfaceContainer = ArmoryColors.SurfaceBlack,
        surfaceContainerLow = ArmoryColors.Background,
        surfaceContainerLowest = ArmoryColors.AbsoluteBlack,
        surfaceContainerHigh = ArmoryColors.SurfaceElevated,
        surfaceContainerHighest = Color(0xFF2A2A2A)
    )

    val animatedColorScheme = animateColorScheme(colorScheme)

    MaterialExpressiveTheme(
        colorScheme = animatedColorScheme,
        typography = AppTypography,
        content = content
    )
}

@Composable
private fun animateColorScheme(targetColorScheme: ColorScheme): ColorScheme {
    val animationSpec = spring<Color>(stiffness = androidx.compose.animation.core.Spring.StiffnessLow)
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
        surfaceContainerHighest = animateColorAsState(targetColorScheme.surfaceContainerHighest, animationSpec, label = "surfaceContainerHighest").value
    )
}
