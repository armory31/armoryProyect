package com.ruben.spotitube

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.ruben.spotitube.constants.AccountAvatarUriKey
import com.ruben.spotitube.utils.SessionManager
import com.ruben.spotitube.utils.rememberPreference
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.ruben.spotitube.models.MediaMetadata

enum class PlayerStatus { PLAYING, PAUSED, LOADING }

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

@Composable
fun rememberAvatarUrl(): String? {
    val context = LocalContext.current
    val session = remember(context) { SessionManager(context) }
    val (accountAvatarUriPref) = rememberPreference(AccountAvatarUriKey, "")
    val currentUser = remember { FirebaseAuth.getInstance().currentUser }

    val rawUrl = accountAvatarUriPref.ifEmpty {
        session.getAvatarUri().ifEmpty {
            currentUser?.photoUrl?.toString().orEmpty()
        }
    }
    return rawUrl.takeIf { it.isNotBlank() }
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

        // 3/5 (60%) - Palabra ARMORY con estilo central
        Box(
            modifier = Modifier.weight(3f),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "ARMORY",
                style = MaterialTheme.typography.displayLarge.copy(letterSpacing = 4.sp),
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
                    modifier = Modifier.size(40.dp).border(1.dp, ArmoryColors.SurfaceBorder, CircleShape)
                ) {
                    Log.d("AvatarDbg", "avatarUrl=[$avatarUrl] nullOrBlank=${avatarUrl.isNullOrBlank()}") // TEMP-DEBUG
                    if (avatarUrl.isNullOrBlank()) {
                        ArmoryIcon(Icons.Filled.Person, "Cuenta")
                    } else {
                        SubcomposeAsyncImage(
                            model = avatarUrl,
                            contentDescription = "Cuenta",
                            modifier = Modifier.size(28.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop,
                            onError = { Log.d("AvatarDbg", "ERROR: ${it.result.throwable}") }, // TEMP-DEBUG
                            onSuccess = { Log.d("AvatarDbg", "OK") }, // TEMP-DEBUG
                            error = { ArmoryIcon(Icons.Filled.Person, "Cuenta") },
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
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .then(
                if (selected) Modifier.background(ArmoryGradients.MetallicGold)
                else Modifier.background(ArmoryColors.SurfaceBlack).border(1.dp, ArmoryColors.SurfaceBorder, shape)
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(
            LocalContentColor provides if (selected) ArmoryColors.AbsoluteBlack else ArmoryColors.TextSecondary
        ) { label() }
    }
}

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
    val progress = if (duration > 0) position.toFloat() / duration else 0f
    
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isPressed) 0.97f else 1f, label = "scale")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clip(RoundedCornerShape(16.dp))
            .background(ArmoryColors.SurfaceBlack.copy(alpha = 0.90f))
            .border(
                width = 1.dp,
                color = ArmoryColors.SurfaceBorder,
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
                Text(nowPlaying.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(nowPlaying.artists.joinToString { it.name }, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                ArmoryIconButton(painterResource(R.drawable.skip_previous), null, onPrevious)

                if (playerStatus == PlayerStatus.LOADING) {
                    CircularProgressIndicator(
                        color = ArmoryColors.Gold2,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(ArmoryGradients.MetallicGold)
                            .clickable(onClick = onToggle),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(
                                if (playerStatus == PlayerStatus.PLAYING) R.drawable.pause else R.drawable.play
                            ),
                            contentDescription = null,
                            tint = ArmoryColors.AbsoluteBlack,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                ArmoryIconButton(painterResource(R.drawable.skip_next), null, onNext)
            }
        }

        // Barra de progreso minimalista en la base
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(2.5.dp)
                .background(ArmoryColors.Gold2)
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

        if (showNavigationBar) ArmoryNavigationBar(selectedTab, onTabChange, Modifier.height(76.dp))
    }
}

@Composable
fun ArmoryNavigationBar(
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
            .background(ArmoryColors.SurfaceBlack)
            .border(
                width = 1.dp,
                color = ArmoryColors.Gold1,
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
                    icon = { ArmoryIcon(icon, null, active = isSelected) },
                    label = {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodySmall.let {
                                if (isSelected) it.copy(color = ArmoryColors.TextGold, fontWeight = FontWeight.Bold) else it
                            }
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
