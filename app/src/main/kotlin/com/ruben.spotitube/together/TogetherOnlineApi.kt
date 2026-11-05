/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ruben.spotitube.together

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.MutableData
import com.google.firebase.database.ServerValue
import com.google.firebase.database.Transaction
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import java.util.UUID
import kotlin.coroutines.resume

data class TogetherOnlineCreateSessionResponse(
    val sessionId: String,
    val code: String,
    val settings: TogetherRoomSettings,
)

data class TogetherOnlineResolveResponse(
    val sessionId: String,
    val settings: TogetherRoomSettings,
)

class TogetherOnlineApiException(
    message: String,
    val statusCode: Int? = null,
    cause: Throwable? = null,
) : Exception(message, cause)

class TogetherOnlineApi {
    suspend fun createSession(
        hostDisplayName: String,
        settings: TogetherRoomSettings,
    ): TogetherOnlineCreateSessionResponse {
        val hostUid = TogetherFirebase.currentUid()
            ?: throw TogetherOnlineApiException(TogetherFirebase.loginRequiredMessage())
        val sessionId = UUID.randomUUID().toString()
        var reservedCode: String? = null

        for (attempt in 0 until 15) {
            val candidate = (100_000..999_999).random().toString()
            try {
                if (reserveCode(candidate, sessionId)) {
                    reservedCode = candidate
                    break
                }
            } catch (t: Throwable) {
                throw TogetherOnlineApiException("Failed to reserve session code", cause = t)
            }
        }

        val code = reservedCode
            ?: throw TogetherOnlineApiException("Could not reserve a session code after 15 attempts")
        try {
            TogetherFirebase.session(sessionId).child("meta").setValue(
                mapOf(
                    "code" to code,
                    "hostUid" to hostUid,
                    "hostName" to hostDisplayName.trim(),
                    "settings" to settings.toFirebaseMap(),
                    "createdAt" to ServerValue.TIMESTAMP,
                    "hostConnected" to false,
                ),
            ).await()
        } catch (t: Throwable) {
            try {
                TogetherFirebase.code(code).removeValue().await()
            } catch (cleanupError: Throwable) {
                t.addSuppressed(cleanupError)
            }
            throw TogetherOnlineApiException("Failed to create session", cause = t)
        }

        return TogetherOnlineCreateSessionResponse(sessionId, code, settings)
    }

    suspend fun resolveCode(code: String): TogetherOnlineResolveResponse {
        if (TogetherFirebase.currentUid() == null) {
            throw TogetherOnlineApiException(TogetherFirebase.loginRequiredMessage())
        }
        val sessionId = try {
            TogetherFirebase.code(code.trim()).get().await()
                .child("sessionId").getValue(String::class.java)
        } catch (t: Throwable) {
            val denied = t.causes().any {
                it.message?.contains("permission denied", ignoreCase = true) == true
            }
            throw TogetherOnlineApiException(
                if (denied) "Session not found" else "Failed to resolve session code",
                statusCode = if (denied) 404 else null,
                cause = t,
            )
        }
        if (sessionId.isNullOrBlank()) {
            throw TogetherOnlineApiException("Session not found", statusCode = 404)
        }

        return try {
            val meta = TogetherFirebase.session(sessionId).child("meta").get().await()
            if (!meta.exists()) throw TogetherOnlineApiException("Session not found", statusCode = 404)
            TogetherOnlineResolveResponse(sessionId, meta.child("settings").toTogetherRoomSettings())
        } catch (t: Throwable) {
            if (t is TogetherOnlineApiException) throw t
            val denied = t.causes().any {
                it.message?.contains("permission denied", ignoreCase = true) == true
            }
            throw TogetherOnlineApiException(
                "Session not found",
                statusCode = if (denied) 404 else null,
                cause = t,
            )
        }
    }

    private suspend fun reserveCode(code: String, sessionId: String): Boolean =
        suspendCancellableCoroutine { continuation ->
            TogetherFirebase.code(code).runTransaction(
                object : Transaction.Handler {
                    override fun doTransaction(currentData: MutableData): Transaction.Result {
                        if (currentData.value != null) return Transaction.abort()
                        currentData.value =
                            mapOf(
                                "sessionId" to sessionId,
                                "createdAt" to ServerValue.TIMESTAMP,
                            )
                        return Transaction.success(currentData)
                    }

                    override fun onComplete(
                        error: DatabaseError?,
                        committed: Boolean,
                        currentData: DataSnapshot?,
                    ) {
                        if (!continuation.isActive) return
                        if (error != null) continuation.resumeWith(Result.failure(error.toException()))
                        else continuation.resume(committed)
                    }
                },
                false,
            )
        }
}

internal fun TogetherRoomSettings.toFirebaseMap(): Map<String, Boolean> =
    mapOf(
        "allowGuestsToAddTracks" to allowGuestsToAddTracks,
        "allowGuestsToControlPlayback" to allowGuestsToControlPlayback,
        "requireHostApprovalToJoin" to requireHostApprovalToJoin,
    )

internal fun DataSnapshot.toTogetherRoomSettings(): TogetherRoomSettings =
    TogetherRoomSettings(
        allowGuestsToAddTracks = child("allowGuestsToAddTracks").getValue(Boolean::class.java) ?: true,
        allowGuestsToControlPlayback = child("allowGuestsToControlPlayback").getValue(Boolean::class.java) ?: false,
        requireHostApprovalToJoin = child("requireHostApprovalToJoin").getValue(Boolean::class.java) ?: false,
    )

private fun Throwable.causes(): Sequence<Throwable> = generateSequence(this) { it.cause }
