/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ruben.spotitube.ui.component

import com.ruben.spotitube.PlayerSliderColors

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun BigSeekBar(
    progressProvider: () -> Float,
    onProgressChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    background: Color = MaterialTheme.colorScheme.surfaceTint.copy(alpha = 0.13f),
    color: Color = MaterialTheme.colorScheme.primary,
    steps: Int = 19,
) {
    Slider(
        value = progressProvider(),
        onValueChange = onProgressChange,
        valueRange = 0f..1f,
        steps = steps,
        colors = PlayerSliderColors.getSliderColors(),
        thumb = {
            Spacer(modifier = Modifier.size(0.dp))
        },
        track = { sliderState ->
            PlayerSliderTrack(
                sliderState = sliderState,
                colors = PlayerSliderColors.getSliderColors(),
                trackHeight = 10.dp
            )
        },
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
    )
}
