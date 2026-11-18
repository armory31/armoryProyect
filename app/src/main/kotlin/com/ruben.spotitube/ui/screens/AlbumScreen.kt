/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ruben.spotitube.ui.screens

import com.ruben.spotitube.ArmoryColors
import com.ruben.spotitube.ArmoryArtworkBackdrop
import com.ruben.spotitube.ArmoryGradients
import com.ruben.spotitube.ArtistNameColor
import com.ruben.spotitube.MainHeadingColor
import androidx.compose.material3.ButtonDefaults

import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import com.ruben.spotitube.ui.LocalDatabase
import com.ruben.spotitube.ui.LocalDownloadUtil
import com.ruben.spotitube.ui.LocalPlayerAwareWindowInsets
import com.ruben.spotitube.ui.LocalPlayerConnection
import com.ruben.spotitube.R
import com.ruben.spotitube.constants.AppBarHeight
import com.ruben.spotitube.constants.DisableBlurKey
import com.ruben.spotitube.constants.HideExplicitKey
import com.ruben.spotitube.db.entities.Album
import com.ruben.spotitube.db.entities.DownloadOriginType
import com.ruben.spotitube.extensions.togglePlayPause
import com.ruben.spotitube.models.DownloadedSongMetadata
import com.ruben.spotitube.playback.queues.LocalAlbumRadio
import com.ruben.spotitube.ui.component.IconButton
import com.ruben.spotitube.ui.component.LocalMenuState
import com.ruben.spotitube.ui.component.EmptyPlaceholder
import com.ruben.spotitube.SectionHeader
import com.ruben.spotitube.ArmoryScrollTopBar
import com.ruben.spotitube.ArmoryIconButton
import com.ruben.spotitube.ui.component.SongListItem
import com.ruben.spotitube.ui.component.shimmer.ButtonPlaceholder
import com.ruben.spotitube.ui.component.shimmer.ListItemPlaceHolder
import com.ruben.spotitube.ui.component.shimmer.ShimmerHost
import com.ruben.spotitube.ui.component.shimmer.TextPlaceholder
import com.ruben.spotitube.ui.menu.AlbumMenu
import com.ruben.spotitube.ui.menu.SelectionSongMenu
import com.ruben.spotitube.ui.menu.SongMenu
import com.ruben.spotitube.utils.AlbumDateCache
import com.ruben.spotitube.ui.utils.ItemWrapper
import com.ruben.spotitube.utils.makeTimeString
import com.ruben.spotitube.utils.rememberPreference
import com.ruben.spotitube.viewmodels.AlbumUiState
import com.ruben.spotitube.viewmodels.AlbumViewModel
import com.valentinilk.shimmer.shimmer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ============================================================================
// FUNCIONES DE DESCARGA
// ============================================================================

/**
 * Descarga la carátula del álbum en la calidad especificada
 * @param quality Calidad: 512 (baja), 768 (media), 1024 (alta)
 */
suspend fun downloadAlbumCover(
    context: android.content.Context,
    imageUrl: String?,
    albumTitle: String,
    quality: Int = 1024,
): Boolean {
    return !imageUrl.isNullOrEmpty() && try {
        val request = ImageRequest.Builder(context)
            .data(imageUrl)
            .size(Size(quality, quality))
            .allowHardware(false)
            .build()

        val result = context.imageLoader.execute(request)
        val bitmap = result.image?.toBitmap() ?: return false

        val qualityLabel = when (quality) {
            512 -> "baja"
            768 -> "media"
            1024 -> "alta"
            else -> "custom"
        }

        val filename = "${albumTitle.replace("/", "_")}_${qualityLabel}_${System.currentTimeMillis()}.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/OpenTune")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val imageUri = context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ) ?: return false

        context.contentResolver.openOutputStream(imageUri)?.use { outputStream ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, outputStream)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            context.contentResolver.update(imageUri, contentValues, null, null)
        }

        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

// ============================================================================
// COMPOSABLE DIALOG DE CALIDAD
// ============================================================================

