/*
 * OpenTune Project - Music Player (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ruben.spotitube.ui.screens.artist

import com.ruben.spotitube.ArmoryColors
import androidx.compose.foundation.BorderStroke

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import com.ruben.spotitube.GoldDivider
import com.ruben.spotitube.LocalDatabase
import com.ruben.spotitube.LocalPlayerAwareWindowInsets
import com.ruben.spotitube.LocalPlayerConnection

import com.ruben.spotitube.GlobalFont
import com.ruben.spotitube.R
import com.ruben.spotitube.constants.DisableBlurKey
import com.ruben.spotitube.constants.HideExplicitKey
import com.ruben.spotitube.extensions.toMediaItem
import com.ruben.spotitube.extensions.togglePlayPause
import com.ruben.spotitube.innertube.models.AlbumItem
import com.ruben.spotitube.innertube.models.ArtistItem
import com.ruben.spotitube.innertube.models.PlaylistItem
import com.ruben.spotitube.innertube.models.SongItem
import com.ruben.spotitube.innertube.models.WatchEndpoint
import com.ruben.spotitube.models.toMediaMetadata
import com.ruben.spotitube.playback.queues.ListQueue
import com.ruben.spotitube.playback.queues.YouTubeQueue
import com.ruben.spotitube.ui.component.AlbumGridItem
import com.ruben.spotitube.ui.component.IconButton
import com.ruben.spotitube.ui.component.LocalMenuState
import com.ruben.spotitube.ArmoryTopBar
import com.ruben.spotitube.ArmoryIconButton
import com.ruben.spotitube.ArmoryIcon
import com.ruben.spotitube.SectionHeader
import com.ruben.spotitube.ArmoryButton
import com.ruben.spotitube.ui.component.SongListItem
import com.ruben.spotitube.ui.component.YouTubeGridItem
import com.ruben.spotitube.ui.component.YouTubeListItem
import com.ruben.spotitube.ui.component.shimmer.ButtonPlaceholder
import com.ruben.spotitube.ui.component.shimmer.ListItemPlaceHolder
import com.ruben.spotitube.ui.component.shimmer.ShimmerHost
import com.ruben.spotitube.ui.component.shimmer.TextPlaceholder
import com.ruben.spotitube.ui.menu.AlbumMenu
import com.ruben.spotitube.ui.menu.SongMenu
import com.ruben.spotitube.ui.menu.YouTubeAlbumMenu
import com.ruben.spotitube.ui.menu.YouTubeArtistMenu
import com.ruben.spotitube.ui.menu.YouTubePlaylistMenu
import com.ruben.spotitube.ui.menu.YouTubeSongMenu
import com.ruben.spotitube.ui.theme.PlayerColorExtractor
import com.ruben.spotitube.ui.utils.resize
import com.ruben.spotitube.utils.rememberPreference
import com.ruben.spotitube.viewmodels.ArtistViewModel
import com.valentinilk.shimmer.shimmer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ArtistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: ArtistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val artistPage = viewModel.artistPage
    val libraryArtist by viewModel.libraryArtist.collectAsStateWithLifecycle()
    val isSubscribed by viewModel.isBookmarked.collectAsStateWithLifecycle()
    val librarySongs by viewModel.librarySongs.collectAsStateWithLifecycle()
    val libraryAlbums by viewModel.libraryAlbums.collectAsStateWithLifecycle()
    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)
    val (disableBlur) = rememberPreference(DisableBlurKey, false)

    val lazyListState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showLocal by rememberSaveable { mutableStateOf(false) }
    val density = LocalDensity.current

    // System bars padding
    val systemBarsTopPadding = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()

    // Gradient colors for mesh background
    var gradientColors by remember { mutableStateOf<List<Color>>(emptyList()) }
    val fallbackColor = ArmoryColors.AbsoluteBlack.toArgb()
    val surfaceColor = ArmoryColors.AbsoluteBlack

    // Get thumbnail URL
    val thumbnail = artistPage?.artist?.thumbnail ?: libraryArtist?.artist?.thumbnailUrl

    // Extract gradient colors from artist image
    LaunchedEffect(thumbnail) {
        if (thumbnail != null) {
            val request = ImageRequest.Builder(context)
                .data(thumbnail)
                .size(Size(PlayerColorExtractor.Config.IMAGE_SIZE, PlayerColorExtractor.Config.IMAGE_SIZE))
                .allowHardware(false)
                .build()

            val result = runCatching {
                context.imageLoader.execute(request)
            }.getOrNull()

            if (result != null) {
                val bitmap = result.image?.toBitmap()
                if (bitmap != null) {
                    val palette = withContext(Dispatchers.Default) {
                        Palette.from(bitmap)
                            .maximumColorCount(PlayerColorExtractor.Config.MAX_COLOR_COUNT)
                            .resizeBitmapArea(PlayerColorExtractor.Config.BITMAP_AREA)
                            .generate()
                    }

                    val extractedColors = PlayerColorExtractor.extractGradientColors(
                        palette = palette,
                        fallbackColor = fallbackColor
                    ).toMutableList()
                    
                    // Inyectar el dorado Armory para consistencia de marca
                    if (extractedColors.size >= 3) {
                        extractedColors.add(0, ArmoryColors.Gold3)
                    }
                    
                    gradientColors = extractedColors
                }
            }
        } else {
            gradientColors = emptyList()
        }
    }

    // Calculate gradient opacity based on scroll position
    val gradientAlpha by remember {
        derivedStateOf {
            if (lazyListState.firstVisibleItemIndex == 0) {
                val offset = lazyListState.firstVisibleItemScrollOffset
                (1f - (offset / 800f)).coerceIn(0f, 1f)
            } else {
                0f
            }
        }
    }

    val transparentAppBar by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset < 100
        }
    }

    val appBarContainerColor by animateColorAsState(
        targetValue = if (transparentAppBar) Color.Transparent else ArmoryColors.AbsoluteBlack.copy(alpha = 0.95f),
        animationSpec = tween(500),
        label = "appBarContainerColor"
    )

    val appBarContentColor by animateColorAsState(
        targetValue = if (transparentAppBar) Color.White else ArmoryColors.Gold3,
        animationSpec = tween(500),
        label = "appBarContentColor"
    )

    LaunchedEffect(libraryArtist) {
        showLocal = false
    }

    Scaffold(
        topBar = {
            ArmoryTopBar(
                title = "",
                onBack = navController::navigateUp,
                overImage = true,
                scrollBehavior = scrollBehavior,
                actions = {
                    ArmoryIconButton(
                        painter = painterResource(R.drawable.link),
                        contentDescription = "Copiar enlace",
                        onClick = {
                            viewModel.artistPage?.artist?.shareLink?.let { link ->
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Artist Link", link)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, R.string.link_copied, Toast.LENGTH_SHORT).show()
                            }
                        },
                        overImage = true
                    )
                    ArmoryIconButton(
                        painter = painterResource(R.drawable.share),
                        contentDescription = "Compartir",
                        onClick = {
                            val shareIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    viewModel.artistPage?.artist?.shareLink
                                        ?: "https://music.youtube.com/channel/${viewModel.artistId}"
                                )
                            }
                            context.startActivity(Intent.createChooser(shareIntent, null))
                        },
                        overImage = true
                    )
                }
            )
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            )
        },
        containerColor = surfaceColor,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Mesh gradient background layer
            if (!disableBlur && gradientColors.isNotEmpty() && gradientAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxSize(0.65f)
                        .align(Alignment.TopCenter)
                        .zIndex(-1f)
                        .drawBehind {
                            val width = size.width
                            val height = size.height

                            if (gradientColors.size >= 3) {
                                val c0 = gradientColors[0]
                                val c1 = gradientColors[1]
                                val c2 = gradientColors[2]
                                val c3 = gradientColors.getOrElse(3) { c0 }
                                val c4 = gradientColors.getOrElse(4) { c1 }
                                // Primary color blob - top center
                                drawRect(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            c0.copy(alpha = gradientAlpha * 0.72f),
                                            c0.copy(alpha = gradientAlpha * 0.4f),
                                            Color.Transparent
                                        ),
                                        center = Offset(width * 0.5f, height * 0.2f),
                                        radius = width * 0.7f
                                    )
                                )

                                // Secondary color blob - top left
                                drawRect(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            c1.copy(alpha = gradientAlpha * 0.56f),
                                            c1.copy(alpha = gradientAlpha * 0.3f),
                                            Color.Transparent
                                        ),
                                        center = Offset(width * 0.15f, height * 0.35f),
                                        radius = width * 0.6f
                                    )
                                )

                                // Third color blob - right side
                                drawRect(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            c2.copy(alpha = gradientAlpha * 0.52f),
                                            c2.copy(alpha = gradientAlpha * 0.26f),
                                            Color.Transparent
                                        ),
                                        center = Offset(width * 0.85f, height * 0.45f),
                                        radius = width * 0.65f
                                    )
                                )

                                drawRect(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            c3.copy(alpha = gradientAlpha * 0.34f),
                                            c3.copy(alpha = gradientAlpha * 0.18f),
                                            Color.Transparent
                                        ),
                                        center = Offset(width * 0.35f, height * 0.6f),
                                        radius = width * 0.8f
                                    )
                                )

                                drawRect(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            c4.copy(alpha = gradientAlpha * 0.28f),
                                            c4.copy(alpha = gradientAlpha * 0.14f),
                                            Color.Transparent
                                        ),
                                        center = Offset(width * 0.55f, height * 0.85f),
                                        radius = width * 0.95f
                                    )
                                )
                            } else if (gradientColors.isNotEmpty()) {
                                drawRect(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            gradientColors[0].copy(alpha = gradientAlpha * 0.6f),
                                            gradientColors[0].copy(alpha = gradientAlpha * 0.3f),
                                            Color.Transparent
                                        ),
                                        center = Offset(width * 0.5f, height * 0.3f),
                                        radius = width * 0.8f
                                    )
                                )
                            }

                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Transparent,
                                        surfaceColor.copy(alpha = gradientAlpha * 0.22f),
                                        surfaceColor.copy(alpha = gradientAlpha * 0.55f),
                                        surfaceColor
                                    ),
                                    startY = height * 0.4f,
                                    endY = height
                                )
                            )
                        }
                )
            }

            LazyColumn(
                state = lazyListState,
                contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
                modifier = Modifier
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
            ) {
                if (artistPage == null && !showLocal) {
                    // Shimmer loading state
                    item(key = "shimmer") {
                        ShimmerHost {
                            // Hero section placeholder
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp)
                            ) {
                                // Artist image placeholder - circular
                                Box(
                                    modifier = Modifier
                                        .padding(top = 8.dp)
                                        .size(210.dp)
                                        .align(Alignment.CenterHorizontally)
                                        .shimmer()
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onSurface)
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                // Artist name placeholder
                                TextPlaceholder(
                                    height = 32.dp,
                                    modifier = Modifier
                                        .fillMaxWidth(0.5f)
                                        .align(Alignment.CenterHorizontally)
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // Stats placeholder
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 48.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    repeat(3) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            TextPlaceholder(
                                                height = 20.dp,
                                                modifier = Modifier.width(40.dp)
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            TextPlaceholder(
                                                height = 14.dp,
                                                modifier = Modifier.width(50.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                // Buttons placeholder
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                                ) {
                                    ButtonPlaceholder(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                    )
                                    ButtonPlaceholder(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(32.dp))
                            }

                            // Songs list placeholder
                            repeat(5) {
                                ListItemPlaceHolder()
                            }
                        }
                    }
                } else {
                    // Hero Header
                    item(key = "header") {
                        val artistName = artistPage?.artist?.title ?: libraryArtist?.artist?.name

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Artist Image - Circular with shadow
                            Box(
                                modifier = Modifier
                                    .padding(top = 8.dp, bottom = 16.dp)
                            ) {
                                if (thumbnail != null) {
                                    AsyncImage(
                                        model = thumbnail.resize(600, 600),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(210.dp)
                                            .clip(CircleShape)
                                    )
                                } else {
                                    // Placeholder when no image
                                    Box(
                                        modifier = Modifier
                                            .size(200.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        ArmoryIcon(
                                            painter = painterResource(R.drawable.person),
                                            contentDescription = null,
                                            modifier = Modifier.size(80.dp),
                                            active = true
                                        )
                                    }
                                }
                            }

                            // Artist Name
                            Text(
                                text = artistName ?: stringResource(R.string.unknown_artist),
                                style = MaterialTheme.typography.headlineMedium,
                                fontFamily = GlobalFont,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )

                            // Artist Description (expandable)
                            val description = artistPage?.description
                            if (!description.isNullOrBlank()) {
                                var isExpanded by rememberSaveable { mutableStateOf(false) }
                                val maxLines = if (isExpanded) Int.MAX_VALUE else 2
                                
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp, vertical = 8.dp)
                                        .combinedClickable(
                                            onClick = { isExpanded = !isExpanded },
                                            onLongClick = {}
                                        ),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = if (!isExpanded && description.length > 100) {
                                            description.take(100).trimEnd() + "…"
                                        } else {
                                            description
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = TextAlign.Center,
                                        maxLines = maxLines,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    
                                    if (!isExpanded && description.length > 100) {
                                        Text(
                                            text = stringResource(R.string.more),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }

                            // Stats Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp, horizontal = 32.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                // Songs count - sum all SongItem instances across all sections
                                val songSections = artistPage?.sections?.filter { section ->
                                    section.items.any { it is SongItem }
                                }
                                val songCount = if (showLocal) {
                                    librarySongs.size
                                } else {
                                    songSections
                                        ?.flatMap { it.items }
                                        ?.filterIsInstance<SongItem>()
                                        ?.distinctBy { it.id }
                                        ?.size ?: librarySongs.size
                                }
                                // Check if any song section has moreEndpoint (meaning there are more songs)
                                val hasMoreSongs = !showLocal && songSections?.any { it.moreEndpoint != null } == true

                                if (songCount > 0) {
                                    StatItem(
                                        value = if (hasMoreSongs) "$songCount+" else songCount.toString(),
                                        label = stringResource(R.string.songs),
                                        valueColor = ArmoryColors.Gold2,
                                        labelColor = ArmoryColors.Silver3
                                    )
                                }

                                // Albums count - sum all AlbumItem instances across all sections
                                val albumSections = artistPage?.sections?.filter { section ->
                                    section.items.any { it is AlbumItem }
                                }
                                val albumCount = if (showLocal) {
                                    libraryAlbums.size
                                } else {
                                    albumSections
                                        ?.flatMap { it.items }
                                        ?.filterIsInstance<AlbumItem>()
                                        ?.distinctBy { it.id }
                                        ?.size ?: libraryAlbums.size
                                }
                                // Check if any album section has moreEndpoint (meaning there are more albums)
                                val hasMoreAlbums = !showLocal && albumSections?.any { it.moreEndpoint != null } == true

                                if (albumCount > 0) {
                                    StatItem(
                                        value = if (hasMoreAlbums) "$albumCount+" else albumCount.toString(),
                                        label = stringResource(R.string.albums),
                                        valueColor = ArmoryColors.Gold2,
                                        labelColor = ArmoryColors.Silver3
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 6.dp)
                            ) {
                                if (isSubscribed) {
                                    ArmoryButton(
                                        text = stringResource(R.string.subscribed),
                                        onClick = { viewModel.toggleLike() },
                                        painter = painterResource(R.drawable.done),
                                        modifier = Modifier.fillMaxWidth().height(48.dp)
                                    )
                                } else {
                                    Button(
                                        onClick = { viewModel.toggleLike() },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = ArmoryColors.SurfaceElevated,
                                            contentColor = ArmoryColors.TextPrimary
                                        ),
                                        shape = RoundedCornerShape(24.dp),
                                        border = BorderStroke(1.dp, ArmoryColors.SurfaceBorder),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                    ) {
                                        ArmoryIcon(
                                            painter = painterResource(R.drawable.add),
                                            contentDescription = null,
                                            active = true
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = stringResource(R.string.subscribe),
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                }
                            }

                            // 2. Aleatorio & Radio Buttons (2 columns underneath, SurfaceElevated with SurfaceBorder)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Shuffle Button
                                Button(
                                    onClick = {
                                        if (!showLocal) {
                                            artistPage?.artist?.shuffleEndpoint?.let { shuffleEndpoint ->
                                                playerConnection.playQueue(YouTubeQueue(shuffleEndpoint))
                                            }
                                        } else if (librarySongs.isNotEmpty()) {
                                            val shuffledSongs = librarySongs.shuffled()
                                            playerConnection.playQueue(
                                                ListQueue(
                                                    title = libraryArtist?.artist?.name ?: "Unknown Artist",
                                                    items = shuffledSongs.map { it.toMediaItem() }
                                                )
                                            )
                                        }
                                    },
                                    enabled = if (showLocal) librarySongs.isNotEmpty() else artistPage?.artist?.shuffleEndpoint != null,
                                    shape = RoundedCornerShape(24.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ArmoryColors.SurfaceElevated,
                                        contentColor = ArmoryColors.TextPrimary
                                    ),
                                    border = BorderStroke(1.dp, ArmoryColors.SurfaceBorder),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                ) {
                                    ArmoryIcon(
                                        painter = painterResource(R.drawable.shuffle),
                                        contentDescription = null,
                                        active = true
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.shuffle),
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1
                                    )
                                }

                                // Radio Button
                                val radioEndpoint = if (!showLocal) artistPage?.artist?.radioEndpoint else null
                                Button(
                                    onClick = {
                                        radioEndpoint?.let {
                                            playerConnection.playQueue(YouTubeQueue(it))
                                        }
                                    },
                                    enabled = radioEndpoint != null,
                                    shape = RoundedCornerShape(24.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ArmoryColors.SurfaceElevated,
                                        contentColor = ArmoryColors.TextPrimary
                                    ),
                                    border = BorderStroke(1.dp, ArmoryColors.SurfaceBorder),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                ) {
                                    ArmoryIcon(
                                        painter = painterResource(R.drawable.radio),
                                        contentDescription = null,
                                        active = true
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.radio),
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    // Content sections
                    if (showLocal) {
                        // Local Songs Section
                        if (librarySongs.isNotEmpty()) {
                            item {
                                GoldDivider(modifier = Modifier.padding(bottom = 8.dp))
                            }
                            item {
                                SectionHeader(
                                    text = stringResource(R.string.songs),
                                    onClick = {
                                        val route = "artist/${viewModel.artistId}/songs"
                                        if (navController.currentDestination?.route != route) {
                                            navController.navigate(route)
                                        }
                                    }
                                )
                            }

                            val filteredLibrarySongs = if (hideExplicit) {
                                librarySongs.filter { !it.song.explicit }
                            } else {
                                librarySongs
                            }

                            itemsIndexed(
                                items = filteredLibrarySongs.take(5),
                                key = { index, item -> "local_song_${item.id}_$index" }
                            ) { index, song ->
                                SongListItem(
                                    song = song,
                                    showInLibraryIcon = true,
                                    isActive = song.id == mediaMetadata?.id,
                                    isPlaying = isPlaying,
                                    trailingContent = {
                                        IconButton(
                                            onClick = {
                                                menuState.show {
                                                    SongMenu(
                                                        originalSong = song,
                                                        navController = navController,
                                                        onDismiss = menuState::dismiss,
                                                    )
                                                }
                                            },
                                            onLongClick = {},
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.more_vert),
                                                contentDescription = null,
                                            )
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = {
                                                if (song.id == mediaMetadata?.id) {
                                                    playerConnection.player.togglePlayPause()
                                                } else {
                                                    playerConnection.playQueue(
                                                        ListQueue(
                                                            title = libraryArtist?.artist?.name ?: "Unknown Artist",
                                                            items = librarySongs.map { it.toMediaItem() },
                                                            startIndex = index
                                                        )
                                                    )
                                                }
                                            },
                                            onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                menuState.show {
                                                    SongMenu(
                                                        originalSong = song,
                                                        navController = navController,
                                                        onDismiss = menuState::dismiss,
                                                    )
                                                }
                                            },
                                        )
                                        .animateItem(),
                                )
                            }

                            // Show "View All" if more songs available
                            if (filteredLibrarySongs.size > 5) {
                                item {
                                    Surface(
                                        onClick = {
                                            navController.navigate("artist/${viewModel.artistId}/songs")
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.view_all),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 12.dp),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }

                        // Local Albums Section
                        if (libraryAlbums.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    text = stringResource(R.string.albums),
                                    onClick = {
                                        val route = "artist/${viewModel.artistId}/albums"
                                        if (navController.currentDestination?.route != route) {
                                            navController.navigate(route)
                                        }
                                    }
                                )
                            }

                            item {
                                val filteredLibraryAlbums = if (hideExplicit) {
                                    libraryAlbums.filter { !it.album.explicit }
                                } else {
                                    libraryAlbums
                                }

                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(
                                        items = filteredLibraryAlbums,
                                        key = { album -> "local_album_${album.id}_${filteredLibraryAlbums.indexOf(album)}" }
                                    ) { album ->
                                        AlbumGridItem(
                                            album = album,
                                            isActive = mediaMetadata?.album?.id == album.id,
                                            isPlaying = isPlaying,
                                            coroutineScope = coroutineScope,
                                            modifier = Modifier
                                                .combinedClickable(
                                                    onClick = {
                                                        navController.navigate("album/${album.id}")
                                                    },
                                                    onLongClick = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        menuState.show {
                                                            AlbumMenu(
                                                                originalAlbum = album,
                                                                navController = navController,
                                                                onDismiss = menuState::dismiss
                                                            )
                                                        }
                                                    }
                                                )
                                                .animateItem()
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // YouTube/Remote content sections
                        artistPage?.sections?.fastForEach { section ->
                            if (section.items.isNotEmpty()) {
                                item {
                                    SectionHeader(
                                        text = section.title,
                                        onClick = section.moreEndpoint?.let { endpoint ->
                                            {
                                                val encodedTitle = android.net.Uri.encode(section.title)
                                                val route = "artist/${viewModel.artistId}/items?browseId=${endpoint.browseId}&params=${endpoint.params}&title=$encodedTitle"
                                                if (navController.currentDestination?.route != route) {
                                                    navController.navigate(route)
                                                }
                                            }
                                        },
                                    )
                                }
                            }

                            if ((section.items.firstOrNull() as? SongItem)?.album != null) {
                                // Song items with album info - display as list
                                items(
                                    items = section.items.distinctBy { it.id },
                                    key = { "youtube_song_${it.id}" },
                                ) { song ->
                                    YouTubeListItem(
                                        item = song as SongItem,
                                        isActive = mediaMetadata?.id == song.id,
                                        isPlaying = isPlaying,
                                        trailingContent = {
                                            IconButton(
                                                onClick = {
                                                    menuState.show {
                                                        YouTubeSongMenu(
                                                            song = song,
                                                            navController = navController,
                                                            onDismiss = menuState::dismiss,
                                                        )
                                                    }
                                                },
                                                onLongClick = {},
                                            ) {
                                                Icon(
                                                    painter = painterResource(R.drawable.more_vert),
                                                    contentDescription = null,
                                                )
                                            }
                                        },
                                        modifier = Modifier
                                            .combinedClickable(
                                                onClick = {
                                                    if (song.id == mediaMetadata?.id) {
                                                        playerConnection.player.togglePlayPause()
                                                    } else {
                                                        playerConnection.playQueue(
                                                            YouTubeQueue(
                                                                WatchEndpoint(videoId = song.id),
                                                                song.toMediaMetadata()
                                                            ),
                                                        )
                                                    }
                                                },
                                                onLongClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    menuState.show {
                                                        YouTubeSongMenu(
                                                            song = song,
                                                            navController = navController,
                                                            onDismiss = menuState::dismiss,
                                                        )
                                                    }
                                                },
                                            )
                                            .animateItem(),
                                    )
                                }
                            } else {
                                // Grid items (albums, playlists, etc.)
                                item {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        items(
                                            items = section.items.distinctBy { it.id },
                                            key = {
                                                val type = when (it) {
                                                    is SongItem -> "song"
                                                    is AlbumItem -> "album"
                                                    is ArtistItem -> "artist"
                                                    is PlaylistItem -> "playlist"
                                                    else -> "item"
                                                }
                                                "youtube_${type}_${it.id}"
                                            },
                                        ) { item ->
                                            YouTubeGridItem(
                                                item = item,
                                                isActive = when (item) {
                                                    is SongItem -> mediaMetadata?.id == item.id
                                                    is AlbumItem -> mediaMetadata?.album?.id == item.id
                                                    else -> false
                                                },
                                                isPlaying = isPlaying,
                                                coroutineScope = coroutineScope,
                                                modifier = Modifier
                                                    .combinedClickable(
                                                        onClick = {
                                                            when (item) {
                                                                is SongItem ->
                                                                    playerConnection.playQueue(
                                                                        YouTubeQueue(
                                                                            WatchEndpoint(videoId = item.id),
                                                                            item.toMediaMetadata()
                                                                        ),
                                                                    )

                                                                is AlbumItem -> navController.navigate("album/${item.id}")
                                                                is ArtistItem -> navController.navigate("artist/${item.id}")
                                                                is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
                                                            }
                                                        },
                                                        onLongClick = {
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            menuState.show {
                                                                when (item) {
                                                                    is SongItem ->
                                                                        YouTubeSongMenu(
                                                                            song = item,
                                                                            navController = navController,
                                                                            onDismiss = menuState::dismiss,
                                                                        )

                                                                    is AlbumItem ->
                                                                        YouTubeAlbumMenu(
                                                                            albumItem = item,
                                                                            navController = navController,
                                                                            onDismiss = menuState::dismiss,
                                                                        )

                                                                    is ArtistItem ->
                                                                        YouTubeArtistMenu(
                                                                            artist = item,
                                                                            onDismiss = menuState::dismiss,
                                                                        )

                                                                    is PlaylistItem ->
                                                                        YouTubePlaylistMenu(
                                                                            playlist = item,
                                                                            coroutineScope = coroutineScope,
                                                                            onDismiss = menuState::dismiss,
                                                                        )
                                                                }
                                                            }
                                                        },
                                                    )
                                                    .animateItem(),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom spacing
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

/**
 * Stat item component for displaying statistics like subscriber count, songs, albums
 */
@Composable
private fun StatItem(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    valueColor: Color = ArmoryColors.Gold2,
    labelColor: Color = ArmoryColors.Silver3
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontFamily = GlobalFont,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = labelColor
        )
    }
}
