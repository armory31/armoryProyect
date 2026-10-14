package com.ruben.spotitube.extensions

import androidx.navigation.NavController
import timber.log.Timber

fun NavController.navigateSafe(route: String) {
    try {
        navigate(route)
    } catch (e: IllegalArgumentException) {
        Timber.e(e, "Safe navigation caught error for route: $route")
    }
}
