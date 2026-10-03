/*
 * ARMORY Project - Centralized App Style & Branding System (2026)
 * Single Source of Truth for Fonts, Typography, Colors, Brushes, Themes, and Visual Components
 */

package com.ruben.spotitube

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json

// ── 1. TIPOGRAFÍA GLOBAL (Baskerville) ──────────────────────────────────────
val GlobalFont = FontFamily(Font(R.font.baskerville))

val PremiumTitleFont = GlobalFont
val PremiumBodyFont = GlobalFont

object ArmoryFont {
    val Title = GlobalFont
    val Body = GlobalFont
}

private fun buildTypography() =
    Typography(
        displayLarge = TextStyle(
            fontFamily = GlobalFont,
            fontWeight = FontWeight.Black,
            fontSize = 32.sp,
            lineHeight = 40.sp,
            letterSpacing = (-0.5).sp
        ),
        headlineLarge = TextStyle(
            fontFamily = GlobalFont,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            lineHeight = 32.sp,
            letterSpacing = (-0.2).sp
        ),
        headlineMedium = TextStyle(
            fontFamily = GlobalFont,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            lineHeight = 28.sp,
            letterSpacing = 0.sp
        ),
        titleLarge = TextStyle(
            fontFamily = GlobalFont,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.sp
        ),
        titleMedium = TextStyle(
            fontFamily = GlobalFont,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.1.sp
        ),
        bodyLarge = TextStyle(
            fontFamily = GlobalFont,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.25.sp
        ),
        bodyMedium = TextStyle(
            fontFamily = GlobalFont,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.25.sp
        ),
        labelLarge = TextStyle(
            fontFamily = GlobalFont,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp
        )
    )

val AppTypography = buildTypography()
val SystemTypography = buildTypography()


// ── 2. SISTEMA DE COLORES Y PINCELES METÁLICOS ──────────────────────────────
object ArmoryColors {

    // DORADO — Principal
    val Gold1 = Color(0xFFC9A84C)
    val Gold2 = Color(0xFFDAA520)
    val Gold3 = Color(0xFFB8860B)
    val Gold4 = Color(0xFF8B6914)
    val Gold5 = Color(0xFF3D2B00)

    val GoldGradient = Brush.verticalGradient(
        colors = listOf(Gold1, Gold3)
    )

    // PLATA — Base neutra
    val Silver1 = Color(0xFFC0C0C0)
    val Silver2 = Color(0xFFB0B0B0)
    val Silver3 = Color(0xFF606060)
    val Silver4 = Color(0xFF2A2A2A)
    val Silver5 = Color(0xFF121212)

    // COBRE — Acción
    val Copper1 = Color(0xFFE8956D)
    val Copper2 = Color(0xFFCD7F32)
    val Copper3 = Color(0xFFA0522D)
    val Copper4 = Color(0xFF3D1A00)
    val Copper5 = Color(0xFF1A0D00)

    val Gold = Brush.linearGradient(
        colors = listOf(
            Color(0xFF8C6126),
            Color(0xFFC89B48),
            Color(0xFFECC675),
            Color(0xFFFAF0BD),
            Color(0xFFC89B48)
        )
    )

    // Cobre
    val Copper = Brush.linearGradient(
        colors = listOf(
            Color(0xFFA36B56),
            Color(0xFFD49A83),
            Color(0xFFF8D3C4),
            Color(0xFFD49A83)
        )
    )

    // Plata
    val Silver = Brush.linearGradient(
        colors = listOf(
            Color(0xFF828588),
            Color(0xFFB8BBBE),
            Color(0xFFE6E8EA),
            Color(0xFFB8BBBE)
        )
    )

    // Negro Azabache Mate Metálico
    val JetBlack = Brush.linearGradient(
        colors = listOf(
            Color(0xFF0A0A0A),
            Color(0xFF141414),
            Color(0xFF2A2A2A),
            Color(0xFF141414)
        )
    )

    // OXIDADO — Alerta
    val Rust1 = Color(0xFFE57373)
    val Rust2 = Color(0xFFC0392B)
    val Rust3 = Color(0xFF8B0000)
    val Rust4 = Color(0xFF2C0000)
    val Rust5 = Color(0xFF1A0000)

    // DIAMANTE — Pro
    val Diamond1 = Color(0xFFADD8E6)
    val Diamond2 = Color(0xFF4FC3F7)
    val Diamond3 = Color(0xFF0288D1)
    val Diamond4 = Color(0xFF01002A)
    val Diamond5 = Color(0xFF00051A)

    // COLORES DE ACENTO Y ALIAS PARA COMPONENTES
    val Champagne    = Color(0xFFF7E7CE)
    val GoldPrimary  = Gold1
    val GoldBright   = Color(0xFFFFE766)
    val ObsidianSoft = Color(0xFF0F0F0F)

