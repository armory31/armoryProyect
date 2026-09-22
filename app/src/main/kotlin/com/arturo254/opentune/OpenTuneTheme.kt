package com.arturo254.opentune

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val GoldGradient = Brush.linearGradient(
    colors = listOf(OpenTuneColors.Gold1, OpenTuneColors.Gold3, OpenTuneColors.Gold5)
)

val GoldCopperGradient = Brush.linearGradient(
    colors = listOf(OpenTuneColors.Gold3, OpenTuneColors.Copper3)
)

val GoldSilverGradient = Brush.linearGradient(
    colors = listOf(OpenTuneColors.Gold3, OpenTuneColors.Silver3)
)

val GoldRustGradient = Brush.linearGradient(
    colors = listOf(OpenTuneColors.Gold3, OpenTuneColors.Rust3)
)

val GoldDiamondGradient = Brush.linearGradient(
    colors = listOf(OpenTuneColors.Gold3, OpenTuneColors.Diamond2)
)

val GlassGoldBackground = Brush.verticalGradient(
    colors = listOf(
        Color(0xCC0A0A0A),
        Color(0xE6000000)
    )
)

val ObsidianGradient = Brush.verticalGradient(
    colors = listOf(
        OpenTuneColors.SurfaceBlack,
        OpenTuneColors.AbsoluteBlack,
        Color(0xFF050505)
    )
)

@Composable
fun DynamicBackground(thumbnailUrl: String) {
    // Note: rememberDominantColor should be imported or defined
    val dominantColor = Color.Black // Placeholder if not found
    val animatedColor by animateColorAsState(
        targetValue = if (thumbnailUrl.isEmpty()) Color.Transparent else dominantColor.copy(alpha = 0.22f),
        animationSpec = tween(2000),
        label = "bgDynamic"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OpenTuneColors.AbsoluteBlack)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        animatedColor,
                        OpenTuneColors.SurfaceBlack.copy(alpha = 0.3f),
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
        listOf(OpenTuneColors.Gold2, OpenTuneColors.AbsoluteBlack),
        PlaylistIconType.CROWN),
    PlaylistItem("exitos", "ÉXITOS ESPAÑA", "Lo que más suena",
        listOf(OpenTuneColors.Silver2, OpenTuneColors.AbsoluteBlack),
        PlaylistIconType.FLAME),
    PlaylistItem("novedades", "NOVEDADES", "Recién salido",
        listOf(OpenTuneColors.Copper2, OpenTuneColors.AbsoluteBlack),
        PlaylistIconType.GUITAR),
    PlaylistItem("alertas", "TENDENCIAS", "Lo más buscado",
        listOf(OpenTuneColors.Rust2, OpenTuneColors.AbsoluteBlack),
        PlaylistIconType.VINYL),
    PlaylistItem("premium", "SELECCIÓN ELITE", "Contenido exclusivo",
        listOf(OpenTuneColors.Diamond2, OpenTuneColors.AbsoluteBlack),
        PlaylistIconType.DIAMOND),
)

// Emojis para portadas automáticas
val OpenTunePlaylistEmojis = listOf("🎵", "🎶", "🎧", "🎹", "🎸", "🎷", "🥁", "🎤", "🎼", "💿", "📀", "✨", "🔥", "👑", "💎")

// Gradientes premium para los fondos de los emojis
val OpenTunePlaylistGradients = listOf(
    listOf(OpenTuneColors.SurfaceBlack, OpenTuneColors.AbsoluteBlack),
    listOf(OpenTuneColors.Gold5.copy(alpha = 0.4f), OpenTuneColors.AbsoluteBlack),
    listOf(OpenTuneColors.Copper3.copy(alpha = 0.3f), OpenTuneColors.AbsoluteBlack),
    listOf(Color(0xFF1A1A1A), Color(0xFF050505))
)


val quickFilters = listOf("Top España", "Éxitos Hoy", "Urbano", "Pop Español", "Indie", "Flamenco", "Rock", "Relax")
