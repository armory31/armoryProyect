package com.ruben.spotitube.ui.utils

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.ruben.spotitube.ui.screens.Screens

fun Modifier.tabSwipeNavigation(
    route: String?,
    navController: NavHostController,
): Modifier {
    return if (route in listOf(Screens.Home.route, Screens.Explore.route, Screens.MoodAndGenres.route, Screens.Library.route)) {
        this.pointerInput(route) {
            var totalDrag = 0f
            detectHorizontalDragGestures(
                onDragStart = { totalDrag = 0f },
                onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount },
                onDragEnd = {
                    val currentIdx = when (route) {
                        Screens.Home.route -> 0
                        Screens.Explore.route -> 1
                        Screens.MoodAndGenres.route -> 2
                        Screens.Library.route -> 3
                        else -> 0
                    }
                    if (totalDrag < -120f && currentIdx < 3) {
                        val nextRoute = when (currentIdx + 1) {
                            1 -> Screens.Explore.route
                            2 -> Screens.MoodAndGenres.route
                            3 -> Screens.Library.route
                            else -> Screens.Home.route
                        }
                        com.ruben.spotitube.utils.PerfTiming.onTabClicked(nextRoute)
                        navController.navigate(nextRoute) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    } else if (totalDrag > 120f && currentIdx > 0) {
                        val prevRoute = when (currentIdx - 1) {
                            0 -> Screens.Home.route
                            1 -> Screens.Explore.route
                            2 -> Screens.MoodAndGenres.route
                            else -> Screens.Home.route
                        }
                        com.ruben.spotitube.utils.PerfTiming.onTabClicked(prevRoute)
                        navController.navigate(prevRoute) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
        }
    } else this
}