    // SUPERFICIES
    val AbsoluteBlack = Color(0xFF000000)
    val SurfaceBlack  = Color(0xFF141414)

    // TEXTO
    val TextPrimary = Color(0xFFF5F5F5)
    val TextMuted   = Color(0xFF707070)
    val Divider     = Color(0xFF2A2A2A)
}

val OpenTuneColors = ArmoryColors


// ── 3. GRADIENTES Y FONDOS DE MARCA ─────────────────────────────────────────
object ArmoryGradients {
    val GoldGradient = Brush.linearGradient(
        colors = listOf(ArmoryColors.Gold1, ArmoryColors.Gold3, ArmoryColors.Gold5)
    )

    val GoldCopperGradient = Brush.linearGradient(
        colors = listOf(ArmoryColors.Gold3, ArmoryColors.Copper3)
    )

    val GoldSilverGradient = Brush.linearGradient(
        colors = listOf(ArmoryColors.Gold3, ArmoryColors.Silver3)
    )

    val GoldRustGradient = Brush.linearGradient(
        colors = listOf(ArmoryColors.Gold3, ArmoryColors.Rust3)
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
}

val GoldGradient = ArmoryGradients.GoldGradient
val GoldCopperGradient = ArmoryGradients.GoldCopperGradient
val GoldSilverGradient = ArmoryGradients.GoldSilverGradient
val GoldRustGradient = ArmoryGradients.GoldRustGradient
val GoldDiamondGradient = ArmoryGradients.GoldDiamondGradient
val GlassGoldBackground = ArmoryGradients.GlassGoldBackground
val ObsidianGradient = ArmoryGradients.ObsidianGradient


// ── 4. PALETAS DE TEMA SERIALIZABLES ─────────────────────────────────────────
@Serializable
data class ThemeSeedPalette(
    @Serializable(with = ColorSerializer::class) val primary: Color,
    @Serializable(with = ColorSerializer::class) val secondary: Color,
    @Serializable(with = ColorSerializer::class) val tertiary: Color,
    @Serializable(with = ColorSerializer::class) val neutral: Color,
)

@Serializable
private data class NamedSeedPalette(
    val name: String? = null,
    val palette: ThemeSeedPalette
)

object ThemeSeedPaletteCodec {
    private val json = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = true
    }

    fun encodeAsJson(palette: ThemeSeedPalette, name: String?): String {
        return json.encodeToString(NamedSeedPalette(name, palette))
    }

    fun decodeFromJson(text: String): ThemeSeedPalette? {
        return try {
            json.decodeFromString<NamedSeedPalette>(text).palette
        } catch (e: Exception) {
            null
        }
    }

    fun extractNameFromJsonOrNull(text: String): String? {
        return try {
            json.decodeFromString<NamedSeedPalette>(text).name
        } catch (e: Exception) {
            null
        }
    }

    fun encodeForPreference(palette: ThemeSeedPalette, name: String?): String {
        return encodeAsJson(palette, name)
    }

    fun decodeFromPreference(value: String): ThemeSeedPalette? {
        if (!value.startsWith("{")) return null
        return decodeFromJson(value)
    }

    fun extractNameFromPreference(value: String): String? {
        if (!value.startsWith("{")) return null
        return extractNameFromJsonOrNull(value)
    }
}

object ColorSerializer : KSerializer<Color> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Color", PrimitiveKind.LONG)
    override fun serialize(encoder: Encoder, value: Color) = encoder.encodeLong(value.value.toLong())
    override fun deserialize(decoder: Decoder): Color = Color(decoder.decodeLong().toULong())
}

val ColorSaver = Saver<Color, Int>(
    save = { it.toArgb() },
    restore = { Color(it) }
)

data class ThemePalette(
    val id: String,
    val nameResId: Int,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val neutral: Color,
    val onPrimary: Color = if (primary.luminance() > 0.5f) Color.Black else Color.White
)

object ThemePalettes {
    val Gold = ThemePalette(
        id = "opentune_gold",
        nameResId = R.string.palette_opentune_gold,
        primary = ArmoryColors.Gold1,
        secondary = ArmoryColors.Gold2,
        tertiary = ArmoryColors.Gold3,
        neutral = ArmoryColors.Gold4
    )

    val Silver = ThemePalette(
        id = "opentune_silver",
        nameResId = R.string.palette_opentune_silver,
        primary = ArmoryColors.Silver1,
        secondary = ArmoryColors.Silver2,
        tertiary = ArmoryColors.Silver3,
        neutral = ArmoryColors.Silver4
    )

    val Copper = ThemePalette(
        id = "opentune_copper",
        nameResId = R.string.palette_opentune_copper,
        primary = ArmoryColors.Copper1,
        secondary = ArmoryColors.Copper2,
        tertiary = ArmoryColors.Copper3,
        neutral = ArmoryColors.Copper4
    )

