/*
 * OpenTune Project - Music Player (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ruben.spotitube.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.ruben.spotitube.ArmoryColors
import com.ruben.spotitube.ArmoryHeader
import com.ruben.spotitube.rememberAvatarUrl
import com.ruben.spotitube.constants.ChipSortTypeKey
import com.ruben.spotitube.constants.LibraryFilter
import com.ruben.spotitube.utils.rememberEnumPreference

@Composable
fun LibraryScreen(navController: NavController) {
    var filterType by rememberEnumPreference(ChipSortTypeKey, LibraryFilter.PLAYLISTS)

    val avatarUrl = rememberAvatarUrl()

    val headerContent = @Composable {}

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ArmoryColors.AbsoluteBlack)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ArmoryHeader(
                avatarUrl = avatarUrl,
                onSettings = { navController.navigate("account") }
            )
            
            LibraryPlaylistsScreen(
                navController = navController,
                headerContent = headerContent,
                filterType = filterType,
                onFilterTypeChange = { filterType = it }
            )
        }
    }
}
