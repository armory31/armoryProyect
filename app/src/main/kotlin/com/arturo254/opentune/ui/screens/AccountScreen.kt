/*
 * ARMORY Project - Music Player (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.arturo254.opentune.ui.screens

import android.app.Activity
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.arturo254.opentune.App.Companion.forgetAccount
import com.arturo254.opentune.LocalSyncManager
import com.arturo254.opentune.LoginActivity
import com.arturo254.opentune.R
import com.arturo254.opentune.constants.AccountNameKey
import com.arturo254.opentune.ui.component.ActionPromptDialog
import com.arturo254.opentune.ui.component.IconButton
import com.arturo254.opentune.ui.utils.backToMain
import com.arturo254.opentune.utils.SessionManager
import com.arturo254.opentune.utils.isInternetAvailable
import com.arturo254.opentune.utils.rememberPreference
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    navController: NavController,
) {
    val context = LocalContext.current
    val syncManager = LocalSyncManager.current
    val session = remember { SessionManager(context) }
    val coroutineScope = rememberCoroutineScope()
    
    val (accountNamePref) = rememberPreference(AccountNameKey, "")
    val currentUser = remember { FirebaseAuth.getInstance().currentUser }

    val accountEmail = session.getEmail().ifEmpty { currentUser?.email ?: "" }
    val accountName = session.getNombre().takeIf { it.isNotBlank() && it != "Usuario" }
        ?: accountNamePref.takeIf { it.isNotBlank() }
        ?: currentUser?.displayName?.takeIf { it.isNotBlank() }
        ?: currentUser?.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
        ?: "Usuario"
    val avatarUrl = currentUser?.photoUrl?.toString()

    var showLogoutConfirmation by rememberSaveable { mutableStateOf(false) }
    var showLogoutProgress by rememberSaveable { mutableStateOf(false) }
    var isBackupLoading by remember { mutableStateOf(false) }
    var isRestoreLoading by remember { mutableStateOf(false) }

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
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
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

            Spacer(modifier = Modifier.height(16.dp))

            // ── Nombre y Correo ──
            Text(
                text = accountName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

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

            Spacer(modifier = Modifier.height(36.dp))

            // ── Lista Vertical de Opciones ──
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            ) {
                Column {
                    // 1. ☁️ Guardar copia de seguridad en Firebase
                    AccountOptionRow(
                        icon = painterResource(R.drawable.add_circle),
                        title = "Guardar copia de seguridad en Firebase",
                        subtitle = "Subida manual inmediata a la nube",
                        isLoading = isBackupLoading,
                        onClick = {
                            coroutineScope.launch {
                                isBackupLoading = true
                                try {
                                    if (isInternetAvailable(context)) {
                                        syncManager.pushLibraryToCloud()
                                        Toast.makeText(context, "Copia de seguridad guardada con éxito", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Sin conexión a internet", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error al guardar copia", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isBackupLoading = false
                                }
                            }
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                    )

                    // 2. 🔄 Restaurar copia desde Firebase
                    AccountOptionRow(
                        icon = painterResource(R.drawable.cached),
                        title = "Restaurar copia desde Firebase",
                        subtitle = "Descarga e importación manual desde la nube",
                        isLoading = isRestoreLoading,
                        onClick = {
                            coroutineScope.launch {
                                isRestoreLoading = true
                                try {
                                    if (isInternetAvailable(context)) {
                                        val lib = syncManager.getUserLibrary()
                                        if (lib != null) {
                                            syncManager.applyLibraryToLocal(lib)
                                            Toast.makeText(context, "Biblioteca restaurada con éxito", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "No hay copias guardadas en la nube", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "Sin conexión a internet", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error al restaurar copia", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isRestoreLoading = false
                                }
                            }
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                    )

                    // 3. ⚙️ Ajustes Generales
                    AccountOptionRow(
                        icon = painterResource(R.drawable.settings),
                        title = "Ajustes Generales",
                        subtitle = "Abre la pantalla de Ajustes de la aplicación",
                        onClick = {
                            navController.navigate("settings")
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                    )

                    // 4. 🚪 Cerrar Sesión
                    AccountOptionRow(
                        icon = painterResource(R.drawable.logout),
                        title = stringResource(R.string.action_logout),
                        subtitle = "Cierra tu sesión previa copia de seguridad",
                        titleColor = MaterialTheme.colorScheme.error,
                        iconTint = MaterialTheme.colorScheme.error,
                        onClick = {
                            showLogoutConfirmation = true
                        }
                    )
                }
            }

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
