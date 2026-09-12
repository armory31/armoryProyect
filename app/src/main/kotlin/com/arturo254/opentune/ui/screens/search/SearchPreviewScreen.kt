package com.arturo254.opentune.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arturo254.opentune.ArmoryColors
import com.arturo254.opentune.PremiumTitleFont

@Composable
fun SearchPreviewScreen(onGenreClick: (String) -> Unit) {
    val genres = listOf(
        GenreItem("Pop", ArmoryColors.Gold3),
        GenreItem("Rock", ArmoryColors.Copper2),
        GenreItem("Urbano", ArmoryColors.Silver3),
        GenreItem("Indie", ArmoryColors.Diamond3),
        GenreItem("Flamenco", ArmoryColors.Rust2),
        GenreItem("Jazz", ArmoryColors.Gold2),
        GenreItem("Clásica", ArmoryColors.Silver2),
        GenreItem("Electrónica", ArmoryColors.Diamond2)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ArmoryColors.AbsoluteBlack)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Explorar Todo",
            color = ArmoryColors.GoldPrimary,
            fontFamily = PremiumTitleFont,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(genres) { genre ->
                GenreCard(genre) { onGenreClick(genre.name) }
            }
        }
    }
}

data class GenreItem(val name: String, val color: Color)

@Composable
fun GenreCard(genre: GenreItem, onClick: () -> Unit) {
    val darkVariant = genre.color.copy(alpha = 0.4f)
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(ArmoryColors.ObsidianSoft)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(genre.color.copy(alpha = 0.3f), Color.Transparent),
                    start = Offset(0f, 0f),
                    end = Offset(100f, 100f)
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
    ) {
        // Efecto Radial de fondo
        Box(modifier = Modifier.fillMaxSize().background(
            Brush.radialGradient(
                colors = listOf(genre.color.copy(alpha = 0.15f), Color.Transparent),
                center = Offset(200f, 0f),
                radius = 300f
            )
        ))

        Text(
            text = genre.name,
            color = ArmoryColors.Champagne,
            fontFamily = PremiumTitleFont,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        )
    }
}
