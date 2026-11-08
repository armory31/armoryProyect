/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ruben.spotitube.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.ruben.spotitube.ArmoryColors
import com.ruben.spotitube.AppTypography
import com.ruben.spotitube.LocalPlayerAwareWindowInsets
import com.ruben.spotitube.ArmoryHeader
import com.ruben.spotitube.rememberAvatarUrl
import com.ruben.spotitube.ui.component.shimmer.ShimmerHost
import com.ruben.spotitube.ui.component.shimmer.TextPlaceholder
import com.ruben.spotitube.viewmodels.MoodAndGenresViewModel

@Composable
fun MoodAndGenresScreen(
    navController: NavController,
    viewModel: MoodAndGenresViewModel = run {
        val context = androidx.compose.ui.platform.LocalContext.current
        val owner = androidx.compose.runtime.remember(context) {
            var ctx = context
            while (ctx is android.content.ContextWrapper) {
                if (ctx is androidx.activity.ComponentActivity) return@remember ctx
                ctx = ctx.baseContext
            }
            null
        }
        if (owner != null) hiltViewModel(viewModelStoreOwner = owner) else hiltViewModel()
    },
) {
    val moodAndGenres by viewModel.moodAndGenres.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    val density = LocalDensity.current
    val windowInsets = LocalPlayerAwareWindowInsets.current
    val bottomPadding = with(density) { windowInsets.getBottom(this).toDp() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop =
        backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsStateWithLifecycle()

    val avatarUrl = rememberAvatarUrl()

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    LaunchedEffect(moodAndGenres) {
        if (moodAndGenres != null) {
            com.ruben.spotitube.utils.PerfTiming.onTabComposedWithData(Screens.MoodAndGenres.route, hasData = true)
        }
    }

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            gridState.animateScrollToItem(0)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        ArmoryHeader(
            avatarUrl = avatarUrl,
            onSettings = { navController.navigate("account") }
        )

        val moodAndGenresList = androidx.compose.runtime.remember(moodAndGenres) { moodAndGenres.orEmpty() }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = 12.dp,
                top = 0.dp,
                end = 12.dp,
                bottom = bottomPadding + 12.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (moodAndGenres == null) {
                items(12) {
                    ShimmerHost {
                        TextPlaceholder(
                            height = MoodAndGenresButtonHeight,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            } else {
                items(
                    items = moodAndGenresList,
                    key = { item -> "${item.title}:${item.endpoint.browseId}:${item.endpoint.params}" },
                ) { item ->
                    MoodAndGenresButton(
                        title = item.title,
                        stripeColor = item.stripeColor,
                        onClick = {
                            val encodedTitle = Uri.encode(item.title)
                            navController.navigate("youtube_browse/${item.endpoint.browseId}?params=${item.endpoint.params}&title=$encodedTitle")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
fun MoodAndGenresButton(
    title: String,
    stripeColor: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val baseColor = if (stripeColor != 0L) Color(stripeColor) else ArmoryColors.Gold2
    val tinted = lerp(ArmoryColors.SurfaceBlack, baseColor, 0.55f)

    Box(
        modifier = modifier
            .height(MoodAndGenresButtonHeight)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        tinted,
                        lerp(ArmoryColors.SurfaceBlack, baseColor, 0.18f)
                    ),
                    start = Offset(Float.POSITIVE_INFINITY, 0f),
                    end = Offset(0f, Float.POSITIVE_INFINITY)
                )
            )
            .border(
                1.dp,
                baseColor.copy(alpha = 0.35f),
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Text(
            text = title,
            style = AppTypography.titleMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            color = ArmoryColors.TextGold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
        )
    }
}

val MoodAndGenresButtonHeight = 88.dp
