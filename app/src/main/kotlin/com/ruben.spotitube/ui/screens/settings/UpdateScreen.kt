/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ruben.spotitube.ui.screens.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.ruben.spotitube.ArmoryTopBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ruben.spotitube.BuildConfig
import com.ruben.spotitube.ArmoryColors
import com.ruben.spotitube.ui.LocalPlayerAwareWindowInsets
import com.ruben.spotitube.R
import com.ruben.spotitube.ui.component.PreferenceGroupTitle
import com.ruben.spotitube.utils.UpdateInfo
import com.ruben.spotitube.utils.Updater
import kotlinx.coroutines.launch

// ─── Estado de comprobación de actualización ──────────────────────────────────

private sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState
    data object Loading : UpdateCheckState
    data object UpToDate : UpdateCheckState
    data class UpdateAvailable(val info: UpdateInfo) : UpdateCheckState
    data class Error(val message: String) : UpdateCheckState
}

// ─── Pantalla ─────────────────────────────────────────────────────────────────

@Composable
fun UpdateScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val coroutineScope = rememberCoroutineScope()

    var latestVersion by remember { mutableStateOf<String?>(null) }

    var updateCheckState by remember { mutableStateOf<UpdateCheckState>(UpdateCheckState.Idle) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var pendingUpdateInfo by remember { mutableStateOf<UpdateInfo?>(null) }

    // ── Función de comprobación ───────────────────────────────────────────────
    fun checkForUpdate() {
        if (updateCheckState == UpdateCheckState.Loading) return
        coroutineScope.launch {
            updateCheckState = UpdateCheckState.Loading
            Updater.checkForUpdate(BuildConfig.VERSION_NAME, forceRefresh = true)
                .onSuccess { info ->
                    if (info == null) {
                        updateCheckState = UpdateCheckState.UpToDate
                    } else {
                        pendingUpdateInfo = info
                        updateCheckState = UpdateCheckState.UpdateAvailable(info)
                        showUpdateDialog = true
                    }
                }
                .onFailure { err ->
                    updateCheckState = UpdateCheckState.Error(err.message ?: "Error desconocido")
                }
        }
    }

    // ── Diálogos ──────────────────────────────────────────────────────────────
    if (showUpdateDialog) {
        pendingUpdateInfo?.let { info ->
            UpdateAvailableDialog(
                info = info,
                onDownload    = { showUpdateDialog = false; uriHandler.openUri(info.downloadUrl) },
                onViewRelease = { showUpdateDialog = false; uriHandler.openUri(info.releasePageUrl) },
                onDismiss     = { showUpdateDialog = false }
            )
        }
    }

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            Updater.getLatestVersionName().onSuccess { latestVersion = it }
        }
    }

    // ── Layout ────────────────────────────────────────────────────────────────
    Scaffold(
        containerColor = ArmoryColors.AbsoluteBlack,
        topBar = {
            ArmoryTopBar(
                title = stringResource(R.string.updates),
                onBack = navController::navigateUp
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                    )
                )
                .padding(horizontal = 16.dp)
        ) {

            // ── Tarjeta versión + botón comprobar ─────────────────────────────
            item {
                Spacer(Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, ArmoryColors.Gold3.copy(alpha = 0.55f)),
                    colors = CardDefaults.cardColors(
                        containerColor = ArmoryColors.SurfaceBlack,
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(ArmoryColors.Gold2.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.update),
                                    contentDescription = null,
                                    tint = ArmoryColors.Gold1,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.current_version),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ArmoryColors.TextMuted
                                )
                                Text(
                                    text = BuildConfig.VERSION_NAME,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = ArmoryColors.TextPrimary,
                                )
                                latestVersion?.let { latest ->
                                    if (Updater.isNewerVersion(latest, BuildConfig.VERSION_NAME)) {
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = stringResource(R.string.latest_version_format, latest),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = ArmoryColors.Gold1
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        // Botón con estado animado
                        UpdateCheckButton(
                            state = updateCheckState,
                            onClick = ::checkForUpdate
                        )

                        // Feedback textual bajo el botón
                        AnimatedVisibility(
                            visible = updateCheckState == UpdateCheckState.UpToDate ||
                                    updateCheckState is UpdateCheckState.Error
                        ) {
                            val (msg, color) = when (val s = updateCheckState) {
                                UpdateCheckState.UpToDate  ->
                                    "Ya tienes la versión más reciente." to ArmoryColors.SuccessGreen
                                is UpdateCheckState.Error  ->
                                    "Error: ${s.message}" to ArmoryColors.Rust1
                                else -> "" to ArmoryColors.TextPrimary
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(text = msg, style = MaterialTheme.typography.bodySmall, color = color)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // TODO: Add a GitHub import OutlinedTextField here when its import flow is implemented.

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

// ─── UpdateCheckButton ────────────────────────────────────────────────────────

@Composable
private fun UpdateCheckButton(
    state: UpdateCheckState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = state != UpdateCheckState.Loading,
        modifier = modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = when (state) {
                UpdateCheckState.UpToDate           -> ArmoryColors.SuccessGreen
                is UpdateCheckState.Error           -> ArmoryColors.Rust2
                else                                -> ArmoryColors.Gold2
            },
            contentColor = ArmoryColors.AbsoluteBlack,
        )
    ) {
        AnimatedContent(targetState = state, label = "btnContent") { s ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                when (s) {
                    UpdateCheckState.Loading -> {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = ArmoryColors.AbsoluteBlack)
                        Spacer(Modifier.width(8.dp))
                        Text("Comprobando…", color = ArmoryColors.AbsoluteBlack)
                    }
                    UpdateCheckState.UpToDate -> {
                        Icon(painterResource(R.drawable.done), null, Modifier.size(16.dp), tint = ArmoryColors.AbsoluteBlack)
                        Spacer(Modifier.width(8.dp))
                        Text("Al día", color = ArmoryColors.AbsoluteBlack)
                    }
                    is UpdateCheckState.UpdateAvailable -> {
                        Icon(painterResource(R.drawable.update), null, Modifier.size(16.dp), tint = ArmoryColors.AbsoluteBlack)
                        Spacer(Modifier.width(8.dp))
                        Text("Ver actualización (${s.info.versionName})", color = ArmoryColors.AbsoluteBlack)
                    }
                    is UpdateCheckState.Error -> {
                        Text("Reintentar", color = ArmoryColors.AbsoluteBlack)
                    }
                    else -> {
                        Icon(painterResource(R.drawable.update), null, Modifier.size(16.dp), tint = ArmoryColors.AbsoluteBlack)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.updates), color = ArmoryColors.AbsoluteBlack)
                    }
                }
            }
        }
    }
}