    val Rust = ThemePalette(
        id = "opentune_rust",
        nameResId = R.string.palette_opentune_rust,
        primary = ArmoryColors.Rust1,
        secondary = ArmoryColors.Rust2,
        tertiary = ArmoryColors.Rust3,
        neutral = ArmoryColors.Rust4
    )

    val Diamond = ThemePalette(
        id = "opentune_diamond",
        nameResId = R.string.palette_opentune_diamond,
        primary = ArmoryColors.Diamond1,
        secondary = ArmoryColors.Diamond2,
        tertiary = ArmoryColors.Diamond3,
        neutral = ArmoryColors.Diamond4
    )

    val allPalettes: List<ThemePalette> = listOf(
        Gold,
        Silver,
        Copper,
        Rust,
        Diamond
    )

    val Default = Gold

    fun findByPrimaryColor(colorHex: String): ThemePalette? {
        return allPalettes.find { it.primary.toHexString() == colorHex }
    }

    fun findById(id: String): ThemePalette? {
        return allPalettes.find { it.id == id }
    }
}

fun Color.toHexString(): String {
    val red = (this.red * 255).toInt()
    val green = (this.green * 255).toInt()
    val blue = (this.blue * 255).toInt()
    return String.format("#%02X%02X%02X", red, green, blue)
}


// ── 5. COLORES DE DESLIZADORES DEL REPRODUCTOR ──────────────────────────────
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
        return getSliderColors(
            activeColor = ArmoryColors.Copper2,
            inactiveAlpha = Config.INACTIVE_TRACK_ALPHA
        )
    }

    @Composable
    fun wavySliderColors(buttonColor: Color): SliderColors {
        return SliderDefaults.colors(
            activeTrackColor = ArmoryColors.Copper2,
            activeTickColor = ArmoryColors.Copper2,
            thumbColor = Color.Transparent,
            inactiveTrackColor = Color.White.copy(alpha = Config.INACTIVE_TRACK_ALPHA),
            inactiveTickColor = Color.White.copy(alpha = Config.INACTIVE_TICK_ALPHA)
        )
    }

    @Composable
    fun thickSliderColors(buttonColor: Color): SliderColors {
        return getSliderColors(
            activeColor = ArmoryColors.Copper2,
            inactiveAlpha = Config.THICK_INACTIVE_TRACK_ALPHA
        )
    }

    @Composable
    fun circularSliderColors(buttonColor: Color): SliderColors {
        return SliderDefaults.colors(
            activeTrackColor = ArmoryColors.Copper2,
            activeTickColor = ArmoryColors.Copper2,
            thumbColor = ArmoryColors.Copper2,
            inactiveTrackColor = Color.White.copy(alpha = Config.INACTIVE_TRACK_ALPHA)
        )
    }

    @Composable
    fun simpleSliderColors(buttonColor: Color): SliderColors {
        return SliderDefaults.colors(
            activeTrackColor = ArmoryColors.Copper2.copy(alpha = Config.SIMPLE_ACTIVE_TRACK_ALPHA),
            activeTickColor = ArmoryColors.Copper2.copy(alpha = Config.SIMPLE_ACTIVE_TRACK_ALPHA),
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
        val DEFAULT_ACTIVE_COLOR = ArmoryColors.Copper2
        val DEFAULT_INACTIVE_COLOR = Color.White.copy(alpha = INACTIVE_TRACK_ALPHA)
    }
}


// ── 6. ESTILOS DE CRISTAL / VIDRIO (LYRICS GLASS STYLE) ─────────────────────
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


// ── 7. COMPONENTES VISUALES Y RECURSOS DE PLANTILLA ─────────────────────────
@Composable
fun DynamicBackground(thumbnailUrl: String) {
    val dominantColor = Color.Black
    val animatedColor by animateColorAsState(
        targetValue = if (thumbnailUrl.isEmpty()) Color.Transparent else dominantColor.copy(alpha = 0.22f),
        animationSpec = tween(2000),
        label = "bgDynamic"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ArmoryColors.AbsoluteBlack)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        animatedColor,
                        ArmoryColors.SurfaceBlack.copy(alpha = 0.3f),
                        Color.Transparent
                    ),
                    startY = 0f,
                    endY = 1200f
                )
            )
    )
}

enum class PlaylistIconType { VINYL, CROWN, GUITAR, NOTES, DIAMOND, FLAME }

data class PlaylistItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val gradientColors: List<Color>,
    val iconType: PlaylistIconType
)

