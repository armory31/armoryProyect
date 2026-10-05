/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ruben.spotitube.ui.screens.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.ruben.spotitube.BuildConfig
import com.ruben.spotitube.R
import androidx.core.net.toUri

@Composable
fun buildQuickActions(
    navController: NavController,
    resetSearch: () -> Unit,
): List<SettingsQuickAction> =
    listOf(
        SettingsQuickAction(
            icon = painterResource(R.drawable.storage),
            label = stringResource(R.string.storage),
            onClick = { resetSearch(); navController.navigate("settings/storage") },
            accentColor = MaterialTheme.colorScheme.secondary,
        ),
    )

@Composable
fun buildIntegrationActions(
    navController: NavController,
    resetSearch: () -> Unit,
): List<SettingsIntegrationAction> =
    listOf(
        SettingsIntegrationAction(
            icon = painterResource(R.drawable.fire),
            label = stringResource(R.string.music_together),
            onClick = { resetSearch(); navController.navigate("settings/music_together") },
            accentColor = MaterialTheme.colorScheme.tertiary,
        ),
    )

@Composable
fun buildSettingsGroups(
    navController: NavController,
    isAndroid12OrLater: Boolean,
    hasUpdate: Boolean,
    context: Context,
    resetSearch: () -> Unit,
): List<SettingsGroup> =
    buildList {
        add(
            SettingsGroup(
                title = stringResource(R.string.settings_section_storage),
                items = listOf(
                    SettingsItem(
                        icon = painterResource(R.drawable.storage),
                        title = stringResource(R.string.storage),
                        subtitle = stringResource(R.string.cache),
                        accentColor = MaterialTheme.colorScheme.secondary,
                        keywords = listOf("storage", "cache", "offline", "downloads", "cleanup"),
                        onClick = { resetSearch(); navController.navigate("settings/storage") },
                    ),
                ),
            ),
        )

        add(
            SettingsGroup(
                title = stringResource(R.string.settings_section_system),
                items = buildList {
                    if (isAndroid12OrLater) {
                        add(
                            SettingsItem(
                                icon = painterResource(R.drawable.link),
                                title = stringResource(R.string.default_links),
                                subtitle = stringResource(R.string.open_supported_links),
                                accentColor = MaterialTheme.colorScheme.primary,
                                keywords = listOf("links", "deeplink", "default", "supported links"),
                                onClick = {
                                    resetSearch()
                                    try {
                                        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                            Intent(
                                                Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
                                                "package:${context.packageName}".toUri()
                                            )
                                        } else {
                                            TODO("VERSION.SDK_INT < S")
                                        }
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        when (e) {
                                            is ActivityNotFoundException,
                                            is SecurityException,
                                            -> {
                                                Toast.makeText(
                                                    context,
                                                    R.string.open_app_settings_error,
                                                    Toast.LENGTH_LONG,
                                                ).show()
                                            }
                                            else -> {
                                                Toast.makeText(
                                                    context,
                                                    R.string.open_app_settings_error,
                                                    Toast.LENGTH_LONG,
                                                ).show()
                                            }
                                        }
                                    }
                                },
                            ),
                        )
                    }
                    add(
                        SettingsItem(
                            icon = painterResource(R.drawable.experiment),
                            title = stringResource(R.string.experiment_settings),
                            subtitle = stringResource(R.string.misc),
                            accentColor = MaterialTheme.colorScheme.tertiary,
                            keywords = listOf("experimental", "debug", "developer", "labs", "internal"),
                            onClick = { resetSearch(); navController.navigate("settings/misc") },
                        ),
                    )
                    add(
                        SettingsItem(
                            icon = painterResource(R.drawable.link),
                            title = "Configurar PO Token",
                            subtitle = "Token de origen obligatorio de YouTube",
                            accentColor = MaterialTheme.colorScheme.primary,
                            keywords = listOf("po_token", "youtube", "token", "origin", "fix"),
                            onClick = { resetSearch(); navController.navigate("settings/po_token") },
                        ),
                    )
                    add(
                        SettingsItem(
                            icon = painterResource(R.drawable.update),
                            title = stringResource(R.string.updates),
                            subtitle = if (hasUpdate) {
                                stringResource(R.string.new_version_available)
                            } else {
                                BuildConfig.VERSION_NAME
                            },
                            showUpdateIndicator = hasUpdate,
                            accentColor = if (hasUpdate) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            keywords = listOf("update", "version", "release", "changelog"),
                            onClick = { resetSearch(); navController.navigate("settings/update") },
                        ),
                    )
                    add(
                        SettingsItem(
                            icon = painterResource(R.drawable.info),
                            title = stringResource(R.string.about),
                            subtitle = "ARMORY",
                            accentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            keywords = listOf("about", "app info", "license", "contributors"),
                            onClick = { resetSearch(); navController.navigate("settings/about") },
                        ),
                    )
                },
            ),
        )
    }

@Composable
fun buildInternalItems(
    navController: NavController,
    resetSearch: () -> Unit,
): List<SettingsItem> =
    listOf(
        SettingsItem(
            icon = painterResource(R.drawable.fire),
            title = stringResource(R.string.music_together),
            subtitle = stringResource(R.string.integration),
            accentColor = MaterialTheme.colorScheme.tertiary,
            keywords = listOf("together", "session", "sync", "party", "join", "host"),
            onClick = { resetSearch(); navController.navigate("settings/music_together") },
        ),
    )
