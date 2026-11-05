/*
 * ARMORY Project - Centralized App Style & Branding System (2026)
 * Single Source of Truth for Fonts, Typography, Colors, Brushes, Themes, and Visual Components
 */

package com.ruben.spotitube

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.ruben.spotitube.ui.component.NavigationTitle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette

// ── 1. TIPOGRAFÍA Y JERARQUÍA ────────────────────────────────────────────────
val GlobalFont = FontFamily(Font(R.font.baskerville))

private val displayStyle  = TextStyle(fontFamily = GlobalFont, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp, brush = ArmoryGradients.HeaderGold)
private val headlineStyle = TextStyle(fontFamily = GlobalFont, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp, color = ArmoryColors.TextGold)
private val sectionStyle  = TextStyle(fontFamily = GlobalFont, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 28.sp, color = ArmoryColors.TextGold)
private val titleStyle    = TextStyle(fontFamily = GlobalFont, fontWeight = FontWeight.Normal, fontSize = 18.sp, lineHeight = 24.sp, color = ArmoryColors.TextPrimary)
private val bodyStyle     = TextStyle(fontFamily = GlobalFont, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, color = ArmoryColors.TextSecondary)
private val labelStyle    = TextStyle(fontFamily = GlobalFont, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp)
private val captionStyle  = TextStyle(fontFamily = GlobalFont, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, color = ArmoryColors.TextMuted)

val AppTypography = Typography(
    displayLarge = displayStyle,
    displayMedium = displayStyle,
    displaySmall = displayStyle,
    headlineLarge = headlineStyle,
    titleLarge = headlineStyle,
    headlineMedium = sectionStyle,
    headlineSmall = sectionStyle,
    titleMedium = titleStyle,
    bodyLarge = bodyStyle,
    bodyMedium = bodyStyle,
    titleSmall = labelStyle,
    labelLarge = labelStyle,
    labelMedium = labelStyle,
    bodySmall = captionStyle,
    labelSmall = captionStyle
)

// ── 2. COLORES Y SUPERFICIES (ArmoryColors) ──────────────────────────────────
object ArmoryColors {
    val AbsoluteBlack   = Color(0xFF000000)
    val Background      = Color(0xFF0A0A0A)
    val SurfaceBlack    = Color(0xFF141414)
    val SurfaceElevated = Color(0xFF1F1F1F)
    val SurfaceBorder   = Color(0xFF2E2A20)
    val SurfaceBorderMuted = Color(0xFF222222)
    val SuccessGreen    = Color(0xFF43B581)

    val Gold1 = Color(0xFFE6C687)
    val Gold2 = Color(0xFFD4AF37)
    val Gold3 = Color(0xFFB8860B)
    val Gold4 = Color(0xFF8B6914)
    val Gold5 = Color(0xFF3D2B00)

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

    val TextPrimary   = Color(0xFFFFFFFF)
    val TextSecondary = Silver2
    val TextMuted     = Color(0xFF8A8A8A)
    val TextGold      = Gold1

    val PlayerScrim   = Color(0xCC000000)
}

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

    val HeaderGold = Brush.linearGradient(
        colors = listOf(
            Color(0xFFC89B48),
            Color(0xFFECC675),
            Color(0xFFFAF0BD),
            Color(0xFFC89B48)
        )
    )

    val MetallicCopper = Brush.linearGradient(colors = listOf(Color(0xFFD49A83), Color(0xFFA36B56), ArmoryColors.Copper3))
    val MetallicSilver = Brush.linearGradient(colors = listOf(Color(0xFFE6E8EA), Color(0xFFB8BBBE), Color(0xFF8E9194)))

    val GoldCopperGradient = Brush.linearGradient(colors = listOf(ArmoryColors.Gold3, ArmoryColors.Copper2))
    val GoldSilverGradient = Brush.linearGradient(colors = listOf(ArmoryColors.Gold3, ArmoryColors.Silver2))
    val GoldRustGradient = Brush.linearGradient(colors = listOf(ArmoryColors.Gold3, ArmoryColors.Rust2))
    val GoldDiamondGradient = Brush.linearGradient(colors = listOf(ArmoryColors.Gold3, ArmoryColors.Diamond2))

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

    val JetBlack = Brush.linearGradient(
        colors = listOf(
            ArmoryColors.Background,
            ArmoryColors.SurfaceBlack,
            ArmoryColors.Silver4,
            ArmoryColors.SurfaceBlack
        )
    )
}