@Composable
private fun QualitySelectionDialog(
    onDismiss: () -> Unit,
    onQualitySelected: (Int) -> Unit,
) {
    var selectedQuality by remember { mutableIntStateOf(1024) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Selecciona la calidad",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Opción Baja Calidad
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .selectable(
                            selected = selectedQuality == 512,
                            onClick = { selectedQuality = 512 },
                            role = Role.RadioButton
                        )
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RadioButton(
                        selected = selectedQuality == 512,
                        onClick = { selectedQuality = 512 }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Baja (512x512)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "~50 KB",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Opción Media Calidad
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .selectable(
                            selected = selectedQuality == 768,
                            onClick = { selectedQuality = 768 },
                            role = Role.RadioButton
                        )
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RadioButton(
                        selected = selectedQuality == 768,
                        onClick = { selectedQuality = 768 }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Media (768x768)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "~100 KB",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Opción Alta Calidad
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .selectable(
                            selected = selectedQuality == 1024,
                            onClick = { selectedQuality = 1024 },
                            role = Role.RadioButton
                        )
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RadioButton(
                        selected = selectedQuality == 1024,
                        onClick = { selectedQuality = 1024 }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Alta (1024x1024)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "~200 KB",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onQualitySelected(selectedQuality)
                    onDismiss()
                }
            ) {
                Text("Descargar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun AlbumScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: AlbumViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val scope = rememberCoroutineScope()

    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val playlistId by viewModel.playlistId.collectAsStateWithLifecycle()
    val albumWithSongs by viewModel.albumWithSongs.collectAsStateWithLifecycle()
    val isSaved by viewModel.isSaved.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)
    val (disableBlur) = rememberPreference(DisableBlurKey, false)

    // System bars padding
    val systemBarsTopPadding = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()

    val surfaceColor = MaterialTheme.colorScheme.background

    // Estados para descarga de carátula
    var downloadingCover by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }

    val wrappedSongs = remember(albumWithSongs, hideExplicit) {
        val filteredSongs = if (hideExplicit) {
            albumWithSongs?.songs?.filter { !it.song.explicit } ?: emptyList()
        } else {
            albumWithSongs?.songs ?: emptyList()
        }
        filteredSongs.map { item -> ItemWrapper(item) }.toMutableStateList()
    }

    var isSearching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val visibleSongs = remember(wrappedSongs, query.text) {
        val searchText = query.text.trim()
        wrappedSongs.mapIndexedNotNull { index, songWrapper ->
            val song = songWrapper.item
            if (
                searchText.isEmpty() ||
                song.title.contains(searchText, ignoreCase = true) ||
                song.artists.any { it.name.contains(searchText, ignoreCase = true) }
            ) {
                index to songWrapper
            } else {
                null
            }
        }
    }

    LaunchedEffect(isSearching) {
        if (isSearching) {
            focusRequester.requestFocus()
        } else {
            focusManager.clearFocus()
        }
    }

    var selection by remember { mutableStateOf(false) }

    if (selection || isSearching) {
        BackHandler {
            if (selection) {
                selection = false
            } else {
                isSearching = false
                query = TextFieldValue()
            }
        }
    }

    val downloadUtil = LocalDownloadUtil.current
    val downloadState = downloadUtil.originState(DownloadOriginType.ALBUM, viewModel.albumId)
        .collectAsStateWithLifecycle(initialValue = Download.STATE_STOPPED).value

    // State for LazyColumn to track scroll
    val lazyListState = rememberLazyListState()

    // Calculate gradient opacity based on scroll position
    val gradientAlpha by remember {
        derivedStateOf {
            if (lazyListState.firstVisibleItemIndex == 0) {
                val offset = lazyListState.firstVisibleItemScrollOffset
                (1f - (offset / 600f)).coerceIn(0f, 1f)
            } else {
                0f
            }
        }
    }


    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(surfaceColor),
    ) {
        ArmoryArtworkBackdrop(
            imageUrls = listOf(albumWithSongs?.album?.thumbnailUrl),
            alpha = gradientAlpha,
            enabled = !disableBlur,
            backgroundColor = surfaceColor
        )

        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
        ) {
            val albumWithSongs = albumWithSongs
            val hasSongs = albumWithSongs?.songs?.isNotEmpty() == true
            if (hasSongs) {
                if (!isSearching) {
                    // Hero Header
                    item(
                        key = "header",
                        contentType = "header"
                    ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Album Art - Large centered with shadow and rounded corners
                        Box(
                            modifier = Modifier
                                .padding(top = 8.dp, bottom = 20.dp)
                        ) {
                            Surface(
                                modifier = Modifier
                                    .size(240.dp)
                                    .shadow(
                                        elevation = 24.dp,
                                        shape = RoundedCornerShape(16.dp),
                                        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                    ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                AsyncImage(
                                    model = albumWithSongs.album.thumbnailUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            // Download Cover Button - Inside image, bottom-right
                            Surface(
                                onClick = { showQualityDialog = true },
                                shape = CircleShape,
                                color = Color.Transparent,
                                modifier = Modifier
                                    .size(48.dp) // Tamaño ligeramente reducido
                                    .align(Alignment.BottomEnd)
                                    .offset(x = (-12).dp, y = (-12).dp)
                                    .shadow(
                                        elevation = 4.dp, // Sombra más sutil
                                        shape = CircleShape,
                                        ambientColor = Color.Black.copy(alpha = 0.15f),
                                        spotColor = Color.Black.copy(alpha = 0.15f)
                                    )
                                    .clip(CircleShape)
                                    .background(
                                        color = Color.Black.copy(alpha = 0.2f), // Más transparente
                                        shape = CircleShape
                                    )
                                    .border(
                                        width = 0.5.dp, // Borde más fino
                                        color = Color.White.copy(alpha = 0.15f),
                                        shape = CircleShape
                                    )
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (downloadingCover) {
                                        CircularProgressIndicator(
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(24.dp), // Ícono más pequeño
                                            color = Color.White.copy(alpha = 0.9f)
                                        )
                                    } else {
                                        Icon(
                                            painter = painterResource(R.drawable.download),
                                            contentDescription = "Descargar carátula",
                                            tint = Color.White.copy(alpha = 0.8f), // Ícono semi-transparente
                                            modifier = Modifier.size(24.dp) // Ícono más pequeño
                                        )
                                    }
                                }
                            }
                        }

                        // Artist Names (Clickable)
                        Text(
                            text = buildAnnotatedString {
                                withStyle(
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Normal,
                                        color = ArtistNameColor
                                    ).toSpanStyle()
                                ) {
                                    albumWithSongs.artists.fastForEachIndexed { index, artist ->
                                        val link = LinkAnnotation.Clickable(artist.id) {
                                            navController.navigate("artist/${artist.id}")
                                        }
                                        withLink(link) {
                                            append(artist.name)
                                        }
                                        if (index != albumWithSongs.artists.lastIndex) {
                                            append(", ")
                                        }
                                    }
                                }
                            },
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Metadata Row - Year, Song Count, Duration
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 48.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Year
                            albumWithSongs.album.year?.let { year ->
                                MetadataChip(
                                    icon = R.drawable.calendar_today,
                                    text = AlbumDateCache.get(viewModel.albumId) ?: year.toString(),
                                    textColor = ArmoryColors.TextGold,
                                    iconTint = ArmoryColors.TextGold
                                )
                            }

                            // Song Count
                            MetadataChip(
                                icon = R.drawable.music_note,
                                text = pluralStringResource(
                                    R.plurals.n_song,
                                    wrappedSongs.size,
                                    wrappedSongs.size
                                ),
                                textColor = ArmoryColors.TextGold,
                                iconTint = ArmoryColors.TextGold
                            )

                            // Duration
                            val totalDuration = albumWithSongs.songs.sumOf { it.song.duration }
                            if (totalDuration > 0) {
                                MetadataChip(
                                    icon = R.drawable.timer,
                                    text = makeTimeString(totalDuration * 1000L),
                                    textColor = ArmoryColors.TextGold,
                                    iconTint = ArmoryColors.TextGold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Action Buttons Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Like/Bookmark Button
                            Surface(
                                onClick = {
                                    viewModel.toggleSaved()
                                },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(
                                            if (isSaved)
                                                R.drawable.favorite
                                            else
                                                R.drawable.favorite_border
                                        ),
                                        contentDescription = null,
                                        tint = if (isSaved)
                                            MaterialTheme.colorScheme.error
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            // Play Button (Plata Metálico)
                            Button(
                                onClick = {
                                    playerConnection.service.getAutomix(playlistId)
                                    playerConnection.playQueue(
                                        LocalAlbumRadio(albumWithSongs),
                                    )
                                },
                                shape = RoundedCornerShape(24.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Transparent,
                                    contentColor = Color.Black
                                ),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .background(brush = ArmoryGradients.MetallicSilver, shape = RoundedCornerShape(24.dp))
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.play),
                                    contentDescription = stringResource(R.string.play),
                                    tint = Color.Black,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Shuffle Button (Plata Metálico)
                            Button(
                                onClick = {
                                    playerConnection.service.getAutomix(playlistId)
                                    playerConnection.playQueue(
                                        LocalAlbumRadio(albumWithSongs.copy(songs = albumWithSongs.songs.shuffled())),
                                    )
                                },
                                shape = RoundedCornerShape(24.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Transparent,
                                    contentColor = Color.Black
                                ),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .background(brush = ArmoryGradients.MetallicSilver, shape = RoundedCornerShape(24.dp))
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.shuffle),
                                    contentDescription = stringResource(R.string.shuffle),
                                    tint = Color.Black,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Download Button (Bronce Metálico)
                            Surface(
                                onClick = {
                                    when (downloadState) {
                                        Download.STATE_COMPLETED -> {
                                            scope.launch {
                                                downloadUtil.removeOrigin(DownloadOriginType.ALBUM, viewModel.albumId)
                                            }
                                        }
                                        Download.STATE_DOWNLOADING, Download.STATE_QUEUED -> {
                                            scope.launch {
                                                downloadUtil.cancelPending(DownloadOriginType.ALBUM, viewModel.albumId)
                                            }
                                        }
                                        else -> {
                                            albumWithSongs?.let { data ->
                                                scope.launch {
                                                    downloadUtil.enqueue(
                                                        DownloadOriginType.ALBUM,
                                                        viewModel.albumId,
                                                        data.album.title,
                                                        data.album.thumbnailUrl,
                                                        data.songs.map { DownloadedSongMetadata.fromSong(it) }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                },
                                shape = CircleShape,
                                color = Color.Transparent,
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(brush = ArmoryGradients.MetallicCopper, shape = CircleShape)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    when (downloadState) {
                                        Download.STATE_COMPLETED -> {
                                            Icon(
                                                painter = painterResource(R.drawable.offline),
                                                contentDescription = null,
                                                tint = Color.Black,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        Download.STATE_DOWNLOADING -> {
                                            CircularProgressIndicator(
                                                strokeWidth = 2.dp,
                                                modifier = Modifier.size(24.dp),
                                                color = Color.Black
                                            )
                                        }
                                        else -> {
                                            Icon(
                                                painter = painterResource(R.drawable.download),
                                                contentDescription = null,
                                                tint = Color.Black,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // More Options Button (Bronce Metálico - El de al lado)
                            Surface(
                                onClick = {
                                    menuState.show {
                                        AlbumMenu(
                                            originalAlbum = Album(
                                                albumWithSongs.album,
                                                albumWithSongs.artists
                                            ),
                                            navController = navController,
                                            onDismiss = menuState::dismiss,
                                        )
                                    }
                                },
                                shape = CircleShape,
                                color = Color.Transparent,
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(brush = ArmoryGradients.MetallicCopper, shape = CircleShape)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.more_vert),
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
                }

                if (!isSearching) {
                    // Songs Section Header
                    item(
                        key = "songs_header",
                        contentType = "section_header"
                    ) {
                        SectionHeader(
                            text = stringResource(R.string.songs),
                        )
                    }
                }

                if (visibleSongs.isEmpty() && isSearching) {
                    item(key = "no_search_results") {
                        EmptyPlaceholder(
                            icon = R.drawable.search,
                            text = stringResource(R.string.no_results_found),
                        )
                    }
                } else {
                    // Songs List
                    itemsIndexed(
                        items = visibleSongs,
                        key = { _, entry -> "${entry.first}_${entry.second.item.id}" },
                        contentType = { _, _ -> "song_item" },
                    ) { _, entry ->
                        val (index, songWrapper) = entry
                        SongListItem(
                            song = songWrapper.item,
                            albumIndex = index + 1,
                            isActive = songWrapper.item.id == mediaMetadata?.id,
                            isPlaying = isPlaying,
                            showInLibraryIcon = true,
                            trailingContent = {
                                IconButton(
                                    onClick = {
                                        menuState.show {
                                            SongMenu(
                                                originalSong = songWrapper.item,
                                                navController = navController,
                                                onDismiss = menuState::dismiss,
                                            )
                                        }
                                    },
                                    onLongClick = {}
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.more_vert),
                                        contentDescription = null,
                                    )
                                }
                            },
                            isSelected = songWrapper.isSelected && selection,
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (!selection) {
                                            if (songWrapper.item.id == mediaMetadata?.id) {
                                                playerConnection.player.togglePlayPause()
                                            } else {
                                                playerConnection.service.getAutomix(playlistId)
                                                playerConnection.playQueue(
                                                    LocalAlbumRadio(albumWithSongs, startIndex = index),
                                                )
                                            }
                                        } else {
                                            songWrapper.isSelected = !songWrapper.isSelected
                                        }
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (!selection) {
                                            selection = true
                                        }
                                        wrappedSongs.forEach { it.isSelected = false }
                                        songWrapper.isSelected = true
                                    },
                                ),
                        )
                    }
                }
            } else {
                when (val state = uiState) {
                    AlbumUiState.Loading,
                    AlbumUiState.Content -> {
                        item(key = "shimmer") {
                            ShimmerHost {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = systemBarsTopPadding + AppBarHeight),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 8.dp, bottom = 20.dp)
                                            .size(240.dp)
                                            .shimmer()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(MaterialTheme.colorScheme.onSurface)
                                    )

                                    TextPlaceholder(
                                        height = 28.dp,
                                        modifier = Modifier
                                            .fillMaxWidth(0.6f)
                                            .padding(horizontal = 32.dp)
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    TextPlaceholder(
                                        height = 20.dp,
                                        modifier = Modifier.fillMaxWidth(0.4f)
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 48.dp),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        repeat(3) {
                                            TextPlaceholder(
                                                height = 32.dp,
                                                modifier = Modifier.width(70.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(24.dp))

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 24.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .shimmer()
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.onSurface)
                                        )
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
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .shimmer()
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.onSurface)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(24.dp))
                                }

                                repeat(6) {
                                    ListItemPlaceHolder()
                                }
                            }
                        }
                    }

                    AlbumUiState.Empty -> {
                        item(key = "empty") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = systemBarsTopPadding + AppBarHeight)
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = stringResource(R.string.empty_album),
                                    style = MaterialTheme.typography.titleLarge,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.empty_album_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    is AlbumUiState.Error -> {
                        item(key = "error") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = systemBarsTopPadding + AppBarHeight)
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (state.isNotFound) stringResource(R.string.album_not_found) else stringResource(R.string.error_unknown),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = if (state.isNotFound) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (state.isNotFound) stringResource(R.string.album_not_found_desc) else stringResource(R.string.error_unknown),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = { viewModel.retry() }) {
                                    Text(stringResource(R.string.retry))
                                }
                            }
                        }
                    }
                }
            }
        }

        ArmoryScrollTopBar(
            title = {
                if (selection) {
                    val count = wrappedSongs.count { it.isSelected }
                    Text(
                        text = pluralStringResource(R.plurals.n_song, count, count),
                        style = MaterialTheme.typography.titleLarge,
                        color = MainHeadingColor
                    )
                } else if (isSearching) {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.search),
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleMedium,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                    )
                } else {
                    Text(
                        text = albumWithSongs?.album?.title.orEmpty(),
                        style = MaterialTheme.typography.titleLarge,
                        color = MainHeadingColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            listState = lazyListState,
            forceOpaque = disableBlur || selection,
            onBack = {
                if (selection) {
                    selection = false
                } else if (isSearching) {
                    isSearching = false
                    query = TextFieldValue()
                } else {
                    navController.navigateUp()
                }
            },
            centerTitle = true,
            actions = { transparentAppBar ->
                if (selection) {
                    val count = wrappedSongs.count { it.isSelected }
                    ArmoryIconButton(
                        painter = painterResource(
                            if (count == wrappedSongs.size) R.drawable.deselect else R.drawable.select_all
                        ),
                        contentDescription = "Seleccionar",
                        onClick = {
                            if (count == wrappedSongs.size) {
                                wrappedSongs.forEach { it.isSelected = false }
                            } else {
                                wrappedSongs.forEach { it.isSelected = true }
                            }
                        },
                        overImage = transparentAppBar
                    )
                    ArmoryIconButton(
                        painter = painterResource(R.drawable.more_vert),
                        contentDescription = "Más",
                        onClick = {
                            menuState.show {
                                SelectionSongMenu(
                                    songSelection = wrappedSongs.filter { it.isSelected }
                                        .map { it.item },
                                    onDismiss = menuState::dismiss,
                                    clearAction = { selection = false }
                                )
                            }
                        },
                        overImage = transparentAppBar
                    )
                } else if (!isSearching && wrappedSongs.size > 1) {
                    ArmoryIconButton(
                        painter = painterResource(R.drawable.search),
                        contentDescription = "Buscar",
                        onClick = {
                            query = TextFieldValue()
                            isSearching = true
                        },
                        overImage = transparentAppBar
                    )
                }
            }
        )

        // Dialog para seleccionar calidad
        if (showQualityDialog) {
            QualitySelectionDialog(
                onDismiss = { showQualityDialog = false },
                onQualitySelected = { quality ->
                    downloadingCover = true
                    scope.launch {
                        try {
                            val success = downloadAlbumCover(
                                context = context,
                                imageUrl = albumWithSongs?.album?.thumbnailUrl,
                                albumTitle = albumWithSongs?.album?.title ?: "album",
                                quality = quality
                            )

                            val qualityLabel = when (quality) {
                                512 -> "Baja (512x512)"
                                768 -> "Media (768x768)"
                                1024 -> "Alta (1024x1024)"
                                else -> "Custom"
                            }
                        } finally {
                            downloadingCover = false
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun MetadataChip(
    icon: Int,
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color? = null,
    iconTint: Color? = null
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = iconTint ?: MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = textColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}