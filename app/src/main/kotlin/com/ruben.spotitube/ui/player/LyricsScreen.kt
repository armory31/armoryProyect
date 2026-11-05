/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ruben.spotitube.ui.player

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import com.ruben.spotitube.ArmoryColors
import com.ruben.spotitube.ArmoryGradients
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Player.STATE_BUFFERING
import androidx.media3.common.Player.STATE_READY
import androidx.navigation.NavController
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import com.ruben.spotitube.LocalDatabase
import com.ruben.spotitube.LocalPlayerConnection
import com.ruben.spotitube.R
import com.ruben.spotitube.constants.BlurRadiusKey
import com.ruben.spotitube.constants.DisableBlurKey
import com.ruben.spotitube.constants.PlayerBackgroundStyle
import com.ruben.spotitube.constants.PlayerBackgroundStyleKey
import com.ruben.spotitube.constants.PlayerCustomBlurKey
import com.ruben.spotitube.constants.PlayerCustomBrightnessKey
import com.ruben.spotitube.constants.PlayerCustomContrastKey
import com.ruben.spotitube.constants.PlayerCustomImageUriKey
import com.ruben.spotitube.constants.SliderStyle
import com.ruben.spotitube.constants.SliderStyleKey
import com.ruben.spotitube.constants.UseLyricsV2Key
import com.ruben.spotitube.db.entities.LyricsEntity
import com.ruben.spotitube.di.LyricsHelperEntryPoint
import com.ruben.spotitube.extensions.togglePlayPause
import com.ruben.spotitube.extensions.toggleRepeatMode
import com.ruben.spotitube.models.MediaMetadata
import com.ruben.spotitube.ui.component.BigSeekBar
import com.ruben.spotitube.ui.component.LocalMenuState
import com.ruben.spotitube.ui.component.Lyrics
import com.ruben.spotitube.ui.component.LyricsV2
import com.ruben.spotitube.ui.menu.LyricsMenu
import com.ruben.spotitube.ui.theme.PlayerColorExtractor
import com.ruben.spotitube.utils.makeTimeString
import com.ruben.spotitube.utils.rememberEnumPreference
import com.ruben.spotitube.utils.rememberPreference
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun LyricsScreen(
    mediaMetadata: MediaMetadata,
    navController: NavController,
    onBackClick: () -> Unit,
    lyricsSyncOffset: Int,
    modifier: Modifier = Modifier
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val player = playerConnection.player
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()

    val playbackState by playerConnection.playbackState.collectAsStateWithLifecycle()
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val repeatMode by playerConnection.repeatMode.collectAsStateWithLifecycle()
    val shuffleModeEnabled by playerConnection.shuffleModeEnabled.collectAsStateWithLifecycle()
    val playerVolume = playerConnection.service.playerVolume.collectAsStateWithLifecycle()
    
    // slider style preference
    val sliderStyle by rememberEnumPreference(SliderStyleKey, SliderStyle.Standard)
    val currentLyrics by playerConnection.currentLyrics.collectAsStateWithLifecycle(initialValue = null)
    val (useLyricsV2) = rememberPreference(UseLyricsV2Key, defaultValue = true)

    // Auto-fetch lyrics when no lyrics found (same logic as refetch)
    LaunchedEffect(mediaMetadata.id, currentLyrics) {
        if (currentLyrics == null) {
            // Small delay to ensure database state is stable
            delay(500.milliseconds)
            
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    // Get LyricsHelper from Hilt
                    val entryPoint = EntryPointAccessors.fromApplication(
                        context.applicationContext,
                        LyricsHelperEntryPoint::class.java
                    )
                    val lyricsHelper = entryPoint.lyricsHelper()
                    
                    // Fetch lyrics automatically
                    val lyrics = lyricsHelper.getLyrics(mediaMetadata)
                    
                    // Save to database
                    database.query {
                        upsert(LyricsEntity(mediaMetadata.id, lyrics))
                    }
                } catch (_: Exception) {
                    // Handle error silently - user can manually refetch if needed
                }
            }
        }
    }

    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(C.TIME_UNSET) }
    var sliderPosition by remember { mutableStateOf<Long?>(null) }
    
    // Track loading state: when buffering or when user is seeking
    val isLoading = playbackState == STATE_BUFFERING || sliderPosition != null

    val playerBackground by rememberEnumPreference(PlayerBackgroundStyleKey, PlayerBackgroundStyle.DEFAULT)
    val (disableBlur) = rememberPreference(DisableBlurKey, true)
    val (blurRadius) = rememberPreference(BlurRadiusKey, 36f)

    val (playerCustomImageUri) = rememberPreference(PlayerCustomImageUriKey, "")
    val (playerCustomBlur) = rememberPreference(PlayerCustomBlurKey, 0f)
    val (playerCustomContrast) = rememberPreference(PlayerCustomContrastKey, 1f)
    val (playerCustomBrightness) = rememberPreference(PlayerCustomBrightnessKey, 1f)

    var gradientColors by remember { mutableStateOf<List<Color>>(emptyList()) }
    val gradientColorsCache = remember { mutableMapOf<String, List<Color>>() }

    val defaultGradientColors = listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surfaceVariant)
    val fallbackColor = MaterialTheme.colorScheme.surface.toArgb()

    LaunchedEffect(mediaMetadata.id, playerBackground) {
        if (playerBackground == PlayerBackgroundStyle.GRADIENT || playerBackground == PlayerBackgroundStyle.COLORING || playerBackground == PlayerBackgroundStyle.BLUR_GRADIENT || playerBackground == PlayerBackgroundStyle.GLOW || playerBackground == PlayerBackgroundStyle.GLOW_ANIMATED) {
            if (mediaMetadata.thumbnailUrl != null) {
                val cachedColors = gradientColorsCache[mediaMetadata.id]
                if (cachedColors != null) {
                    gradientColors = cachedColors
                } else {
                    val request = ImageRequest.Builder(context)
                        .data(mediaMetadata.thumbnailUrl)
                        .size(Size(PlayerColorExtractor.Config.IMAGE_SIZE, PlayerColorExtractor.Config.IMAGE_SIZE))
                        .allowHardware(false)
                        .memoryCacheKey("gradient_${mediaMetadata.id}")
                        .build()

                    val execResult = runCatching {
                        withContext(Dispatchers.IO) {
                            context.imageLoader.execute(request)
                        }
                    }.getOrNull()

                    val result = execResult?.image

                    if (result != null) {
                        val bitmap = result.toBitmap()
                        val palette = withContext(Dispatchers.Default) {
                            Palette.from(bitmap)
                                .maximumColorCount(PlayerColorExtractor.Config.MAX_COLOR_COUNT)
                                .resizeBitmapArea(PlayerColorExtractor.Config.BITMAP_AREA)
                                .generate()
                        }

                        val extractedColors = PlayerColorExtractor.extractGradientColors(
                            palette = palette,
                            fallbackColor = fallbackColor
                        )

                        gradientColorsCache[mediaMetadata.id] = extractedColors
                        gradientColors = extractedColors
                    } else {
                        gradientColors = defaultGradientColors
                    }
                }
            } else {
                gradientColors = emptyList()
            }
        } else {
            gradientColors = emptyList()
        }
    }

    val textBackgroundColor = when (playerBackground) {
        PlayerBackgroundStyle.DEFAULT -> MaterialTheme.colorScheme.onBackground
        PlayerBackgroundStyle.BLUR -> Color.White
        PlayerBackgroundStyle.GRADIENT -> Color.White
        PlayerBackgroundStyle.COLORING -> Color.White
        PlayerBackgroundStyle.BLUR_GRADIENT -> Color.White
        PlayerBackgroundStyle.GLOW -> Color.White
        PlayerBackgroundStyle.GLOW_ANIMATED -> Color.White
        PlayerBackgroundStyle.CUSTOM -> Color.White
    }

    val icBackgroundColor = when (playerBackground) {
        PlayerBackgroundStyle.DEFAULT -> MaterialTheme.colorScheme.surface
        PlayerBackgroundStyle.BLUR -> Color.Black
        PlayerBackgroundStyle.GRADIENT -> Color.Black
        PlayerBackgroundStyle.COLORING -> Color.Black
        PlayerBackgroundStyle.BLUR_GRADIENT -> Color.Black
        PlayerBackgroundStyle.GLOW -> Color.Black
        PlayerBackgroundStyle.GLOW_ANIMATED -> Color.Black
        PlayerBackgroundStyle.CUSTOM -> Color.Black
    }

    LaunchedEffect(playbackState) {
        if (playbackState == STATE_READY) {
            while (isActive) {
                delay(100.milliseconds)
                position = player.currentPosition
                duration = player.duration
            }
        }
    }

    BackHandler(onBack = onBackClick)

    Box(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            PlayerBackground(
                playerBackground = playerBackground,
                mediaMetadata = mediaMetadata,
                gradientColors = gradientColors,
                disableBlur = disableBlur,
                blurRadius = blurRadius,
                playerCustomImageUri = playerCustomImageUri,
                playerCustomBlur = playerCustomBlur,
                playerCustomContrast = playerCustomContrast,
                playerCustomBrightness = playerCustomBrightness
            )
        }

        // Check orientation and layout accordingly
        when (LocalConfiguration.current.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> {
                // Landscape layout - split screen horizontally
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.systemBars)
                ) {
                    // Unified header across full width
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                            .zIndex(1f),  // Ensure header is above content
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Down arrow button (left)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(
                                        bounded = true,
                                        radius = 16.dp
                                    )
                                ) { onBackClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.expand_more),
                                contentDescription = stringResource(R.string.close),
                                tint = ArmoryColors.Silver2,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        
                        // Now Playing info in center
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = stringResource(R.string.now_playing),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = mediaMetadata.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        
                        // More button (right)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(
                                        bounded = true,
                                        radius = 16.dp
                                    )
                                ) {
                                    menuState.show {
                                        LyricsMenu(
                                            lyricsProvider = { currentLyrics },
                                            mediaMetadataProvider = { mediaMetadata },
                                            onDismiss = menuState::dismiss
                                        )
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.more_horiz),
                                contentDescription = stringResource(R.string.more_options),
                                tint = ArmoryColors.Silver2,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    
                    // Main content row
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                    ) {
                        // Right side - Lyrics only
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                        ) {
                            // Lyrics content - centered in landscape
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.Center  // Center lyrics in landscape
                            ) {
                                if (useLyricsV2) {
                                    LyricsV2(
                                        sliderPositionProvider = { sliderPosition }
                                    )
                                } else {
                                    Lyrics(
                                        sliderPositionProvider = { sliderPosition },
                                        lyricsSyncOffset = lyricsSyncOffset,
                                        modifier = modifier,
                                    )
                                }
                            }
                        }
                        
                        // Left side - Controls only (from slider to volume)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .padding(horizontal = 48.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Slider
                            StyledPlaybackSlider(
                                sliderStyle = sliderStyle,
                                value = (sliderPosition ?: position).toFloat(),
                                valueRange = 0f..(if (duration == C.TIME_UNSET) 0f else duration.toFloat()),
                                onValueChange = {
                                    sliderPosition = it.toLong()
                                },
                                onValueChangeFinished = {
                                    sliderPosition?.let {
                                        player.seekTo(it)
                                        position = it
                                    }
                                    sliderPosition = null
                                },
                                activeColor = ArmoryColors.Gold1,
                                isPlaying = isPlaying,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Time display below slider
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = makeTimeString(sliderPosition ?: position),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ArmoryColors.Gold1.copy(alpha = 0.8f)
                                )
                                Text(
                                    text = if (duration != C.TIME_UNSET) makeTimeString(duration) else "",
                                    style = MaterialTheme.typography.bodySmall,
                                     color = ArmoryColors.Gold1.copy(alpha = 0.8f)
                                )
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            // Control buttons (Metallic Squircles: Copper - Silver - Gold - Silver - Copper)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Shuffle (Copper)
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            brush = ArmoryGradients.MetallicCopper,
                                            alpha = if (shuffleModeEnabled) 1f else 0.75f
                                        )
                                        .clickable {
                                            playerConnection.player.shuffleModeEnabled = !shuffleModeEnabled
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.shuffle),
                                        contentDescription = "Shuffle",
                                        tint = Color.Black,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // Previous (Silver)
                                Box(
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(brush = ArmoryGradients.MetallicSilver)
                                        .clickable { player.seekToPrevious() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.skip_previous),
                                        contentDescription = "Previous",
                                        tint = Color.Black,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                // Play/Pause (Gold)
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(brush = ArmoryGradients.MetallicGold)
                                        .clickable { player.togglePlayPause() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(32.dp),
                                            color = Color.Black,
                                            strokeWidth = 3.dp
                                        )
                                    } else {
                                        Icon(
                                            painter = painterResource(
                                                if (isPlaying) R.drawable.pause else R.drawable.play
                                            ),
                                            contentDescription = if (isPlaying) "Pause" else stringResource(R.string.play),
                                            tint = Color.Black,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                }

                                // Next (Silver)
                                Box(
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(brush = ArmoryGradients.MetallicSilver)
                                        .clickable { player.seekToNext() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.skip_next),
                                        contentDescription = "Next",
                                        tint = Color.Black,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                // Repeat (Copper)
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            brush = ArmoryGradients.MetallicCopper,
                                            alpha = if (repeatMode != Player.REPEAT_MODE_OFF) 1f else 0.75f
                                        )
                                        .clickable { playerConnection.player.toggleRepeatMode() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(
                                            when (repeatMode) {
                                                Player.REPEAT_MODE_ONE -> R.drawable.repeat_one
                                                else -> R.drawable.repeat
                                            }
                                        ),
                                        contentDescription = "Repeat",
                                        tint = Color.Black,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            // Volume Control
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 48.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.volume_off),
                                    contentDescription = stringResource(R.string.minimum_volume),
                                    modifier = Modifier.size(20.dp),
                                    tint = ArmoryColors.Gold1
                                )

                                BigSeekBar(
                                    progressProvider = playerVolume::value,
                                    onProgressChange = { playerConnection.service.playerVolume.value = it },
                                    color = ArmoryColors.Gold1,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(24.dp)
                                        .padding(horizontal = 16.dp)
                                )

                                Icon(
                                    painter = painterResource(R.drawable.volume_up),
                                    contentDescription = stringResource(R.string.maximum_volume),
                                    modifier = Modifier.size(20.dp),
                                    tint = ArmoryColors.Gold1
                                )
                            }
                        }
                    }
                }
            }
            else -> {
                // Portrait layout - original layout
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(WindowInsets.systemBars.asPaddingValues())
                ) {
                    // Header with More button and Down arrow on opposite sides
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Down arrow button (left)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(
                                        bounded = true,
                                        radius = 16.dp
                                    )
                                ) { onBackClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.expand_more),
                                contentDescription = stringResource(R.string.close),
                                tint = ArmoryColors.Silver2,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        
                        // Centered content
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = stringResource(R.string.now_playing),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = mediaMetadata.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        
                        // More button (right)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(
                                        bounded = true,
                                        radius = 16.dp
                                    )
                                ) {
                                    menuState.show {
                                        LyricsMenu(
                                            lyricsProvider = { currentLyrics },
                                            mediaMetadataProvider = { mediaMetadata },
                                            onDismiss = menuState::dismiss
                                        )
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.more_horiz),
                                contentDescription = stringResource(R.string.more_options),
                                tint = ArmoryColors.Silver2,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        if (useLyricsV2) {
                            LyricsV2(
                                sliderPositionProvider = { sliderPosition }
                            )
                        } else {
                            Lyrics(
                                sliderPositionProvider = { sliderPosition },
                                lyricsSyncOffset = lyricsSyncOffset,
                                modifier = modifier,
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 16.dp)
                    ) {
                        StyledPlaybackSlider(
                            sliderStyle = sliderStyle,
                            value = (sliderPosition ?: position).toFloat(),
                            valueRange = 0f..(if (duration == C.TIME_UNSET) 0f else duration.toFloat()),
                            onValueChange = {
                                sliderPosition = it.toLong()
                            },
                            onValueChangeFinished = {
                                sliderPosition?.let {
                                    player.seekTo(it)
                                    position = it
                                }
                                sliderPosition = null
                            },
                            activeColor = ArmoryColors.Gold1,
                            isPlaying = isPlaying,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Time display below slider
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = makeTimeString(sliderPosition ?: position),
                                style = MaterialTheme.typography.bodySmall,
                                color = ArmoryColors.Gold1.copy(alpha = 0.8f)
                            )
                            Text(
                                text = if (duration != C.TIME_UNSET) makeTimeString(duration) else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = ArmoryColors.Gold1.copy(alpha = 0.8f)
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Control buttons (Metallic Squircles: Copper - Silver - Gold - Silver - Copper)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Shuffle (Copper)
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        brush = ArmoryGradients.MetallicCopper,
                                        alpha = if (shuffleModeEnabled) 1f else 0.75f
                                    )
                                    .clickable {
                                        playerConnection.player.shuffleModeEnabled = !shuffleModeEnabled
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.shuffle),
                                    contentDescription = "Shuffle",
                                    tint = Color.Black,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Previous (Silver)
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(brush = ArmoryGradients.MetallicSilver)
                                    .clickable { player.seekToPrevious() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.skip_previous),
                                    contentDescription = "Previous",
                                    tint = Color.Black,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Play/Pause (Gold)
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(brush = ArmoryGradients.MetallicGold)
                                    .clickable { player.togglePlayPause() },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(32.dp),
                                        color = Color.Black,
                                        strokeWidth = 3.dp
                                    )
                                } else {
                                    Icon(
                                        painter = painterResource(
                                            if (isPlaying) R.drawable.pause else R.drawable.play
                                        ),
                                        contentDescription = if (isPlaying) "Pause" else stringResource(R.string.play),
                                        tint = Color.Black,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }

                            // Next (Silver)
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(brush = ArmoryGradients.MetallicSilver)
                                    .clickable { player.seekToNext() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.skip_next),
                                    contentDescription = "Next",
                                    tint = Color.Black,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Repeat (Copper)
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        brush = ArmoryGradients.MetallicCopper,
                                        alpha = if (repeatMode != Player.REPEAT_MODE_OFF) 1f else 0.75f
                                    )
                                    .clickable { playerConnection.player.toggleRepeatMode() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(
                                        when (repeatMode) {
                                            Player.REPEAT_MODE_ONE -> R.drawable.repeat_one
                                            else -> R.drawable.repeat
                                        }
                                    ),
                                    contentDescription = "Repeat",
                                    tint = Color.Black,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Volume Control
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.volume_off),
                                contentDescription = stringResource(R.string.minimum_volume),
                                modifier = Modifier.size(20.dp),
                                tint = ArmoryColors.Gold1
                            )

                            BigSeekBar(
                                progressProvider = playerVolume::value,
                                onProgressChange = { playerConnection.service.playerVolume.value = it },
                                color = ArmoryColors.Gold1,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(24.dp)
                                    .padding(horizontal = 16.dp)
                            )

                            Icon(
                                painter = painterResource(R.drawable.volume_up),
                                contentDescription = stringResource(R.string.maximum_volume),
                                modifier = Modifier.size(20.dp),
                                tint = ArmoryColors.Gold1
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}
