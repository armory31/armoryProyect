/*
 * OpenTune Project - Music Player (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ruben.spotitube.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ruben.spotitube.ArmoryColors
import com.ruben.spotitube.ArmoryFilterChip
import com.ruben.spotitube.ArmoryFont
import com.ruben.spotitube.GoldDivider
import com.ruben.spotitube.LocalDatabase
import com.ruben.spotitube.OpenTuneHeader
import com.ruben.spotitube.constants.ChipSortTypeKey
import com.ruben.spotitube.constants.LibraryFilter
import com.ruben.spotitube.constants.ShowTagsInLibraryKey
import com.ruben.spotitube.utils.rememberEnumPreference
import com.ruben.spotitube.utils.rememberPreference
import com.google.firebase.auth.FirebaseAuth

@Composable
fun LibraryScreen(navController: NavController) {
    var filterType by rememberEnumPreference(ChipSortTypeKey, LibraryFilter.PLAYLISTS)

    val (showTagsInLibrary) = rememberPreference(ShowTagsInLibraryKey, true)
    val database = LocalDatabase.current

    val currentUser = remember { FirebaseAuth.getInstance().currentUser }
    val avatarUrl = currentUser?.photoUrl?.toString()

    val headerContent = @Composable {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(
                text = "Mi Colección",
                fontFamily = ArmoryFont.Title,
                color = ArmoryColors.GoldPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            GoldDivider()
            Spacer(Modifier.height(16.dp))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ArmoryColors.AbsoluteBlack)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            OpenTuneHeader(
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