// ─── UpdateAvailableDialog ────────────────────────────────────────────────────

@Composable
private fun UpdateAvailableDialog(
    info: UpdateInfo,
    onDownload: () -> Unit,
    onViewRelease: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.update), null, tint = ArmoryColors.Gold1) },
        title = { Text("Nueva versión disponible") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column {
                        Text("Instalada", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(BuildConfig.VERSION_NAME, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text("Disponible", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(info.versionName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
                if (!info.releaseNotes.isNullOrBlank()) {
                    HorizontalDivider()
                    Text("Novedades", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    val notes = info.releaseNotes.let { if (it.length > 300) it.take(300) + "…" else it }
                    Text(
                        text = notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.height(120.dp).verticalScroll(rememberScrollState())
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDownload,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ArmoryColors.Gold2,
                    contentColor = ArmoryColors.AbsoluteBlack,
                ),
            ) {
                Icon(painterResource(R.drawable.download), null, Modifier.size(16.dp), tint = ArmoryColors.AbsoluteBlack)
                Spacer(Modifier.width(6.dp))
                Text("Descargar APK", color = ArmoryColors.AbsoluteBlack)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onViewRelease) { Text("Ver release") }
                TextButton(onClick = onDismiss)    { Text("Ahora no") }
            }
        }
    )
}
