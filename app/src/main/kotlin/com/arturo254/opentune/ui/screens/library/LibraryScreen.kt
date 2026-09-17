/*
 * OpenTune Project - Music Player (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.arturo254.opentune.ui.screens.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavController
import com.arturo254.opentune.LocalDatabase
import com.arturo254.opentune.R
import com.arturo254.opentune.constants.ChipSortTypeKey
import com.arturo254.opentune.constants.DisableBlurKey
import com.arturo254.opentune.constants.LibraryFilter
import com.arturo254.opentune.constants.PlaylistTagsFilterKey
import com.arturo254.opentune.constants.ShowTagsInLibraryKey
import androidx.compose.foundation.background
import com.arturo254.opentune.OpenTuneHeader
import com.arturo254.opentune.ArmoryColors
import com.arturo254.opentune.ArmoryFont
import com.arturo254.opentune.GoldDivider
import com.arturo254.opentune.ui.component.ChipsRow
import com.arturo254.opentune.ui.component.TagsFilterChips
import com.arturo254.opentune.utils.rememberEnumPreference
import com.arturo254.opentune.utils.rememberPreference
import com.arturo254.opentune.ArmoryFilterChip

@Composable
fun LibraryScreen(navController: NavController) {
    var filterType by rememberEnumPreference(ChipSortTypeKey, LibraryFilter.PLAYLISTS)

    val (showTagsInLibrary) = rememberPreference(ShowTagsInLibraryKey, true)
    val database = LocalDatabase.current

    val filterContent = @Composable {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(
                text = "Mi Colección",
                fontFamily = ArmoryFont.Title,
                color = ArmoryColors.GoldPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    LibraryFilter.PLAYLISTS to "Playlists",
                    LibraryFilter.ALBUMS to "Álbumes",
                    LibraryFilter.ARTISTS to "Artistas"
                ).forEach { (filter, label) ->
                    val selected = filterType == filter
                    ArmoryFilterChip(
                        selected = selected,
                        onClick = { filterType = filter },
                        label = {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                color = if (selected) ArmoryColors.AbsoluteBlack else ArmoryColors.Champagne,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
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
                onSettings = { navController.navigate("settings") }
            )
            
            when (filterType) {
                LibraryFilter.PLAYLISTS -> LibraryPlaylistsScreen(navController, filterContent)
                LibraryFilter.ALBUMS -> LibraryAlbumsScreen(navController, { filterType = LibraryFilter.PLAYLISTS })
                LibraryFilter.ARTISTS -> LibraryArtistsScreen(navController, { filterType = LibraryFilter.PLAYLISTS })
                else -> LibraryPlaylistsScreen(navController, filterContent)
            }
        }
    }
}
