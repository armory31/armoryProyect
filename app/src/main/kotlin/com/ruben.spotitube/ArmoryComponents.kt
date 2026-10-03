package com.ruben.spotitube

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.toBitmap
import com.ruben.spotitube.models.MediaMetadata

enum class PlayerStatus { PLAYING, PAUSED, LOADING }

// ── Extractor de Color Dominante (Palette) ──────────────────────────────────
@Composable
fun rememberDominantColor(url: String): Color {
    var dominantColor by remember(url) { mutableStateOf(Color.Transparent) }
    val ctx = LocalContext.current

    LaunchedEffect(url) {
        if (url.isEmpty()) return@LaunchedEffect
        try {
            val loader = ctx.imageLoader
            val request = ImageRequest.Builder(ctx).data(url).allowHardware(false).build()
            val result = loader.execute(request)
            val bitmap: Bitmap = (result as? SuccessResult)?.image?.toBitmap()
                ?: return@LaunchedEffect

            Palette.Builder(bitmap).generate { palette ->
                val rgb = palette?.vibrantSwatch?.rgb ?: palette?.dominantSwatch?.rgb ?: return@generate
                dominantColor = Color(rgb)
            }
        } catch (_: Exception) {}
    }
    return animateColorAsState(targetValue = dominantColor, animationSpec = tween(1000), label = "glowColor").value
}

// ── Thumbnail con Estilo ARMORY ──────────────────────────────────────────────
@Composable
fun TrackThumbnail(url: String, size: Int = 52, radius: Int = 4) {
    val ctx = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(ctx)
            .data(url)
            .crossfade(true)
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(size.dp).clip(RoundedCornerShape(radius.dp))
    )
}



// ── Header ARMORY ───────────────────────────────────────────────────────────
@Composable
fun ArmoryHeader(
    avatarUrl: String? = null,
    onLogout: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .statusBarsPadding()
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1/5 (20%) - Logo en la parte superior izquierda
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart
        ) {
            Image(
                painter = painterResource(R.drawable.armory_logo),
                contentDescription = "Logo",
                modifier = Modifier.size(40.dp)
            )
        }

        // 3/5 (60%) - Palabra ARMORY con color gradiente gold nuevo
        Box(
            modifier = Modifier.weight(3f),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "ARMORY",
                style = TextStyle(
                    brush = ArmoryColors.Gold
                ),
                fontFamily = PremiumTitleFont,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp,
                textAlign = TextAlign.Center
            )
        }

        // 1/5 (20%) - Botón de cuenta en el 1/5 restante
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterEnd
        ) {
            if (onSettings != null) {
                IconButton(
                    onClick = onSettings,
                    modifier = Modifier
                        .size(40.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), CircleShape)
                ) {
                    if (avatarUrl != null) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = "Cuenta",
                            modifier = Modifier.size(24.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = "Cuenta",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ArmoryFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        selected = selected,
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) ArmoryColors.GoldPrimary else ArmoryColors.SurfaceBlack,
        border = if (selected) null else BorderStroke(1.dp, ArmoryColors.Divider),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            label()
        }
    }
}

/**
 * Alias de compatibilidad
 */
@Composable
fun OpenTuneHeader(
    avatarUrl: String? = null,
    onLogout: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null
) = ArmoryHeader(avatarUrl, onLogout, onSettings)

@Composable
fun GoldDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        ArmoryColors.Gold4.copy(alpha = 0.5f),
                        ArmoryColors.Gold2,
                        ArmoryColors.Gold4.copy(alpha = 0.5f),
                        Color.Transparent
                    )
                )
            )
    )
}



