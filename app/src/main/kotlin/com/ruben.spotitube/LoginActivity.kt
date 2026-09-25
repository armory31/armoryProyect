package com.ruben.spotitube

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.ruben.spotitube.sync.ArmoryAuthManager
import com.ruben.spotitube.sync.SyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import com.ruben.spotitube.ui.theme.ArmoryTheme
import com.ruben.spotitube.utils.SessionManager
import com.ruben.spotitube.utils.isInternetAvailable
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LoginActivity : ComponentActivity() {

    private lateinit var session: SessionManager

    @Inject
    lateinit var armoryAuthManager: ArmoryAuthManager

    @Inject
    lateinit var syncManager: SyncManager

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        session = SessionManager(this)

        // PASO 4: ARRANQUE FLASH. Prioridad absoluta al salto si ya hay sesión local.
        if (session.isLoggedIn()) {
            goToMain()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        lifecycleScope.launch {
            val auth = armoryAuthManager.getFirebaseAuth()
            
            setContent {
                ArmoryTheme {
                    var showBetaDialog by remember { mutableStateOf(false) }
                    var showRestoringDialog by remember { mutableStateOf(false) }
                    
                    if (showRestoringDialog) {
                        AlertDialog(
                            onDismissRequest = { },
                            title = { Text("📦 Recuperando tu biblioteca") },
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    CircularProgressIndicator(color = ArmoryColors.Gold1)
                                    Text("Descargando tus favoritos desde la nube...")
                                }
                            },
                            confirmButton = { },
                            properties = DialogProperties(
                                dismissOnBackPress = false,
                                dismissOnClickOutside = false
                            )
                        )
                    }

                    if (showBetaDialog) {
                        AlertDialog(
                            onDismissRequest = { },
                            title = { Text("🚀 Versión Beta") },
                            text = { Text("ARMORY está en fase de desarrollo y puede contener fallos. ¡Gracias por probar la aplicación!") },
                            confirmButton = {
                                TextButton(onClick = { 
                                    showBetaDialog = false
                                    goToMain()
                                }) {
                                    Text("Entendido")
                                }
                            }
                        )
                    }

                    LoginScreen(
                        auth = auth,
                        onLoginSuccess = { email, user ->
                            lifecycleScope.launch {
                                showRestoringDialog = true
                                armoryAuthManager.saveAccountInfo(email, user.uid)
                                session.saveSession(email, user.displayName ?: "Usuario", user.uid)
                                
                                // Restauración única tras login
                                try {
                                    if (isInternetAvailable(this@LoginActivity)) {
                                        val library = syncManager.getUserLibrary()
                                        if (library != null) {
                                            syncManager.applyLibraryToLocal(library)
                                        }
                                    } else {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(this@LoginActivity, "Sin internet. La sincronización se hará en segundo plano.", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                } catch (e: Exception) {
                                    Timber.e(e, "LoginActivity: Error recuperando biblioteca")
                                }
                                
                                showRestoringDialog = false
                                delay(500)
                                showBetaDialog = true
                            }
                        }
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun LoginScreen(
        auth: FirebaseAuth,
        onLoginSuccess: (String, FirebaseUser) -> Unit
    ) {
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var name by remember { mutableStateOf("") }
        var isRegisterMode by remember { mutableStateOf(false) }
        var isLoading by remember { mutableStateOf(false) }
        var passwordVisible by remember { mutableStateOf(false) }
        
        var showVerificationDialog by remember { mutableStateOf(false) }
        var showNotVerifiedDialog by remember { mutableStateOf(false) }

        val scrollState = rememberScrollState()

        if (showVerificationDialog) {
            AlertDialog(
                onDismissRequest = { showVerificationDialog = false },
                title = { Text("✅ ¡Correo enviado!") },
                text = { Text("Hemos enviado un enlace de confirmación a:\n$email\n\nSi no lo recibes en unos minutos, revisa tu carpeta de CORREO NO DESEADO o SPAM.") },
                confirmButton = {
                    TextButton(onClick = { 
                        showVerificationDialog = false
                        isRegisterMode = false
                        password = ""
                    }) {
                        Text("Vale, lo miraré")
                    }
                }
            )
        }

        if (showNotVerifiedDialog) {
            AlertDialog(
                onDismissRequest = { showNotVerifiedDialog = false },
                title = { Text("✉️ Cuenta no verificada") },
                text = { Text("Para entrar en ARMORY primero debes confirmar tu correo.\n\nBusca el mensaje que te enviamos al registrarte (mira en SPAM si no lo ves).") },
                confirmButton = {
                    TextButton(onClick = { 
                        showNotVerifiedDialog = false
                        auth.signOut()
                    }) {
                        Text("Entendido")
                    }
                }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .verticalScroll(scrollState)
                .padding(horizontal = 32.dp, vertical = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(2.dp)
                    .background(ArmoryColors.Gold1)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "ARMORY",
                color = ArmoryColors.Gold1,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.3.sp
            )

            Spacer(modifier = Modifier.height(48.dp))

            if (isRegisterMode) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Tu nombre", color = Color(0xFF555555)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ArmoryColors.Gold1,
                        unfocusedBorderColor = Color(0xFF333333),
                        focusedTextColor = Color(0xFFE8E8E8),
                        unfocusedTextColor = Color(0xFFE8E8E8)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("tu@email.com", color = Color(0xFF555555)) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ArmoryColors.Gold1,
                    unfocusedBorderColor = Color(0xFF333333),
                    focusedTextColor = Color(0xFFE8E8E8),
                    unfocusedTextColor = Color(0xFFE8E8E8)
                ),
                shape = RoundedCornerShape(8.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Contraseña", color = Color(0xFF555555)) },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(image, contentDescription = null, tint = ArmoryColors.Gold1)
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ArmoryColors.Gold1,
                    unfocusedBorderColor = Color(0xFF333333),
                    focusedTextColor = Color(0xFFE8E8E8),
                    unfocusedTextColor = Color(0xFFE8E8E8)
                ),
                shape = RoundedCornerShape(8.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (isLoading) {
                CircularProgressIndicator(color = ArmoryColors.Gold1)
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                Button(
                    onClick = {
                        if (isRegisterMode) {
                            if (validar(email, password, name)) {
                                isLoading = true
                                auth.createUserWithEmailAndPassword(email, password)
                                    .addOnCompleteListener { task ->
                                        if (task.isSuccessful) {
                                            val user = auth.currentUser
                                            val profileUpdates = UserProfileChangeRequest.Builder()
                                                .setDisplayName(name)
                                                .build()
                                            user?.updateProfile(profileUpdates)?.addOnCompleteListener {
                                                user.sendEmailVerification().addOnCompleteListener { verifyTask ->
                                                    isLoading = false
                                                    if (verifyTask.isSuccessful) {
                                                        showVerificationDialog = true
                                                    }
                                                }
                                            }
                                        } else {
                                            isLoading = false
                                            Toast.makeText(this@LoginActivity, "Error: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                            }
                        } else {
                            if (validar(email, password)) {
                                isLoading = true
                                auth.signInWithEmailAndPassword(email, password)
                                    .addOnCompleteListener { task ->
                                        if (task.isSuccessful) {
                                            val user = auth.currentUser
                                            user?.reload()?.addOnCompleteListener {
                                                if (user != null && user.isEmailVerified) {
                                                    onLoginSuccess(email, user)
                                                } else {
                                                    isLoading = false
                                                    showNotVerifiedDialog = true
                                                }
                                            }
                                        } else {
                                            isLoading = false
                                            Toast.makeText(this@LoginActivity, "Error: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRegisterMode) Color(0xFF1E1E1E) else ArmoryColors.Gold1,
                        contentColor = if (isRegisterMode) ArmoryColors.Gold1 else Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (isRegisterMode) "CREAR CUENTA" else "ENTRAR",
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = if (isRegisterMode) "¿Ya tienes cuenta? Inicia sesión" else "¿No tienes cuenta? Regístrate",
                color = Color(0xFF6B6B6B),
                fontSize = 13.sp,
                modifier = Modifier.clickable { isRegisterMode = !isRegisterMode }
            )
        }
    }

    private fun validar(email: String, pass: String, nombre: String = "ok"): Boolean {
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Email no válido", Toast.LENGTH_SHORT).show()
            return false
        }
        if (pass.length < 6) {
            Toast.makeText(this, "Firebase exige al menos 6 caracteres", Toast.LENGTH_SHORT).show()
            return false
        }
        if (nombre.isEmpty()) {
            Toast.makeText(this, "Introduce tu nombre", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
