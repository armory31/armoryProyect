package com.ruben.spotitube

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

/**
 * Alias de compatibilidad para evitar errores durante el refactoring
 */
val OpenTuneColors = ArmoryColors
