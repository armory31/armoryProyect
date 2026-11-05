/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ruben.spotitube.ui.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material3.Text
import com.ruben.spotitube.ArmoryTopBar
import com.ruben.spotitube.ArmoryIconButton
import com.ruben.spotitube.models.toSavedSongEntity
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.ruben.spotitube.LocalPlayerAwareWindowInsets
import com.ruben.spotitube.LocalPlayerConnection
import com.ruben.spotitube.R
import com.ruben.spotitube.constants.DisableBlurKey
import com.ruben.spotitube.constants.StatPeriod
import com.ruben.spotitube.db.entities.Artist
import com.ruben.spotitube.db.entities.SongWithStats
import com.ruben.spotitube.extensions.toMediaItem
import com.ruben.spotitube.extensions.togglePlayPause
import com.ruben.spotitube.innertube.models.WatchEndpoint
import com.ruben.spotitube.models.toMediaMetadata
import com.ruben.spotitube.playback.queues.ListQueue
import com.ruben.spotitube.playback.queues.YouTubeQueue
import com.ruben.spotitube.ui.component.ChoiceChipsRow
import com.ruben.spotitube.ui.component.HideOnScrollFAB
import com.ruben.spotitube.ui.component.ItemThumbnail
import com.ruben.spotitube.ui.component.ListItem
import com.ruben.spotitube.ui.component.LocalAlbumsGrid
import com.ruben.spotitube.ui.component.LocalArtistsGrid
import com.ruben.spotitube.ui.component.LocalMenuState
import com.ruben.spotitube.ui.component.NavigationTitle
import com.ruben.spotitube.ui.menu.AlbumMenu
import com.ruben.spotitube.ui.menu.ArtistMenu
import com.ruben.spotitube.ui.menu.SongMenu
import com.ruben.spotitube.utils.joinByBullet
import com.ruben.spotitube.utils.makeTimeString
import com.ruben.spotitube.utils.rememberPreference
import com.ruben.spotitube.viewmodels.StatsViewModel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.sin

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun StatsScreen(
    navController: NavController,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val indexChips by viewModel.indexChips.collectAsStateWithLifecycle()
    val mostPlayedSongsStats by viewModel.mostPlayedSongsStats.collectAsStateWithLifecycle()
    val mostPlayedArtists by viewModel.mostPlayedArtists.collectAsStateWithLifecycle()
    val mostPlayedAlbums by viewModel.mostPlayedAlbums.collectAsStateWithLifecycle()
    val firstEvent by viewModel.firstEvent.collectAsStateWithLifecycle()
    val firstEventTime = remember(firstEvent) {
        firstEvent?.playedAt?.let { LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(it), java.time.ZoneId.systemDefault()) }
    }
    val currentDate = LocalDateTime.now()

    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    val selectedOption by viewModel.selectedOption.collectAsStateWithLifecycle()

    val weeklyDates =
        if (currentDate != null && firstEventTime != null) {
            generateSequence(currentDate) { it.minusWeeks(1) }
                .takeWhile { it.isAfter(firstEventTime.minusWeeks(1)) }
                .mapIndexed { index, date ->
                    val endDate = date.plusWeeks(1).minusDays(1).coerceAtMost(currentDate)
                    val formatter = DateTimeFormatter.ofPattern("dd MMM")

                    val startDateFormatted = formatter.format(date)
                    val endDateFormatted = formatter.format(endDate)

                    val startMonth = date.month
                    val endMonth = endDate.month
                    val startYear = date.year
                    val endYear = endDate.year

                    val text =
                        when {
                            startYear != currentDate.year -> "$startDateFormatted, $startYear - $endDateFormatted, $endYear"
                            startMonth != endMonth -> "$startDateFormatted - $endDateFormatted"
                            else -> "${date.dayOfMonth} - $endDateFormatted"
                        }
                    Pair(index, text)
                }.toList()
        } else {
            emptyList()
        }

    val monthlyDates =
        if (currentDate != null && firstEventTime != null) {
            generateSequence(
                currentDate.plusMonths(1).withDayOfMonth(1).minusDays(1)
            ) { it.minusMonths(1) }
                .takeWhile {
                    it.isAfter(
                        firstEventTime
                            .withDayOfMonth(1),
                    )
                }.mapIndexed { index, date ->
                    val formatter = DateTimeFormatter.ofPattern("MMM")
                    val formattedDate = formatter.format(date)
                    val text =
                        if (date.year != currentDate.year) {
                            "$formattedDate ${date.year}"
                        } else {
                            formattedDate
                        }
                    Pair(index, text)
                }.toList()
        } else {
            emptyList()
        }

    val yearlyDates =
        if (currentDate != null && firstEventTime != null) {
            generateSequence(
                currentDate
                    .plusYears(1)
                    .withDayOfYear(1)
                    .minusDays(1),
            ) { it.minusYears(1) }
                .takeWhile {
                    it.isAfter(
                        firstEventTime,
                    )
                }.mapIndexed { index, date ->
                    Pair(index, "${date.year}")
                }.toList()
        } else {
            emptyList()
        }

    val (disableBlur) = rememberPreference(DisableBlurKey, true)
    val color1 = MaterialTheme.colorScheme.primary
    val color2 = MaterialTheme.colorScheme.secondary
    val color3 = MaterialTheme.colorScheme.tertiary
    val color4 = MaterialTheme.colorScheme.primaryContainer
    val color5 = MaterialTheme.colorScheme.secondaryContainer
    val surfaceColor = MaterialTheme.colorScheme.surface

    Box(modifier = Modifier.fillMaxSize()) {
        if (!disableBlur) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxSize(0.7f)
                    .align(Alignment.TopCenter)
                    .zIndex(-1f)
                    .drawWithCache {
                        val width = this.size.width
                        val height = this.size.height

                        val brush1 = Brush.radialGradient(
                            colors = listOf(
                                color1.copy(alpha = 0.38f),
                                color1.copy(alpha = 0.24f),
                                color1.copy(alpha = 0.14f),
                                color1.copy(alpha = 0.06f),
                                Color.Transparent
                            ),
                            center = Offset(width * 0.15f, height * 0.1f),
                            radius = width * 0.55f
                        )

                        val brush2 = Brush.radialGradient(
                            colors = listOf(
                                color2.copy(alpha = 0.34f),
                                color2.copy(alpha = 0.2f),
                                color2.copy(alpha = 0.11f),
                                color2.copy(alpha = 0.05f),
                                Color.Transparent
                            ),
                            center = Offset(width * 0.85f, height * 0.2f),
                            radius = width * 0.65f
                        )

                        val brush3 = Brush.radialGradient(
                            colors = listOf(
                                color3.copy(alpha = 0.3f),
                                color3.copy(alpha = 0.17f),
                                color3.copy(alpha = 0.09f),
                                color3.copy(alpha = 0.04f),
                                Color.Transparent
                            ),
                            center = Offset(width * 0.3f, height * 0.45f),
                            radius = width * 0.6f
                        )

                        val brush4 = Brush.radialGradient(
                            colors = listOf(
                                color4.copy(alpha = 0.26f),
                                color4.copy(alpha = 0.14f),
                                color4.copy(alpha = 0.08f),
                                color4.copy(alpha = 0.03f),
                                Color.Transparent
                            ),
                            center = Offset(width * 0.7f, height * 0.5f),
                            radius = width * 0.7f
                        )

                        val brush5 = Brush.radialGradient(
                            colors = listOf(
                                color5.copy(alpha = 0.22f),
                                color5.copy(alpha = 0.12f),
                                color5.copy(alpha = 0.06f),
                                color5.copy(alpha = 0.02f),
                                Color.Transparent
                            ),
                            center = Offset(width * 0.5f, height * 0.75f),
                            radius = width * 0.8f
                        )

                        val overlayBrush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Transparent,
                                surfaceColor.copy(alpha = 0.22f),
                                surfaceColor.copy(alpha = 0.55f),
                                surfaceColor
                            ),
                            startY = height * 0.4f,
                            endY = height
                        )

                        onDrawBehind {
                            drawRect(brush = brush1)
                            drawRect(brush = brush2)
                            drawRect(brush = brush3)
                            drawRect(brush = brush4)
                            drawRect(brush = brush5)
                            drawRect(brush = overlayBrush)
                        }
                    }
            )
        }
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current
                .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                .asPaddingValues(),
            modifier = Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)
            )
        ) {
            item {
                ChoiceChipsRow(
                    chips =
                    when (selectedOption) {
                        OptionStats.WEEKS -> weeklyDates
                        OptionStats.MONTHS -> monthlyDates
                        OptionStats.YEARS -> yearlyDates
                        OptionStats.CONTINUOUS -> {
                            listOf(
                                StatPeriod.WEEK_1.ordinal to pluralStringResource(
                                    R.plurals.n_week,
                                    1,
                                    1
                                ),
                                StatPeriod.MONTH_1.ordinal to pluralStringResource(
                                    R.plurals.n_month,
                                    1,
                                    1
                                ),
                                StatPeriod.MONTH_3.ordinal to pluralStringResource(
                                    R.plurals.n_month,
                                    3,
                                    3
                                ),
                                StatPeriod.MONTH_6.ordinal to pluralStringResource(
                                    R.plurals.n_month,
                                    6,
                                    6
                                ),
                                StatPeriod.YEAR_1.ordinal to pluralStringResource(
                                    R.plurals.n_year,
                                    1,
                                    1
                                ),
                                StatPeriod.ALL.ordinal to stringResource(R.string.filter_all),
                            )
                        }
                    },
                    options =
                    listOf(
                        OptionStats.CONTINUOUS to stringResource(id = R.string.continuous),
                        OptionStats.WEEKS to stringResource(R.string.weeks),
                        OptionStats.MONTHS to stringResource(R.string.months),
                        OptionStats.YEARS to stringResource(R.string.years),
                    ),
                    selectedOption = selectedOption,
                    onSelectionChange = {
                        viewModel.selectedOption.value = it
                        viewModel.indexChips.value = 0
                    },
                    currentValue = indexChips,
                    onValueUpdate = { viewModel.indexChips.value = it },
                )
            }

            // HighLights Section
            item {
                StatsHighlightsSection(
                    topArtist = mostPlayedArtists.firstOrNull(),
                    topSong = mostPlayedSongsStats.firstOrNull(),
                    navController = navController,
                )
            }

            item(key = "artistPieChart") {
                if (mostPlayedArtists.isNotEmpty()) {
                    Spacer(modifier = Modifier.size(16.dp))
                    ArtistPieChart(
                        artists = mostPlayedArtists.take(5), // Top 5 artists for the chart
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .animateItem()
                    )
                    Spacer(modifier = Modifier.size(16.dp))
                }
            }

            item(key = "mostPlayedSongsHeader") {
                NavigationTitle(
                    title = "Top ${mostPlayedSongsStats.size}",
                    modifier = Modifier.animateItem(),
                )
            }

            itemsIndexed(
                items = mostPlayedSongsStats,
                key = { _, song -> song.id },
            ) { index, song ->
                val songItem = song.songItem
                ListItem(
                    title = "${index + 1}. ${song.title}",
                    subtitle = joinByBullet(
                        pluralStringResource(
                            R.plurals.n_time,
                            song.songCountListened,
                            song.songCountListened,
                        ),
                        makeTimeString(song.timeListened),
                    ),
                    thumbnailContent = {
                        ItemThumbnail(
                            thumbnailUrl = song.thumbnailUrl,
                            isActive = song.id == mediaMetadata?.id,
                            isPlaying = isPlaying,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(56.dp)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            enabled = songItem != null,
                            onClick = {
                                if (songItem == null) return@combinedClickable
                                if (song.id == mediaMetadata?.id) {
                                    playerConnection.player.togglePlayPause()
                                } else {
                                    playerConnection.playQueue(
                                        YouTubeQueue(
                                            endpoint = WatchEndpoint(song.id),
                                            preloadItem = songItem.toMediaMetadata(),
                                        ),
                                    )
                                }
                            },
                            onLongClick = {
                                if (songItem != null) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    menuState.show {
                                        SongMenu(
                                            originalSong = songItem.toSavedSongEntity().toSong(),
                                            navController = navController,
                                            onDismiss = menuState::dismiss,
                                        )
                                    }
                                }
                            },
                        )
                        .animateItem()
                )
            }

            item(key = "mostPlayedArtists") {
                NavigationTitle(
                    title = "${mostPlayedArtists.size} ${stringResource(id = R.string.artists)}",
                    modifier = Modifier.animateItem(),
                )
            }

            itemsIndexed(
                items = mostPlayedArtists.chunked(2),
                key = { _, rowArtists -> rowArtists.first().id },
            ) { _, rowArtists ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowArtists.forEach { artist ->
                        LocalArtistsGrid(
                            title = artist.artist.name,
                            subtitle = joinByBullet(
                                pluralStringResource(
                                    R.plurals.n_time,
                                    artist.songCount,
                                    artist.songCount
                                ),
                                makeTimeString(artist.timeListened?.toLong()),
                            ),
                            thumbnailUrl = artist.artist.thumbnailUrl,
                            modifier = Modifier
                                .weight(1f)
                                .combinedClickable(
                                    onClick = {
                                        navController.navigate("artist/${artist.id}")
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        menuState.show {
                                            ArtistMenu(
                                                originalArtist = artist,
                                                coroutineScope = coroutineScope,
                                                onDismiss = menuState::dismiss,
                                            )
                                        }
                                    },
                                )
                                .animateItem(),
                        )
                    }
                    // Add spacers for incomplete rows if needed to maintain grid alignment
                    repeat(2 - rowArtists.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            item(key = "mostPlayedAlbums") {
                NavigationTitle(
                    title = "${mostPlayedAlbums.size} ${stringResource(id = R.string.albums)}",
                    modifier = Modifier.animateItem(),
                )

                if (mostPlayedAlbums.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier,
                    ) {
                        itemsIndexed(
                            items = mostPlayedAlbums,
                            key = { _, album -> album.id },
                        ) { index, album ->
                            LocalAlbumsGrid(
                                title = "${index + 1}. ${album.album.title}",
                                subtitle =
                                joinByBullet(
                                    pluralStringResource(
                                        R.plurals.n_time,
                                        album.songCountListened!!,
                                        album.songCountListened
                                    ),
                                    makeTimeString(album.timeListened?.toLong()),
                                ),
                                thumbnailUrl = album.album.thumbnailUrl,
                                isActive = album.id == mediaMetadata?.album?.id,
                                isPlaying = isPlaying,
                                modifier =
                                Modifier
                                    .fillMaxWidth()
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
                                                    onDismiss = menuState::dismiss,
                                                )
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

        // FAB to shuffle most played songs
        if (mostPlayedSongsStats.isNotEmpty()) {
            HideOnScrollFAB(
                visible = true,
                lazyListState = lazyListState,
                icon = R.drawable.shuffle,
                onClick = {
                    val validItems = mostPlayedSongsStats.mapNotNull { it.songItem?.toMediaItem() }.shuffled()
                    if (validItems.isNotEmpty()) {
                        playerConnection.playQueue(
                            ListQueue(
                                title = context.getString(R.string.most_played_songs),
                                items = validItems
                            )
                        )
                    }
                }
            )
        }

        ArmoryTopBar(
            title = stringResource(R.string.stats),
            onBack = navController::navigateUp,
            actions = {
                ArmoryIconButton(
                    icon = androidx.compose.material.icons.Icons.Rounded.CalendarToday,
                    contentDescription = stringResource(R.string.year_in_music),
                    onClick = { navController.navigate("year_in_music") }
                )
            }
        )
    }
}

@Composable
fun ArtistPieChart(
    artists: List<Artist>,
    modifier: Modifier = Modifier
) {
    val totalTime = artists.sumOf { it.timeListened?.toLong() ?: 0L }
    if (totalTime == 0L) return

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Pie Chart
        Box(
            modifier = Modifier.size(160.dp),
            contentAlignment = Alignment.Center
        ) {
            var startAngle = -90f

            artists.forEach { artist ->
                val time = artist.timeListened?.toLong() ?: 0L
                val sweepAngle = (time.toFloat() / totalTime) * 360f
                
                // Only draw significant slices
                if (sweepAngle > 1f) {
                    AsyncImage(
                        model = artist.artist.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(pieSliceShape(startAngle, sweepAngle))
                    )
                    startAngle += sweepAngle
                }
            }
            
            // Optional: Inner circle for donut effect or just overlay style
            // For now, keeping it as a full pie chart of images as requested
        }

        // Text Info
        Column {
            Text(
                text = "Total Time Listened",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                text = makeTimeString(totalTime),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

fun pieSliceShape(startAngle: Float, sweepAngle: Float): GenericShape {
    return GenericShape { size, _ ->
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.width / 2
        
        moveTo(center.x, center.y)
        
        // Arc start
        val startRad = Math.toRadians(startAngle.toDouble())
        val endRad = Math.toRadians((startAngle + sweepAngle).toDouble())
        
        lineTo(
            (center.x + radius * cos(startRad)).toFloat(),
            (center.y + radius * sin(startRad)).toFloat()
        )
        
        // Add arc
        arcTo(
            rect = androidx.compose.ui.geometry.Rect(
                center = center,
                radius = radius
            ),
            startAngleDegrees = startAngle,
            sweepAngleDegrees = sweepAngle,
            forceMoveTo = false
        )
        
        lineTo(center.x, center.y)
        close()
    }
}

@Composable
fun StatsHighlightsSection(
    topArtist: Artist?,
    topSong: SongWithStats?,
    navController: NavController
) {
    if (topArtist == null && topSong == null) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (topArtist != null) {
            StatsHighlightCard(
                title = "Your Favourite Artist",
                mainText = topArtist.artist.name,
                subText = "${topArtist.songCount} songs played • ${makeTimeString(topArtist.timeListened?.toLong())}",
                imageUrl = topArtist.artist.thumbnailUrl,
                onClick = { navController.navigate("artist/${topArtist.id}") }
            )
        }

        if (topSong != null) {
            StatsHighlightCard(
                title = "Your Favourite Song",
                mainText = topSong.title,
                subText = "${topSong.songCountListened} plays • ${makeTimeString(topSong.timeListened)}",
                imageUrl = topSong.thumbnailUrl,
                onClick = { }
            )
        }
    }
}

@Composable
fun StatsHighlightCard(
    title: String,
    mainText: String,
    subText: String,
    imageUrl: String?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
            )

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = mainText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

enum class OptionStats { WEEKS, MONTHS, YEARS, CONTINUOUS }
