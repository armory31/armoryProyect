package com.arturo254.opentune

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.arturo254.opentune.utils.SessionManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import androidx.lifecycle.lifecycleScope
import com.arturo254.opentune.sync.ArmoryAuthManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    private lateinit var session: SessionManager

    @Inject
    lateinit var armoryAuthManager: ArmoryAuthManager

    private var auth: FirebaseAuth? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        session = SessionManager(this)

        lifecycleScope.launch {
            auth = armoryAuthManager.getFirebaseAuth()
        }

        if (session.isLoggedIn()) {
             showBetaDialog()
             return
        }

        setContentView(R.layout.activity_login)

        val etEmail     = findViewById<EditText>(R.id.etEmail)
        val etPassword  = findViewById<EditText>(R.id.etPassword)
        val etNombre    = findViewById<EditText>(R.id.etNombre)
        val btnLogin    = findViewById<Button>(R.id.btnLogin)
        val btnRegister = findViewById<Button>(R.id.btnRegister)
        val tvModo      = findViewById<TextView>(R.id.tvModo)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)

        var isRegisterMode = false

        tvModo.setOnClickListener {
            isRegisterMode = !isRegisterMode
            etNombre.visibility    = if (isRegisterMode) View.VISIBLE else View.GONE
            btnLogin.visibility    = if (isRegisterMode) View.GONE    else View.VISIBLE
            btnRegister.visibility = if (isRegisterMode) View.VISIBLE else View.GONE
            tvModo.text = if (isRegisterMode)
                "¿Ya tienes cuenta? Inicia sesión"
            else
                "¿No tienes cuenta? Regístrate"
        }

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val pass  = etPassword.text.toString()
            if (!validar(email, pass)) return@setOnClickListener

            progressBar.visibility = View.VISIBLE
            btnLogin.isEnabled = false

            auth?.signInWithEmailAndPassword(email, pass)
                ?.addOnCompleteListener(this) { task ->
                    progressBar.visibility = View.GONE
                    btnLogin.isEnabled = true

                    if (task.isSuccessful) {
                        val user = auth?.currentUser
                        user?.reload()?.addOnCompleteListener {
                            if (user != null && user.isEmailVerified) {
                                lifecycleScope.launch {
                                    armoryAuthManager.saveAccountInfo(email, user.uid)
                                    session.saveSession(email, user.displayName ?: "Usuario", user.uid)
                                    // Give a small delay for Firebase Auth state to propagate
                                    kotlinx.coroutines.delay(500)
                                    showBetaDialog()
                                }
                            } else {
                                AlertDialog.Builder(this)
                                    .setTitle("✉️ Cuenta no verificada")
                                    .setMessage("Para entrar en ARMORY primero debes confirmar tu correo.\n\nBusca el mensaje que te enviamos al registrarte (mira en SPAM si no lo ves).")
                                    .setPositiveButton("Entendido", null)
                                    .show()
                                auth?.signOut()
                            }
                        }
                    } else {
                        Toast.makeText(baseContext, "Error al iniciar sesión: ${task.exception?.message}",
                            Toast.LENGTH_SHORT).show()
                    }
                }
        }

        btnRegister.setOnClickListener {
            val email  = etEmail.text.toString().trim()
            val pass   = etPassword.text.toString()
            val nombre = etNombre.text.toString().trim()
            if (!validar(email, pass, nombre)) return@setOnClickListener

            progressBar.visibility = View.VISIBLE
            btnRegister.isEnabled = false

            auth?.createUserWithEmailAndPassword(email, pass)
                ?.addOnCompleteListener(this) { task ->
                    progressBar.visibility = View.GONE
                    btnRegister.isEnabled = true

                    if (task.isSuccessful) {
                        val user = auth?.currentUser
                        val profileUpdates = UserProfileChangeRequest.Builder()
                            .setDisplayName(nombre)
                            .build()

                        user?.updateProfile(profileUpdates)?.addOnCompleteListener {
                            user.sendEmailVerification().addOnCompleteListener { verifyTask ->
                                if (verifyTask.isSuccessful) {
                                    AlertDialog.Builder(this)
                                        .setTitle("✅ ¡Correo enviado!")
                                        .setMessage("Hemos enviado un enlace de confirmación a:\n$email\n\nSi no lo recibes en unos minutos, revisa tu carpeta de CORREO NO DESEADO o SPAM.")
                                        .setPositiveButton("Vale, lo miraré") { _, _ ->
                                            isRegisterMode = false
                                            etNombre.visibility = View.GONE
                                            btnRegister.visibility = View.GONE
                                            btnLogin.visibility = View.VISIBLE
                                            tvModo.text = "¿No tienes cuenta? Regístrate"
                                            etPassword.text.clear()
                                        }
                                        .setCancelable(false)
                                        .show()
                                }
                            }
                        }
                    } else {
                        Toast.makeText(baseContext, "Error al registrar: ${task.exception?.message}",
                            Toast.LENGTH_LONG).show()
                    }
                }
        }
    }

    private fun showBetaDialog() {
        AlertDialog.Builder(this)
            .setTitle("🚀 Versión Beta")
            .setMessage("ARMORY está en fase de desarrollo y puede contener fallos. ¡Gracias por probar la aplicación!")
            .setCancelable(false)
            .setPositiveButton("Entendido") { _, _ ->
                goToMain()
            }
            .show()
    }

    private fun validar(email: String, pass: String, nombre: String = "ok"): Boolean {
        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
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