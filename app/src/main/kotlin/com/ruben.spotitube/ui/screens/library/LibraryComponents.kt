/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ruben.spotitube.ui.screens.library

import androidx.annotation.DrawableRes
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ruben.spotitube.ArmoryColors
import com.ruben.spotitube.ArmoryFont
import com.ruben.spotitube.ui.component.LibraryPinnedCollectionTile

data class LibraryShortcutItem(
    val id: String,
    val title: String,
    @DrawableRes val iconRes: Int,
    val route: String,
    val accentColor: Color,
)

@Composable
fun LibraryShortcutsRow(
    shortcuts: List<LibraryShortcutItem>,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        items(
            items = shortcuts,
            key = { it.id }
        ) { shortcut ->
            LibraryPinnedCollectionTile(
                title = shortcut.title,
                iconRes = shortcut.iconRes,
                accentColor = shortcut.accentColor,
                modifier = Modifier.combinedClickable(onClick = { onClick(shortcut.route) }),
            )
        }
    }
}

@Composable
fun <T> LibraryFilterChips(
    filters: List<Pair<T, String>>,
    selectedFilter: T,
    onFilterSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        items(
            items = filters,
            key = { it.second }
        ) { (filter, label) ->
            val isSelected = filter == selectedFilter
            FilterChip(
                selected = isSelected,
                onClick = { onFilterSelected(filter) },
                label = {
                    Text(
                        text = label,
                        fontFamily = ArmoryFont.Body,
                        color = if (isSelected) ArmoryColors.Gold1 else ArmoryColors.Silver2
                    )
                },
                shape = RoundedCornerShape(16.dp),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = ArmoryColors.Gold5.copy(alpha = 0.3f),
                    labelColor = ArmoryColors.Gold2,
                    selectedContainerColor = ArmoryColors.Gold5.copy(alpha = 0.5f),
                    selectedLabelColor = ArmoryColors.Gold1
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = ArmoryColors.Gold4.copy(alpha = 0.4f),
                    selectedBorderColor = ArmoryColors.Gold3.copy(alpha = 0.6f)
                )
            )
        }
    }
}

@Composable
fun LibrarySortHeaderCard(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ArmoryColors.Silver5.copy(alpha = 0.5f)),
        shape = MaterialTheme.shapes.large,
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .border(1.dp, ArmoryColors.Silver4.copy(alpha = 0.3f), MaterialTheme.shapes.large),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            content = content,
        )
    }
}
