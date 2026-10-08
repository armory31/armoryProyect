/*
 * ARMORY Project - Music Player (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ruben.spotitube.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.ruben.spotitube.ArmoryColors
import com.ruben.spotitube.ArmoryGradients
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import com.ruben.spotitube.ui.component.importCsvPlaylistAndFetchMetadata
import com.ruben.spotitube.viewmodels.BackupRestoreViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.ruben.spotitube.BuildConfig
import com.ruben.spotitube.App.Companion.forgetAccount
import com.ruben.spotitube.LocalDatabase
import com.ruben.spotitube.LocalSyncManager
import com.ruben.spotitube.LoginActivity
import com.ruben.spotitube.R
import com.ruben.spotitube.constants.AccountAvatarUriKey
import com.ruben.spotitube.constants.AccountNameKey
import com.ruben.spotitube.ui.component.ActionPromptDialog
import com.ruben.spotitube.ui.component.IconButton
import com.ruben.spotitube.ui.component.TextFieldDialog
import com.ruben.spotitube.ui.utils.backToMain
import com.ruben.spotitube.utils.SessionManager
import com.ruben.spotitube.utils.isInternetAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ruben.spotitube.utils.rememberPreference
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest

@Composable
fun AccountScreen(
    navController: NavController,
) {
    val context = LocalContext.current
    val syncManager = LocalSyncManager.current
    val database = LocalDatabase.current
    val session = remember { SessionManager(context) }
    val coroutineScope = rememberCoroutineScope()
    
    val (accountNamePref, onAccountNameChange) = rememberPreference(AccountNameKey, "")
    val (accountAvatarUriPref, onAccountAvatarUriChange) = rememberPreference(AccountAvatarUriKey, "")
    val currentUser = remember { FirebaseAuth.getInstance().currentUser }

    val accountEmail = session.getEmail().ifEmpty { currentUser?.email ?: "" }
    val accountName = session.getNombre().takeIf { it.isNotBlank() && it != "Usuario" }
        ?: accountNamePref.takeIf { it.isNotBlank() }
        ?: currentUser?.displayName?.takeIf { it.isNotBlank() }
        ?: currentUser?.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
        ?: "Usuario"
    val avatarUrl = accountAvatarUriPref.ifEmpty { session.getAvatarUri().ifEmpty { currentUser?.photoUrl?.toString() } }

    var showLogoutConfirmation by rememberSaveable { mutableStateOf(false) }
    var showLogoutProgress by rememberSaveable { mutableStateOf(false) }
    var showEditNameDialog by rememberSaveable { mutableStateOf(false) }
    var isBackupLoading by remember { mutableStateOf(false) }
    var isRestoreLoading by remember { mutableStateOf(false) }
    var isCsvImporting by remember { mutableStateOf(false) }
    val backupRestoreViewModel: BackupRestoreViewModel = hiltViewModel()

    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        isCsvImporting = true
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val playlistId = importCsvPlaylistAndFetchMetadata(context, uri, database, backupRestoreViewModel)
                withContext(Dispatchers.Main) {
                    isCsvImporting = false
                    if (playlistId != null) {
                        Toast.makeText(context, "Playlist CSV importada con éxito", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "No se encontraron canciones en el archivo CSV", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    isCsvImporting = false
                    Toast.makeText(context, "Error al importar CSV: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { newUri ->
            val uriString = newUri.toString()
            onAccountAvatarUriChange(uriString)
            session.saveAvatarUri(uriString)

            currentUser?.let { user ->
                coroutineScope.launch(Dispatchers.IO) {
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setPhotoUri(newUri)
                        .build()
                    user.updateProfile(profileUpdates)
                }
            }
            Toast.makeText(context, "Foto de perfil actualizada", Toast.LENGTH_SHORT).show()
        }
    }

    if (showEditNameDialog) {
        TextFieldDialog(
            icon = { Icon(painterResource(R.drawable.edit), contentDescription = null) },
            title = { Text("Editar nombre") },
            initialTextFieldValue = TextFieldValue(accountName),
            onDone = { newName ->
                showEditNameDialog = false
                val trimmedName = newName.trim()
                if (trimmedName.isNotEmpty()) {
                    onAccountNameChange(trimmedName)
                    session.saveNombre(trimmedName)
                    currentUser?.let { user ->
                        coroutineScope.launch(Dispatchers.IO) {
                            val profileUpdates = UserProfileChangeRequest.Builder()
                                .setDisplayName(trimmedName)
                                .build()
                            user.updateProfile(profileUpdates)
                        }
                    }
                    Toast.makeText(context, "Nombre actualizado", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { showEditNameDialog = false }
        )
    }

    if (showLogoutProgress) {
        AlertDialog(
            onDismissRequest = { },
            confirmButton = { },
            title = { Text("Cerrando sesión") },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text("Guardando copia de seguridad en Firebase y limpiando datos locales...")
                }
            },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        )
    }

    if (showLogoutConfirmation) {
        ActionPromptDialog(
            title = stringResource(R.string.logout_confirmation_title),
            onDismiss = { showLogoutConfirmation = false },
            onConfirm = {
                showLogoutConfirmation = false
                showLogoutProgress = true
                coroutineScope.launch {
                    val success = syncManager.handleLogout(context)
                    showLogoutProgress = false
                    if (success) {
                        session.logout()
                        forgetAccount(context)
                        (context as? Activity)?.finish()
                        context.startActivity(Intent(context, LoginActivity::class.java))
                    } else {
                        Toast.makeText(context, "Error al guardar backup. Reintenta con internet.", Toast.LENGTH_LONG).show()
                    }
                }
            },
            onCancel = { showLogoutConfirmation = false },
            content = {
                Text(
                    text = stringResource(R.string.logout_confirmation_message),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.account)) },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(
                            painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // ── Avatar Centrado Prominente (1/4 Superior) ──
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clickable { imagePickerLauncher.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                // Contenedor del avatar recortado en círculo
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!avatarUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = "Avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.account),
                            contentDescription = "Avatar",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(54.dp)
                        )
                    }
                }

                // Badge de edición
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.edit),
                        contentDescription = "Cambiar foto",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Nombre y Correo ──
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showEditNameDialog = true }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = accountName,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    painter = painterResource(R.drawable.edit),
                    contentDescription = "Editar nombre",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            if (accountEmail.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = accountEmail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Cabecera CUENTA ──
            Text(
                text = "Cuenta",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))

            // ── Recuadro CUENTA ──
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(ArmoryGradients.ObsidianGradient),
                shape = RoundedCornerShape(20.dp),
                color = Color.Transparent,
                border = BorderStroke(1.5.dp, ArmoryColors.Gold)
            ) {
                Column {
                    val accountItems = listOf(
                        AccountSettingItem(R.drawable.fire, stringResource(R.string.music_together), stringResource(R.string.integration)) { navController.navigate("settings/music_together") },
                        AccountSettingItem(
                            R.drawable.add,
                            "Importar playlist desde CSV",
                            "Crea una playlist local e importa canciones desde un archivo .csv",
                            isLoading = isCsvImporting
                        ) {
                            csvLauncher.launch("*/*")
                        },
                        AccountSettingItem(R.drawable.add_circle, "Guardar copia de seguridad en Firebase", "Subida manual inmediata a la nube", isLoading = isBackupLoading) {
                            coroutineScope.launch {
                                isBackupLoading = true
                                try {
                                    if (isInternetAvailable(context)) {
                                        syncManager.pushLibraryToCloud()
                                        Toast.makeText(context, "Copia de seguridad guardada con éxito", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Sin conexión a internet", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (_: Exception) {
                                    Toast.makeText(context, "Error al guardar copia", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isBackupLoading = false
                                }
                            }
                        },
                        AccountSettingItem(R.drawable.cached, "Restaurar copia de seguridad en Firebase", "Descarga e importación manual desde la nube", isLoading = isRestoreLoading) {
                            coroutineScope.launch {
                                isRestoreLoading = true
                                try {
                                    if (isInternetAvailable(context)) {
                                        val lib = syncManager.getUserLibrary()
                                        syncManager.applyLibraryToLocal(lib)
                                        Toast.makeText(context, "Biblioteca restaurada con éxito", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Sin conexión a internet", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, e.message ?: "Error al restaurar copia", Toast.LENGTH_LONG).show()
                                } finally {
                                    isRestoreLoading = false
                                }
                            }
                        },
                        AccountSettingItem(
                            R.drawable.logout,
                            stringResource(R.string.action_logout),
                            "Cierra tu sesión previa copia de seguridad",
                            titleColor = MaterialTheme.colorScheme.error,
                            iconTint = MaterialTheme.colorScheme.error
                        ) {
                            showLogoutConfirmation = true
                        }
                    )

                    accountItems.forEachIndexed { index, item ->
                        AccountOptionRow(
                            icon = painterResource(item.iconRes),
                            title = item.title,
                            subtitle = item.subtitle,
                            titleColor = item.titleColor ?: MaterialTheme.colorScheme.onSurface,
                            iconTint = item.iconTint ?: MaterialTheme.colorScheme.primary,
                            isLoading = item.isLoading,
                            onClick = item.onClick
                        )
                        if (index < accountItems.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 56.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Cabecera AJUSTES ──
            Text(                text = "Ajustes",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))

            // ── Recuadro AJUSTES ──
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(ArmoryGradients.ObsidianGradient),
                shape = RoundedCornerShape(20.dp),
                color = Color.Transparent,
                border = BorderStroke(1.5.dp, ArmoryColors.Silver)
            ) {
                Column {
                    val settingsItems = listOf(
                        AccountSettingItem(R.drawable.storage, stringResource(R.string.storage), stringResource(R.string.cache)) { navController.navigate("settings/storage") },
                        AccountSettingItem(R.drawable.link, "Configurar PO Token", "Token de origen de YouTube") { navController.navigate("settings/po_token") },
                        AccountSettingItem(R.drawable.experiment, stringResource(R.string.experiment_settings), stringResource(R.string.misc)) { navController.navigate("settings/misc") },
                        AccountSettingItem(R.drawable.update, stringResource(R.string.updates), BuildConfig.VERSION_NAME) { navController.navigate("settings/update") },
                        AccountSettingItem(R.drawable.info, stringResource(R.string.about), "ARMORY") { navController.navigate("settings/about") },
                    )

                    settingsItems.forEachIndexed { index, item ->
                        AccountOptionRow(
                            icon = painterResource(item.iconRes),
                            title = item.title,
                            subtitle = item.subtitle,
                            titleColor = item.titleColor ?: MaterialTheme.colorScheme.onSurface,
                            iconTint = item.iconTint ?: MaterialTheme.colorScheme.primary,
                            isLoading = item.isLoading,
                            onClick = item.onClick
                        )
                        if (index < settingsItems.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 56.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun AccountOptionRow(
    icon: Painter,
    title: String,
    subtitle: String,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    isLoading: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isLoading, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = iconTint,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = titleColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Icon(
            painter = painterResource(R.drawable.arrow_forward),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp)
        )
    }
}

private data class AccountSettingItem(
    val iconRes: Int,
    val title: String,
    val subtitle: String,
    val titleColor: Color? = null,
    val iconTint: Color? = null,
    val isLoading: Boolean = false,
    val onClick: () -> Unit
)
