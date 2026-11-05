/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ruben.spotitube.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ruben.spotitube.R
import com.ruben.spotitube.constants.ALL_SELECTABLE_COUNTRY_CODES
import com.ruben.spotitube.constants.RegionGroup
import com.ruben.spotitube.constants.getCountryDisplayName

@Composable
fun RegionGroupsEditorDialog(
    groups: List<RegionGroup>,
    onConfirm: (List<RegionGroup>) -> Unit,
    onDismiss: () -> Unit,
) {
    var tempGroups by remember(groups) { mutableStateOf(groups) }
    var editingGroupIndex by remember { mutableStateOf<Int?>(null) }

    // Sub-diálogo para seleccionar países de un grupo
    editingGroupIndex?.let { index ->
        val group = tempGroups.getOrNull(index)
        if (group != null) {
            var selectedCodes by remember(group.codes) { mutableStateOf(group.codes.toSet()) }
            AlertDialog(
                onDismissRequest = { editingGroupIndex = null },
                title = {
                    Text(
                        text = "Países (${group.name.ifBlank { "Sin nombre" }})",
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                text = {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                    ) {
                        items(
                            items = ALL_SELECTABLE_COUNTRY_CODES,
                            key = { it }
                        ) { code ->
                            val isChecked = code in selectedCodes
                            val name = getCountryDisplayName(code)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedCodes = if (isChecked) selectedCodes - code else selectedCodes + code
                                    }
                                    .padding(vertical = 4.dp, horizontal = 4.dp)
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        selectedCodes = if (checked) selectedCodes + code else selectedCodes - code
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (name.equals(code, ignoreCase = true)) code else "$name ($code)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            tempGroups = tempGroups.toMutableList().apply {
                                this[index] = this[index].copy(codes = selectedCodes.toList())
                            }
                            editingGroupIndex = null
                        }
                    ) {
                        Text(stringResource(R.string.ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { editingGroupIndex = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
                shape = RoundedCornerShape(24.dp)
            )
        }
    }

    // Diálogo principal
    val isApplyEnabled = tempGroups.isNotEmpty() &&
            tempGroups.none { it.name.isBlank() } &&
            tempGroups.none { it.codes.isEmpty() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.select_countries),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                ) {
                    itemsIndexed(
                        items = tempGroups,
                        key = { _, item -> item.id }
                    ) { index, group ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedTextField(
                                        value = group.name,
                                        onValueChange = { newName ->
                                            tempGroups = tempGroups.toMutableList().apply {
                                                this[index] = this[index].copy(name = newName)
                                            }
                                        },
                                        label = { Text("Nombre del grupo") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = {
                                            tempGroups = tempGroups.toMutableList().apply {
                                                removeAt(index)
                                            }
                                        }
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.delete),
                                            contentDescription = "Borrar grupo",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    val summaryText = if (group.codes.isEmpty()) {
                                        "Sin países seleccionados"
                                    } else {
                                        group.codes.joinToString(", ") { getCountryDisplayName(it) }
                                    }
                                    Text(
                                        text = summaryText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    OutlinedButton(
                                        onClick = { editingGroupIndex = index }
                                    ) {
                                        Text("Países (${group.codes.size})")
                                    }
                                }
                            }
                        }
                    }

                    item {
                        OutlinedButton(
                            onClick = {
                                tempGroups = tempGroups + RegionGroup(
                                    name = "Grupo ${tempGroups.size + 1}",
                                    codes = emptyList()
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.add),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Añadir grupo")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(tempGroups) },
                enabled = isApplyEnabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            ) {
                Text(stringResource(R.string.apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}
