package com.ruben.spotitube.utils

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("MusicPlayerSession", Context.MODE_PRIVATE)

    fun saveSession(email: String, nombre: String, token: String) {
        prefs.edit()
            .putString("email", email)
            .putString("nombre", nombre)
            .putString("token", token)
            .putBoolean("isLoggedIn", true)
            .apply()
    }

    fun isLoggedIn(): Boolean = prefs.getBoolean("isLoggedIn", false)

    fun getNombre(): String = prefs.getString("nombre", "") ?: ""

    fun saveNombre(nombre: String) {
        prefs.edit().putString("nombre", nombre).apply()
    }

    fun getAvatarUri(): String = prefs.getString("avatarUri", "") ?: ""

    fun saveAvatarUri(uri: String) {
        prefs.edit().putString("avatarUri", uri).apply()
    }

    fun getEmail(): String = prefs.getString("email", "") ?: ""

    fun getToken(): String = prefs.getString("token", "") ?: ""

    fun logout() = prefs.edit().clear().apply()
}