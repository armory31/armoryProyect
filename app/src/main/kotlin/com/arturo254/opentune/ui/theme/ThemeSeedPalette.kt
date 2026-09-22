package com.arturo254.opentune.ui.theme

import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.arturo254.opentune.OpenTuneColors
import com.arturo254.opentune.R
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json

/**
 * Representa una semilla de colores para generar un esquema de Material3.
 */
@Serializable
data class ThemeSeedPalette(
    @Serializable(with = ColorSerializer::class) val primary: Color,
    @Serializable(with = ColorSerializer::class) val secondary: Color,
    @Serializable(with = ColorSerializer::class) val tertiary: Color,
    @Serializable(with = ColorSerializer::class) val neutral: Color,
)

/**
 * Estructura interna para serializar la paleta junto con su nombre.
 */
@Serializable
private data class NamedSeedPalette(
    val name: String? = null,
    val palette: ThemeSeedPalette
)

/**
 * Codificador/Decodificador para persistir [ThemeSeedPalette] en preferencias o archivos.
 */
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

/**
 * Serializador personalizado para la clase Color de Compose (ULong).
 */
object ColorSerializer : KSerializer<Color> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Color", PrimitiveKind.LONG)
    override fun serialize(encoder: Encoder, value: Color) = encoder.encodeLong(value.value.toLong())
    override fun deserialize(decoder: Decoder): Color = Color(decoder.decodeLong().toULong())
}

/**
 * Saver para persistir Color en rememberSaveable.
 */
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
        primary = OpenTuneColors.Gold1,
        secondary = OpenTuneColors.Gold2,
        tertiary = OpenTuneColors.Gold3,
        neutral = OpenTuneColors.Gold4
    )

    val Silver = ThemePalette(
        id = "opentune_silver",
        nameResId = R.string.palette_opentune_silver,
        primary = OpenTuneColors.Silver1,
        secondary = OpenTuneColors.Silver2,
        tertiary = OpenTuneColors.Silver3,
        neutral = OpenTuneColors.Silver4
    )

    val Copper = ThemePalette(
        id = "opentune_copper",
        nameResId = R.string.palette_opentune_copper,
        primary = OpenTuneColors.Copper1,
        secondary = OpenTuneColors.Copper2,
        tertiary = OpenTuneColors.Copper3,
        neutral = OpenTuneColors.Copper4
    )

    val Rust = ThemePalette(
        id = "opentune_rust",
        nameResId = R.string.palette_opentune_rust,
        primary = OpenTuneColors.Rust1,
        secondary = OpenTuneColors.Rust2,
        tertiary = OpenTuneColors.Rust3,
        neutral = OpenTuneColors.Rust4
    )

    val Diamond = ThemePalette(
        id = "opentune_diamond",
        nameResId = R.string.palette_opentune_diamond,
        primary = OpenTuneColors.Diamond1,
        secondary = OpenTuneColors.Diamond2,
        tertiary = OpenTuneColors.Diamond3,
        neutral = OpenTuneColors.Diamond4
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
