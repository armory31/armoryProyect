package com.arturo254.opentune.sync

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.arturo254.opentune.App
import com.arturo254.opentune.constants.ArmoryAccountEmailKey
import com.arturo254.opentune.constants.ArmoryAccountUidKey
import com.arturo254.opentune.utils.dataStore
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.arturo254.opentune.db.MusicDatabase
import com.google.android.gms.tasks.Tasks
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArmoryAuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var _auth: FirebaseAuth? = null
    
    suspend fun getFirebaseAuth(): FirebaseAuth = withContext(Dispatchers.IO) {
        _auth ?: FirebaseAuth.getInstance().also { _auth = it }
    }

    val currentUserEmail: Flow<String?> = context.dataStore.data.map { it[ArmoryAccountEmailKey] }
    val currentUserUid: Flow<String?> = context.dataStore.data.map { it[ArmoryAccountUidKey] }
    
    val isLoggedIn: Flow<Boolean> = currentUserUid.map { it != null }

    suspend fun saveAccountInfo(email: String, uid: String) {
        context.dataStore.edit {
            it[ArmoryAccountEmailKey] = email
            it[ArmoryAccountUidKey] = uid
        }
    }

    suspend fun refreshSessionSilently(): Boolean = withContext(Dispatchers.IO) {
        try {
            val auth = getFirebaseAuth()
            val user = auth.currentUser
            if (user != null) {
                // Force token refresh
                Tasks.await(user.getIdToken(true))
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun clearAccountInfo() {
        getFirebaseAuth().signOut()
        context.dataStore.edit {
            it.remove(ArmoryAccountEmailKey)
            it.remove(ArmoryAccountUidKey)
        }
        (context.applicationContext as? App)?.database?.clearUserData()
    }
}
