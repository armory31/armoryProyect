package com.arturo254.opentune

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

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
    val Silver1 = Color(0xFFE8E8E8)
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
    val DeepBlack     = Color(0xFF0A0A0A)
    val SurfaceBlack  = Color(0xFF141414)
    val CardBlack     = Color(0xFF1C1C1C)

    // TEXTO
    val TextPrimary = Color(0xFFF5F5F5)
    val TextMuted   = Color(0xFF707070)
    val Divider     = Color(0xFF2A2A2A)

    // COMPATIBILIDAD
    val ArmoryCobre = Copper2
    val ArmoryPlataBg = Silver4
    val ArmoryGold = Gold1
    val ActionCopper = Copper2
    val BackgroundBlack = DeepBlack
}

/**
 * Alias de compatibilidad para evitar errores durante el refactoring
 */
val OpenTuneColors = ArmoryColors
