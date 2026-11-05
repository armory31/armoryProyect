/*
 * OpenTune Project - Music Explorer (2026)
 * Licensed Under GPL-3.0
 */

package com.ruben.spotitube.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ruben.spotitube.ArmoryColors
import com.ruben.spotitube.ArmoryHeader
import com.ruben.spotitube.rememberAvatarUrl
import com.ruben.spotitube.GlobalFont
import com.ruben.spotitube.R
import com.ruben.spotitube.ui.screens.musicrecognition.MusicRecognitionRoute
import com.ruben.spotitube.ui.screens.search.OnlineSearchScreen

@Composable
fun ExploreScreen(
    navController: NavController,
) {
    var textFieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    val focusManager = LocalFocusManager.current

    val avatarUrl = rememberAvatarUrl()

    val onSearch = { query: String ->
        if (query.isNotBlank()) {
            textFieldValue = TextFieldValue(query, androidx.compose.ui.text.TextRange(query.length))
            focusManager.clearFocus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ArmoryHeader(
            avatarUrl = avatarUrl,
            onSettings = { navController.navigate("account") }
        )

        // Barra de Búsqueda ARMORY
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .fillMaxWidth()
                .height(52.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(26.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(26.dp))
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.search),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                
                Box(modifier = Modifier.weight(1f)) {
                    if (textFieldValue.text.isEmpty()) {
                        Text(
                            text = stringResource(R.string.search_yt_music),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontFamily = GlobalFont,
                            fontSize = 15.sp
                        )
                    }
                    BasicTextField(
                        value = textFieldValue,
                        onValueChange = { textFieldValue = it },
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = GlobalFont,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            onSearch(textFieldValue.text)
                            focusManager.clearFocus()
                        }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (textFieldValue.text.isNotEmpty()) {
                    IconButton(
                        onClick = { textFieldValue = TextFieldValue("") },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.close),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Fila de accesos rápidos a pantallas de descubrimiento
        if (textFieldValue.text.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExploreShortcutChip(
                    iconRes = R.drawable.album,
                    label = "Novedades",
                    onClick = { navController.navigate("new_release") },
                    modifier = Modifier.weight(1f)
                )
                ExploreShortcutChip(
                    iconRes = R.drawable.trending_up,
                    label = "Éxitos",
                    onClick = { navController.navigate("charts_screen") },
                    modifier = Modifier.weight(1f)
                )
                ExploreShortcutChip(
                    iconRes = R.drawable.music_note,
                    label = "Identificar",
                    onClick = { navController.navigate(MusicRecognitionRoute) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // El componente de búsqueda
        Box(modifier = Modifier.weight(1f)) {
            OnlineSearchScreen(
                query = textFieldValue.text,
                onQueryChange = { textFieldValue = it },
                navController = navController,
                onSearch = onSearch,
                onDismiss = { focusManager.clearFocus() }
            )
        }
    }
}

@Composable
private fun ExploreShortcutChip(
    iconRes: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = ArmoryColors.SurfaceBlack,
        border = BorderStroke(1.dp, ArmoryColors.Gold3.copy(alpha = 0.4f)),
        modifier = modifier.height(42.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp)
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = label,
                tint = ArmoryColors.Gold1,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = ArmoryColors.Silver1,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}