val samplePlaylists = listOf(
    PlaylistItem("top50es", "TOP 50 ESPAÑA", "Los más virales hoy",
        listOf(ArmoryColors.Gold2, ArmoryColors.AbsoluteBlack),
        PlaylistIconType.CROWN),
    PlaylistItem("exitos", "ÉXITOS ESPAÑA", "Lo que más suena",
        listOf(ArmoryColors.Silver2, ArmoryColors.AbsoluteBlack),
        PlaylistIconType.FLAME),
    PlaylistItem("novedades", "NOVEDADES", "Recién salido",
        listOf(ArmoryColors.Copper2, ArmoryColors.AbsoluteBlack),
        PlaylistIconType.GUITAR),
    PlaylistItem("alertas", "TENDENCIAS", "Lo más buscado",
        listOf(ArmoryColors.Rust2, ArmoryColors.AbsoluteBlack),
        PlaylistIconType.VINYL),
    PlaylistItem("premium", "SELECCIÓN ELITE", "Contenido exclusivo",
        listOf(ArmoryColors.Diamond2, ArmoryColors.AbsoluteBlack),
        PlaylistIconType.DIAMOND),
)

val OpenTunePlaylistEmojis = listOf("🎵", "🎶", "🎧", "🎹", "🎸", "🎷", "🥁", "🎤", "🎼", "💿", "📀", "✨", "🔥", "👑", "💎")

val OpenTunePlaylistGradients = listOf(
    listOf(ArmoryColors.SurfaceBlack, ArmoryColors.AbsoluteBlack),
    listOf(ArmoryColors.Gold5.copy(alpha = 0.4f), ArmoryColors.AbsoluteBlack),
    listOf(ArmoryColors.Copper3.copy(alpha = 0.3f), ArmoryColors.AbsoluteBlack),
    listOf(Color(0xFF1A1A1A), Color(0xFF050505))
)

val quickFilters = listOf("Top España", "Éxitos Hoy", "Urbano", "Pop Español", "Indie", "Flamenco", "Rock", "Relax")


// ── 8. SISTEMA DE TEMA Y ESQUEMA DE COLOR MATERIAL 3 ───────────────────────
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ArmoryTheme(
    pureBlack: Boolean = true,
    themeColor: Color? = null,
    seedPalette: ThemeSeedPalette? = null,
    content: @Composable () -> Unit,
) {
    val typography = AppTypography
    val bg = ArmoryColors.AbsoluteBlack

    val colorScheme = ColorScheme(
        primary = themeColor ?: seedPalette?.primary ?: ArmoryColors.Gold3,
        onPrimary = ArmoryColors.AbsoluteBlack,
        primaryContainer = ArmoryColors.Gold5,
        onPrimaryContainer = ArmoryColors.Gold1,
        inversePrimary = ArmoryColors.Gold2,

        secondary = seedPalette?.secondary ?: ArmoryColors.Silver2,
        onSecondary = ArmoryColors.AbsoluteBlack,
        secondaryContainer = ArmoryColors.AbsoluteBlack,
        onSecondaryContainer = ArmoryColors.Silver1,

        tertiary = seedPalette?.tertiary ?: ArmoryColors.Copper2,
        onTertiary = ArmoryColors.AbsoluteBlack,
        tertiaryContainer = ArmoryColors.Copper4,
        onTertiaryContainer = ArmoryColors.Copper1,

        background = bg,
        onBackground = ArmoryColors.TextPrimary,
        surface = ArmoryColors.AbsoluteBlack,
        onSurface = ArmoryColors.TextPrimary,
        surfaceVariant = ArmoryColors.AbsoluteBlack,
        onSurfaceVariant = ArmoryColors.Silver2,
        inverseSurface = ArmoryColors.Silver1,
        inverseOnSurface = ArmoryColors.AbsoluteBlack,

        outline = ArmoryColors.Gold4,
        outlineVariant = ArmoryColors.Gold5,
        scrim = ArmoryColors.AbsoluteBlack,
        surfaceTint = ArmoryColors.Gold3,
        
        error = ArmoryColors.Rust2,
        onError = ArmoryColors.AbsoluteBlack,
        errorContainer = ArmoryColors.Rust4,
        onErrorContainer = ArmoryColors.Rust1,

        surfaceBright = ArmoryColors.AbsoluteBlack,
        surfaceDim = bg,
        surfaceContainer = ArmoryColors.AbsoluteBlack,
        surfaceContainerLow = bg,
        surfaceContainerLowest = ArmoryColors.AbsoluteBlack,
        surfaceContainerHigh = ArmoryColors.AbsoluteBlack,
        surfaceContainerHighest = ArmoryColors.AbsoluteBlack,
    )

    val animatedColorScheme = animateColorScheme(colorScheme)

    MaterialExpressiveTheme(
        colorScheme = animatedColorScheme,
        typography = typography,
        content = content
    )
}

val OpenTuneTheme = @Composable { content: @Composable () -> Unit ->
    ArmoryTheme(content = content)
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
