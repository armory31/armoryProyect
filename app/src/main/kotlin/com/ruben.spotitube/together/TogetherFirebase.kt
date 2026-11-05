package com.ruben.spotitube.together

import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.ruben.spotitube.App
import com.ruben.spotitube.R

object TogetherFirebase {
    val db: FirebaseDatabase by lazy {
        FirebaseDatabase.getInstance(
            FirebaseApp.getInstance().options.databaseUrl
                ?: "https://armory-19193-default-rtdb.europe-west1.firebasedatabase.app",
        )
    }

    fun session(sessionId: String): DatabaseReference = db.reference.child("sessions").child(sessionId)

    fun code(code: String): DatabaseReference = db.reference.child("codes").child(code)

    fun currentUid(): String? = FirebaseAuth.getInstance().currentUser?.uid

    fun loginRequiredMessage(): String = App.instance.getString(R.string.together_firebase_login_required)
}