// ── 4. ESTILOS DE CRISTAL / VIDRIO (LyricsGlassStyle) ────────────────────────
data class LyricsGlassStyle(
    val name: String,
    val surfaceTint: Color,
    val surfaceAlpha: Float,
    val cloudyRadius: Int,
    val refraction: Float = 0.20f,
    val curve: Float = 0.20f,
    val dispersion: Float = 0.0f,
    val glassSaturation: Float = 1.10f,
    val glassContrast: Float = 1.0f,
    val glassEdge: Float = 0.2f,
    val glassCornerRadius: Float = 40f,
    val glassTint: Color,
    val textColor: Color = Color.White,
    val secondaryTextColor: Color = Color.White.copy(alpha = 0.7f),
    val isDark: Boolean = true,
    val overlayColor: Color = if (isDark) Color.Black else Color.White,
    val overlayAlpha: Float = 0.25f,
    val backgroundDimAlpha: Float = 0.35f,
) {
    companion object {
        val FrostedDark = LyricsGlassStyle(
            name = "Frosted Dark",
            surfaceTint = Color.Black,
            surfaceAlpha = 0.35f,
            cloudyRadius = 15,
            glassTint = Color.Black.copy(alpha = 0.35f)
        )

        val FrostedLight = LyricsGlassStyle(
            name = "Frosted Light",
            surfaceTint = Color.White,
            surfaceAlpha = 0.45f,
            cloudyRadius = 15,
            refraction = 0.18f,
            curve = 0.18f,
            glassSaturation = 1.05f,
            glassEdge = 0.25f,
            glassTint = Color.White.copy(alpha = 0.15f),
            textColor = Color(0xFF1A1A1A),
            secondaryTextColor = Color(0xFF1A1A1A).copy(alpha = 0.65f),
            isDark = false,
            overlayAlpha = 0.35f,
            backgroundDimAlpha = 0.15f
        )

        val ClearGlass = LyricsGlassStyle(
            name = "Clear Glass",
            surfaceTint = Color.White,
            surfaceAlpha = 0.15f,
            cloudyRadius = 12,
            refraction = 0.25f,
            curve = 0.25f,
            glassSaturation = 1.15f,
            glassContrast = 1.05f,
            glassTint = Color.White.copy(alpha = 0.05f),
            overlayAlpha = 0.2f,
            backgroundDimAlpha = 0.25f
        )

        val UltraClear = LyricsGlassStyle(
            name = "Ultra Clear",
            surfaceTint = Color.White,
            surfaceAlpha = 0.08f,
            cloudyRadius = 8,
            refraction = 0.30f,
            curve = 0.30f,
            glassSaturation = 1.20f,
            glassContrast = 1.10f,
            glassEdge = 0.15f,
            glassTint = Color.White.copy(alpha = 0.03f),
            secondaryTextColor = Color.White.copy(alpha = 0.75f),
            overlayAlpha = 0.15f,
            backgroundDimAlpha = 0.20f
        )

        val SmokyGlass = LyricsGlassStyle(
            name = "Smoky Glass",
            surfaceTint = Color.Black,
            surfaceAlpha = 0.55f,
            cloudyRadius = 20,
            refraction = 0.15f,
            curve = 0.15f,
            glassSaturation = 1.0f,
            glassContrast = 0.95f,
            glassEdge = 0.15f,
            glassTint = Color.Black.copy(alpha = 0.5f),
            secondaryTextColor = Color.White.copy(alpha = 0.65f),
            overlayAlpha = 0.35f,
            backgroundDimAlpha = 0.45f
        )

        val GoldenGlass = LyricsGlassStyle(
            name = "Golden Glass",
            surfaceTint = Color(0xFFFFD700),
            surfaceAlpha = 0.25f,
            cloudyRadius = 15,
            refraction = 0.22f,
            curve = 0.22f,
            glassSaturation = 1.25f,
            glassContrast = 1.05f,
            glassEdge = 0.3f,
            glassTint = Color(0xFFFFD700).copy(alpha = 0.15f),
            secondaryTextColor = Color(0xFFFFF8DC).copy(alpha = 0.8f),
            backgroundDimAlpha = 0.3f
        )

        val MidnightGlass = LyricsGlassStyle(
            name = "Midnight Glass",
            surfaceTint = Color(0xFF191970),
            surfaceAlpha = 0.40f,
            cloudyRadius = 18,
            refraction = 0.18f,
            curve = 0.18f,
            glassSaturation = 1.15f,
            glassTint = Color(0xFF191970).copy(alpha = 0.3f),
            secondaryTextColor = Color(0xFFE6E6FA).copy(alpha = 0.75f),
            overlayAlpha = 0.3f,
            backgroundDimAlpha = 0.4f
        )

        val EmeraldGlass = LyricsGlassStyle(
            name = "Emerald Glass",
            surfaceTint = Color(0xFF004D40),
            surfaceAlpha = 0.35f,
            cloudyRadius = 15,
            refraction = 0.20f,
            curve = 0.20f,
            glassSaturation = 1.20f,
            glassEdge = 0.25f,
            glassTint = Color(0xFF004D40).copy(alpha = 0.25f),
            secondaryTextColor = Color(0xFFE0F2F1).copy(alpha = 0.75f),
            overlayAlpha = 0.25f,
            backgroundDimAlpha = 0.35f
        )

        val SunsetGlass = LyricsGlassStyle(
            name = "Sunset Glass",
            surfaceTint = Color(0xFFFF4500),
            surfaceAlpha = 0.30f,
            cloudyRadius = 15,
            refraction = 0.20f,
            curve = 0.20f,
            glassSaturation = 1.30f,
            glassContrast = 1.05f,
            glassEdge = 0.3f,
            glassTint = Color(0xFFFF4500).copy(alpha = 0.2f),
            secondaryTextColor = Color(0xFFFFE4E1).copy(alpha = 0.8f),
            overlayAlpha = 0.25f,
            backgroundDimAlpha = 0.3f
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
}

// ── 7. FONDO DINÁMICO DE REPRODUCTOR (DynamicPlayerBackground) ─────────────
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

        secondary = ArmoryColors.Silver2,
        onSecondary = ArmoryColors.AbsoluteBlack,
        secondaryContainer = ArmoryColors.SurfaceElevated,
        onSecondaryContainer = ArmoryColors.TextPrimary,

        tertiary = ArmoryColors.Copper2,
        onTertiary = ArmoryColors.AbsoluteBlack,
        tertiaryContainer = ArmoryColors.Copper4,
        onTertiaryContainer = ArmoryColors.Copper1,

        background = ArmoryColors.AbsoluteBlack,
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

        error = ArmoryColors.Rust2,
        onError = ArmoryColors.AbsoluteBlack,
        errorContainer = ArmoryColors.Rust4,
        onErrorContainer = ArmoryColors.Rust1,

        surfaceBright = ArmoryColors.SurfaceElevated,
        surfaceDim = ArmoryColors.Background,
        surfaceContainer = ArmoryColors.SurfaceBlack,
        surfaceContainerLow = ArmoryColors.Background,
        surfaceContainerLowest = ArmoryColors.AbsoluteBlack,
        surfaceContainerHigh = ArmoryColors.SurfaceElevated,
        surfaceContainerHighest = ArmoryColors.Silver4
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
    val animSpec = spring<Color>(stiffness = androidx.compose.animation.core.Spring.StiffnessLow)
    @Composable
    fun anim(target: Color, label: String) = animateColorAsState(target, animSpec, label = label).value

    return ColorScheme(
        primary = anim(targetColorScheme.primary, "primary"),
        onPrimary = anim(targetColorScheme.onPrimary, "onPrimary"),
        primaryContainer = anim(targetColorScheme.primaryContainer, "primaryContainer"),
        onPrimaryContainer = anim(targetColorScheme.onPrimaryContainer, "onPrimaryContainer"),
        inversePrimary = anim(targetColorScheme.inversePrimary, "inversePrimary"),
        secondary = anim(targetColorScheme.secondary, "secondary"),
        onSecondary = anim(targetColorScheme.onSecondary, "onSecondary"),
        secondaryContainer = anim(targetColorScheme.secondaryContainer, "secondaryContainer"),
        onSecondaryContainer = anim(targetColorScheme.onSecondaryContainer, "onSecondaryContainer"),
        tertiary = anim(targetColorScheme.tertiary, "tertiary"),
        onTertiary = anim(targetColorScheme.onTertiary, "onTertiary"),
        tertiaryContainer = anim(targetColorScheme.tertiaryContainer, "tertiaryContainer"),
        onTertiaryContainer = anim(targetColorScheme.onTertiaryContainer, "onTertiaryContainer"),
        background = anim(targetColorScheme.background, "background"),
        onBackground = anim(targetColorScheme.onBackground, "onBackground"),
        surface = anim(targetColorScheme.surface, "surface"),
        onSurface = anim(targetColorScheme.onSurface, "onSurface"),
        surfaceVariant = anim(targetColorScheme.surfaceVariant, "surfaceVariant"),
        onSurfaceVariant = anim(targetColorScheme.onSurfaceVariant, "onSurfaceVariant"),
        surfaceTint = anim(targetColorScheme.surfaceTint, "surfaceTint"),
        inverseSurface = anim(targetColorScheme.inverseSurface, "inverseSurface"),
        inverseOnSurface = anim(targetColorScheme.inverseOnSurface, "inverseOnSurface"),
        error = anim(targetColorScheme.error, "error"),
        onError = anim(targetColorScheme.onError, "onError"),
        errorContainer = anim(targetColorScheme.errorContainer, "errorContainer"),
        onErrorContainer = anim(targetColorScheme.onErrorContainer, "onErrorContainer"),
        outline = anim(targetColorScheme.outline, "outline"),
        outlineVariant = anim(targetColorScheme.outlineVariant, "outlineVariant"),
        scrim = anim(targetColorScheme.scrim, "scrim"),
        surfaceBright = anim(targetColorScheme.surfaceBright, "surfaceBright"),
        surfaceDim = anim(targetColorScheme.surfaceDim, "surfaceDim"),
        surfaceContainer = anim(targetColorScheme.surfaceContainer, "surfaceContainer"),
        surfaceContainerLow = anim(targetColorScheme.surfaceContainerLow, "surfaceContainerLow"),
        surfaceContainerLowest = anim(targetColorScheme.surfaceContainerLowest, "surfaceContainerLowest"),
        surfaceContainerHigh = anim(targetColorScheme.surfaceContainerHigh, "surfaceContainerHigh"),
        surfaceContainerHighest = anim(targetColorScheme.surfaceContainerHighest, "surfaceContainerHighest")
    )
}

// ── PASO 2: COMPARTIDOS ─────────────────────────────────────────────────────
@Composable
fun ArmoryIcon(icon: ImageVector, contentDescription: String?, modifier: Modifier = Modifier, active: Boolean = true) =
    Icon(icon, contentDescription, modifier, if (active) ArmoryColors.Gold2 else ArmoryColors.TextMuted)

@Composable
fun ArmoryIcon(painter: androidx.compose.ui.graphics.painter.Painter, contentDescription: String?, modifier: Modifier = Modifier, active: Boolean = true) =
    Icon(painter, contentDescription, modifier, if (active) ArmoryColors.Gold2 else ArmoryColors.TextMuted)

@Composable
fun ArmoryIconButton(icon: ImageVector, contentDescription: String?, onClick: () -> Unit, modifier: Modifier = Modifier, active: Boolean = true, overImage: Boolean = false) =
    IconButton(onClick = onClick, modifier = if (overImage) modifier.background(ArmoryColors.AbsoluteBlack.copy(alpha = 0.5f), CircleShape) else modifier) {
        ArmoryIcon(icon, contentDescription, active = active)
    }

@Composable
fun ArmoryIconButton(painter: androidx.compose.ui.graphics.painter.Painter, contentDescription: String?, onClick: () -> Unit, modifier: Modifier = Modifier, active: Boolean = true, overImage: Boolean = false) =
    IconButton(onClick = onClick, modifier = if (overImage) modifier.background(ArmoryColors.AbsoluteBlack.copy(alpha = 0.5f), CircleShape) else modifier) {
        ArmoryIcon(painter, contentDescription, active = active)
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArmoryTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    overImage: Boolean = false,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) = TopAppBar(
    modifier = modifier.wrapContentHeight(Alignment.Top),
    title = { Text(title, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    navigationIcon = {
        if (onBack != null) IconButton(
            onClick = onBack,
            modifier = if (overImage) Modifier.background(ArmoryColors.AbsoluteBlack.copy(alpha = 0.5f), CircleShape) else Modifier
        ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Atrás", tint = ArmoryColors.TextGold) }
    },
    actions = actions,
    scrollBehavior = scrollBehavior,
    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = ArmoryColors.Background),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArmoryTopBar(
    title: @Composable () -> Unit,
    onBack: (() -> Unit)? = null,
    overImage: Boolean = false,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) = TopAppBar(
    modifier = modifier.wrapContentHeight(Alignment.Top),
    title = title,
    navigationIcon = {
        if (onBack != null) IconButton(
            onClick = onBack,
            modifier = if (overImage) Modifier.background(ArmoryColors.AbsoluteBlack.copy(alpha = 0.5f), CircleShape) else Modifier
        ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Atrás", tint = ArmoryColors.TextGold) }
    },
    actions = actions,
    scrollBehavior = scrollBehavior,
    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = ArmoryColors.Background),
)

@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    subtitle: String? = null,
    thumbnail: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    if (onClick != null || label != null || subtitle != null || thumbnail != null) {
        NavigationTitle(
            title = text,
            modifier = modifier,
            label = label,
            subtitle = subtitle,
            thumbnail = thumbnail,
            onClick = onClick
        )
    } else {
        Text(
            text = text,
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.headlineMedium
        )
    }
}

@Composable
fun ArmoryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    brush: Brush = ArmoryGradients.MetallicGold,
    icon: ImageVector? = null,
    painter: androidx.compose.ui.graphics.painter.Painter? = null
) = Row(
    modifier.defaultMinSize(minHeight = 44.dp).clip(CircleShape).background(brush)
        .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 24.dp),
    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
) {
    CompositionLocalProvider(LocalContentColor provides ArmoryColors.AbsoluteBlack) {
        if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        if (painter != null) { Icon(painter, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}