// ── Playlist Card Metálica ──────────────────────────────────────────────────
@Composable
fun MetallicPlaylistCard(playlist: PlaylistItem, onClick: () -> Unit) {
    val brandColor = playlist.gradientColors.first()
    val darkVariant = when (brandColor) {
        ArmoryColors.Gold3 -> ArmoryColors.Gold5
        ArmoryColors.Silver3 -> ArmoryColors.Silver5
        ArmoryColors.Copper3 -> ArmoryColors.Copper5
        ArmoryColors.Rust3 -> ArmoryColors.Rust5
        ArmoryColors.Diamond3 -> ArmoryColors.Diamond5
        else -> ArmoryColors.AbsoluteBlack
    }
    
    Box(
        modifier = Modifier
            .width(155.dp)
            .height(195.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(ArmoryColors.AbsoluteBlack)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(brandColor.copy(alpha = 0.25f), Color.Transparent),
                    start = Offset(0f, 0f),
                    end = Offset(100f, 100f)
                ),
                shape = RoundedCornerShape(24.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(darkVariant.copy(alpha = 0.6f), Color.Transparent), endY = 500f)
        ))
        Box(modifier = Modifier.fillMaxSize().background(
            Brush.radialGradient(listOf(brandColor.copy(alpha = 0.2f), Color.Transparent), center = Offset(0f, 0f), radius = 450f)
        ))
        
        Column(modifier = Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Box(modifier = Modifier.size(48.dp).background(brandColor.copy(alpha = 0.15f), CircleShape).border(1.dp, brandColor.copy(alpha = 0.4f), CircleShape), contentAlignment = Alignment.Center) {
                PlaylistIcon(playlist.iconType, brandColor)
            }
            Column {
                Text(playlist.title, color = ArmoryColors.Champagne, fontFamily = PremiumTitleFont, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 2)
                Text(playlist.subtitle, color = ArmoryColors.TextMuted, fontFamily = PremiumBodyFont, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

@Composable
fun PlaylistIcon(type: PlaylistIconType, tint: Color) {
    val icon = when (type) {
        PlaylistIconType.VINYL -> Icons.Filled.Album
        PlaylistIconType.CROWN -> Icons.Filled.Star
        PlaylistIconType.GUITAR -> Icons.Filled.MusicNote
        PlaylistIconType.NOTES -> Icons.Filled.LibraryMusic
        PlaylistIconType.DIAMOND -> Icons.Filled.Diamond
        PlaylistIconType.FLAME -> Icons.Filled.Whatshot
    }
    Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
}

// ── Mini Player Premium ─────────────────────────────────────────────────────
@Composable
fun MiniPlayer(
    nowPlaying: MediaMetadata,
    playerStatus: PlayerStatus,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClick: () -> Unit,
    position: Long = 0L,
    duration: Long = 0L
) {
    val dynamicColor = rememberDominantColor(nowPlaying.thumbnailUrl ?: "")
    val progress = if (duration > 0) position.toFloat() / duration else 0f
    
    // Animación de escala al pulsar
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isPressed) 0.97f else 1f, label = "scale")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clip(RoundedCornerShape(16.dp))
            .background(brush = ArmoryColors.JetBlack)
            .background(
                Brush.horizontalGradient(
                    listOf(dynamicColor.copy(alpha = 0.35f), Color.Transparent),
                    endX = 500f
                )
            )
            .border(
                width = 2.dp,
                brush = ArmoryColors.Gold,
                shape = RoundedCornerShape(16.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = { onClick() }
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TrackThumbnail(nowPlaying.thumbnailUrl ?: "", size = 48, radius = 10)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    nowPlaying.title,
                    color = ArmoryColors.Silver1,
                    fontSize = 14.sp,
                    fontFamily = PremiumTitleFont,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    nowPlaying.artists.joinToString { it.name },
                    color = ArmoryColors.Silver3,
                    fontSize = 12.sp,
                    fontFamily = PremiumBodyFont,
                    maxLines = 1
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(onClick = onPrevious) {
                    Icon(
                        painter = painterResource(R.drawable.skip_previous),
                        contentDescription = null,
                        tint = ArmoryColors.Silver1,
                        modifier = Modifier.size(28.dp)
                    )
                }

                if (playerStatus == PlayerStatus.LOADING) {
                    CircularProgressIndicator(
                        color = ArmoryColors.Gold1,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    IconButton(onClick = onToggle) {
                        Icon(
                            painter = painterResource(
                                if (playerStatus == PlayerStatus.PLAYING) R.drawable.pause else R.drawable.play
                            ),
                            contentDescription = null,
                            tint = ArmoryColors.Gold1,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                IconButton(onClick = onNext) {
                    Icon(
                        painter = painterResource(R.drawable.skip_next),
                        contentDescription = null,
                        tint = ArmoryColors.Silver1,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        // Barra de progreso minimalista en la base
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(2.5.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(ArmoryColors.Gold5, ArmoryColors.Gold2)
                    )
                )
        )
    }
}

// Bottom Navigation Premium
@Composable
fun ArmoryBottomBar(
    selectedTab: Int,
    onTabChange: (Int) -> Unit,
    nowPlaying: MediaMetadata?,
    playerStatus: PlayerStatus,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onPlayerClick: () -> Unit,
    position: Long = 0L,
    duration: Long = 0L,
    showNavigationBar: Boolean = true
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = if (showNavigationBar) 12.dp else 24.dp)
    ) {
        AnimatedVisibility(
            visible = nowPlaying != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .padding(bottom = if (showNavigationBar) 10.dp else 0.dp)
                ) {
                    if (nowPlaying != null) {
                        MiniPlayer(
                            nowPlaying = nowPlaying,
                            playerStatus = playerStatus,
                            onToggle = onToggle,
                            onNext = onNext,
                            onPrevious = onPrevious,
                            onClick = onPlayerClick,
                            position = position,
                            duration = duration
                        )
                    }
                }
            }
        }

        if (showNavigationBar) {
            Surface(
                color = ArmoryColors.ObsidianSoft,
                shape = RoundedCornerShape(26.dp),
                tonalElevation = 8.dp,
                modifier = Modifier
                    .height(76.dp)
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.12f),
                                Color.Transparent
                            )
                        ),
                        shape = RoundedCornerShape(26.dp)
                    )
            ) {
                NavigationBar(
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp,
                    windowInsets = WindowInsets(0, 0, 0, 0)
                ) {
                    val tabs = listOf(
                        Triple(0, Icons.Filled.Home, "Inicio"),
                        Triple(1, Icons.Filled.Explore, "Explorar"),
                        Triple(2, Icons.Filled.Style, "Géneros"),
                        Triple(3, Icons.Filled.LibraryMusic, "Biblioteca")
                    )
                    tabs.forEach { (idx, icon, label) ->
                        val isSelected = selectedTab == idx
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { onTabChange(idx) },
                            icon = {
                                Icon(
                                    icon,
                                    null,
                                    tint = if (isSelected) ArmoryColors.Gold2 else ArmoryColors.Silver3.copy(alpha = 0.8f),
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            label = {
                                Text(
                                    label,
                                    color = if (isSelected) ArmoryColors.Gold2 else ArmoryColors.Silver3.copy(alpha = 0.8f),
                                    fontSize = 11.sp,
                                    fontFamily = PremiumBodyFont,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = ArmoryColors.GoldPrimary.copy(alpha = 0.12f)
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OpenTuneNavigationBar(
    selectedTab: Int,
    onTabChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(26.dp),
        tonalElevation = 8.dp,
        modifier = modifier
            .height(62.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(brush = ArmoryColors.JetBlack)
            .border(
                width = 2.dp,
                brush = ArmoryColors.Gold,
                shape = RoundedCornerShape(26.dp)
            )
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            windowInsets = WindowInsets(0, 0, 0, 0)
        ) {
            val tabs = listOf(
                Triple(0, Icons.Filled.Home, "Inicio"),
                Triple(1, Icons.Filled.Explore, "Explorar"),
                Triple(2, Icons.Filled.Style, "Géneros"),
                Triple(3, Icons.Filled.LibraryMusic, "Biblioteca")
            )
            tabs.forEach { (idx, icon, label) ->
                val isSelected = selectedTab == idx
                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onTabChange(idx) },
                    icon = {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier
                                .size(24.dp)
                                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                                .drawWithCache {
                                    onDrawWithContent {
                                        drawContent()
                                        drawRect(
                                            brush = if (isSelected) ArmoryColors.Gold else ArmoryColors.Silver,
                                            blendMode = BlendMode.SrcIn
                                        )
                                    }
                                }
                        )
                    },
                    label = {
                        Text(
                            text = label,
                            style = TextStyle(
                                brush = if (isSelected) ArmoryColors.Gold else ArmoryColors.Silver,
                                fontSize = 11.sp,
                                fontFamily = PremiumBodyFont,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = Color.Transparent
                    )
                )
            }
        }
    }
}

/**
 * Alias de compatibilidad
 */
@Composable
fun OpenTuneBottomBar(
    selectedTab: Int,
    onTabChange: (Int) -> Unit,
    nowPlaying: MediaMetadata?,
    playerStatus: PlayerStatus,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onPlayerClick: () -> Unit,
    position: Long = 0L,
    duration: Long = 0L,
    showNavigationBar: Boolean = true
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = if (showNavigationBar) 12.dp else 24.dp)
    ) {
        // El MiniPlayer se mantiene aquí temporalmente para no romper la compilación,
        // pero en el siguiente paso lo eliminaremos de aquí para moverlo al BottomSheet.
        AnimatedVisibility(
            visible = nowPlaying != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .padding(bottom = if (showNavigationBar) 10.dp else 0.dp)
                ) {
                    if (nowPlaying != null) {
                        MiniPlayer(
                            nowPlaying = nowPlaying,
                            playerStatus = playerStatus,
                            onToggle = onToggle,
                            onNext = onNext,
                            onPrevious = onPrevious,
                            onClick = onPlayerClick,
                            position = position,
                            duration = duration
                        )
                    }
                }
            }
        }

        if (showNavigationBar) {
            OpenTuneNavigationBar(
                selectedTab = selectedTab,
                onTabChange = onTabChange
            )
        }
    }
}
