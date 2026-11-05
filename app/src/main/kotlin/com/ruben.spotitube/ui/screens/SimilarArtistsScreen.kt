/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ruben.spotitube.ui.screens

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ruben.spotitube.ArmoryColors
import com.ruben.spotitube.ArmoryTopBar
import com.ruben.spotitube.LocalPlayerAwareWindowInsets
import com.ruben.spotitube.extensions.navigateSafe
import com.ruben.spotitube.innertube.models.ArtistItem
import com.ruben.spotitube.ui.component.YouTubeListItem
import com.ruben.spotitube.viewmodels.HomeViewModel

@Composable
fun SimilarArtistsScreen(
    navController: NavController,
    index: Int,
) {
    val homeEntry = remember(navController.currentBackStackEntry) {
        navController.getBackStackEntry(Screens.Home.route)
    }
    val viewModel: HomeViewModel = hiltViewModel(homeEntry)
    val similarRecommendations by viewModel.similarRecommendations.collectAsStateWithLifecycle()
    val recommendation = similarRecommendations?.getOrNull(index)

    LaunchedEffect(similarRecommendations, recommendation) {
        if (similarRecommendations != null && recommendation == null) {
            navController.popBackStack()
        }
    }

    if (recommendation == null) return

    val artistItems = remember(recommendation) {
        recommendation.items.filterIsInstance<ArtistItem>().distinctBy { it.id }
    }

    Scaffold(
        containerColor = ArmoryColors.AbsoluteBlack,
        topBar = {
            ArmoryTopBar(
                title = recommendation.title.title,
                onBack = navController::navigateUp,
            )
        },
    ) { paddingValues ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = 0.dp,
                bottom = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding(),
            ),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            items(
                items = artistItems,
                key = { it.id },
            ) { item ->
                YouTubeListItem(
                    item = item,
                    modifier = Modifier.clickable {
                        navController.navigateSafe("artist/${Uri.encode(item.id)}")
                    },
                )
            }
        }
    }
}
