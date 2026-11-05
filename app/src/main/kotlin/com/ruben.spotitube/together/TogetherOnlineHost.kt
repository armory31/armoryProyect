/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ruben.spotitube.together

import android.os.SystemClock
import androidx.compose.runtime.Immutable
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlin.math.abs

@Immutable
sealed class TogetherOnlineHostState {
    data object Idle : TogetherOnlineHostState()
    data object Connecting : TogetherOnlineHostState()
    data class Connected(
        val sessionId: String,
        val hostParticipantId: String,
    ) : TogetherOnlineHostState()
}

class TogetherOnlineHost(
    externalScope: CoroutineScope,
    val sessionId: String,
    private val code: String,
    private val hostId: String,
    private val hostDisplayName: String,
    initialSettings: TogetherRoomSettings,
) {
    private val scope = CoroutineScope(externalScope.coroutineContext + SupervisorJob())
    private val mutex = Mutex()
    private val sessionRef = TogetherFirebase.session(sessionId)
    private val guestsRef = sessionRef.child("guests")
    private val inboxRef = sessionRef.child("hostInbox")
    private var settings = initialSettings
    private var connected = false
    private var hostInboxListener: ChildEventListener? = null
    private var guestsListener: ChildEventListener? = null
    private val guests = LinkedHashMap<String, Guest>()
    @Volatile private var lastParticipants: List<TogetherParticipant> = emptyList()
    @Volatile private var lastWrittenState: TogetherRoomState? = null
    @Volatile private var lastWriteElapsedMs: Long = 0L

    private data class Guest(
        val participantId: String,
        val name: String,
        var pending: Boolean,
    )

    var onEvent: ((TogetherServerEvent) -> Unit)? = null

    fun currentParticipants(): List<TogetherParticipant> = lastParticipants

    suspend fun currentSettings(): TogetherRoomSettings = mutex.withLock { settings }

    suspend fun connect() {
        if (connected) return
        val uid = TogetherFirebase.currentUid()
        if (uid == null) {
            onEvent?.invoke(TogetherServerEvent.Error(TogetherFirebase.loginRequiredMessage()))
            return
        }

        try {
            sessionRef.child("meta").child("hostConnected").onDisconnect().setValue(false).await()
            TogetherFirebase.code(code).onDisconnect().removeValue().await()
            sessionRef.child("meta").child("hostConnected").setValue(true).await()
            connected = true
            attachHostInboxListener()
            attachGuestsListener()
        } catch (t: Throwable) {
            onEvent?.invoke(TogetherServerEvent.Error("Failed to connect to session", t))
        }
    }

    suspend fun disconnect() {
        var failure: Throwable? = null
        try {
            TogetherFirebase.code(code).removeValue().await()
        } catch (t: Throwable) {
            failure = t
        }
        try {
            sessionRef.removeValue().await()
        } catch (t: Throwable) {
            if (failure == null) failure = t else failure.addSuppressed(t)
        } finally {
            removeListeners()
            connected = false
            mutex.withLock {
                guests.clear()
                rebuildParticipantsSnapshot()
            }
            lastWrittenState = null
            lastWriteElapsedMs = 0L
        }
        failure?.let {
            onEvent?.invoke(TogetherServerEvent.Error("Failed to disconnect session", it))
            throw it
        }
    }

    suspend fun updateSettings(newSettings: TogetherRoomSettings) {
        try {
            sessionRef.child("meta").child("settings").setValue(newSettings.toFirebaseMap()).await()
            mutex.withLock { settings = newSettings }
        } catch (t: Throwable) {
            onEvent?.invoke(TogetherServerEvent.Error("Failed to update session settings", t))
            throw t
        }
    }

    suspend fun approveParticipant(participantId: String, approved: Boolean) {
        val guest = mutex.withLock {
            guests[participantId]?.takeIf { it.pending }
        } ?: return
        var sendFailure: Throwable? = null
        try {
            sendToGuest(
                participantId,
                JoinDecision(sessionId = sessionId, participantId = participantId, approved = approved),
            )
        } catch (t: Throwable) {
            sendFailure = t
        }
        if (approved) {
            sendFailure?.let { throw it }
            mutex.withLock {
                guests[participantId]?.pending = false
                rebuildParticipantsSnapshot()
            }
            onEvent?.invoke(
                TogetherServerEvent.ParticipantJoined(
                    participant(guest.copy(pending = false)),
                ),
            )
        } else {
            removeGuest(participantId, "Not approved")
            sendFailure?.let { throw it }
        }
    }

    suspend fun kickParticipant(participantId: String, reason: String?) {
        if (!hasGuest(participantId)) return
        var sendFailure: Throwable? = null
        try {
            sendToGuest(participantId, KickParticipant(sessionId, participantId, reason))
        } catch (t: Throwable) {
            sendFailure = t
        }
        removeGuest(participantId, reason ?: "Kicked")
        sendFailure?.let { throw it }
    }

    suspend fun banParticipant(participantId: String, reason: String?) {
        if (!hasGuest(participantId)) return
        var failure: Throwable? = null
        try {
            sendToGuest(participantId, BanParticipant(sessionId, participantId, reason))
        } catch (t: Throwable) {
            failure = t
        }
        try {
            sessionRef.child("bans").child(participantId).setValue(true).await()
        } catch (t: Throwable) {
            onEvent?.invoke(TogetherServerEvent.Error("Failed to ban participant", t))
            if (failure == null) failure = t else failure.addSuppressed(t)
        }
        removeGuest(participantId, reason ?: "Banned")
        failure?.let { throw it }
    }

    suspend fun broadcastRoomState(state: TogetherRoomState) {
        val snapshotSettings = mutex.withLock { settings }
        mutex.withLock { rebuildParticipantsSnapshot() }
        val roomState = state.copy(
            hostId = hostId,
            settings = snapshotSettings,
            participants = lastParticipants,
        )
        val now = SystemClock.elapsedRealtime()
        val previous = lastWrittenState
        val stateChanged = previous == null ||
            previous.copy(
                positionMs = roomState.positionMs,
                sentAtElapsedRealtimeMs = roomState.sentAtElapsedRealtimeMs,
            ) != roomState
        val expectedPosition = previous?.let {
            it.positionMs + if (it.isPlaying) (now - it.sentAtElapsedRealtimeMs).coerceAtLeast(0L) else 0L
        }
        val positionDrifted = expectedPosition == null || abs(roomState.positionMs - expectedPosition) > 1_500L
        val shouldWrite = stateChanged || positionDrifted || now - lastWriteElapsedMs >= 5_000L
        if (!shouldWrite) return

        try {
            sessionRef.child("state").setValue(
                TogetherJson.json.encodeToString(
                    TogetherMessage.serializer(),
                    RoomStateMessage(roomState),
                ),
            ).await()
            lastWrittenState = roomState
            lastWriteElapsedMs = now
        } catch (t: Throwable) {
            onEvent?.invoke(TogetherServerEvent.Error("Failed to broadcast room state", t))
        }
    }

    private fun attachHostInboxListener() {
        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                scope.launch {
                    try {
                        processHostInboxChild(snapshot)
                    } catch (t: Throwable) {
                        onEvent?.invoke(TogetherServerEvent.Error("Failed to process guest message", t))
                    } finally {
                        try {
                            snapshot.ref.removeValue().await()
                        } catch (t: Throwable) {
                            onEvent?.invoke(TogetherServerEvent.Error("Failed to remove processed guest message", t))
                        }
                    }
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) = Unit
            override fun onChildRemoved(snapshot: DataSnapshot) = Unit
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) = Unit
            override fun onCancelled(error: DatabaseError) {
                onEvent?.invoke(TogetherServerEvent.Error("Session inbox listener failed", error.toException()))
            }
        }
        hostInboxListener = listener
        inboxRef.addChildEventListener(listener)
    }

    private fun attachGuestsListener() {
        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) = Unit
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) = Unit
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) = Unit
            override fun onChildRemoved(snapshot: DataSnapshot) {
                val uid = snapshot.key ?: return
                scope.launch {
                    val removed = mutex.withLock {
                        val guest = guests.remove(uid)
                        if (guest != null) rebuildParticipantsSnapshot()
                        guest
                    }
                    if (removed != null) {
                        onEvent?.invoke(TogetherServerEvent.ParticipantLeft(uid, "Disconnected"))
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                onEvent?.invoke(TogetherServerEvent.Error("Guest listener failed", error.toException()))
            }
        }
        guestsListener = listener
        guestsRef.addChildEventListener(listener)
    }

    private suspend fun processHostInboxChild(snapshot: DataSnapshot) {
        val fromUid = snapshot.child("fromUid").getValue(String::class.java)?.takeIf { it.isNotBlank() } ?: return
        val payload = snapshot.child("msg").getValue(String::class.java) ?: return
        val message = try {
            TogetherJson.json.decodeFromString(TogetherMessage.serializer(), payload)
        } catch (t: Throwable) {
            onEvent?.invoke(TogetherServerEvent.Error("Failed to decode guest message", t))
            return
        }

        when (message) {
            is ClientHello -> {
                if (message.protocolVersion != TogetherProtocolVersion || message.sessionId != sessionId) return
                if (sessionRef.child("bans").child(fromUid).get().await().getValue(Boolean::class.java) == true) {
                    sendToGuest(fromUid, BanParticipant(sessionId, fromUid, "Banned"))
                    return
                }
                val pending = mutex.withLock { settings.requireHostApprovalToJoin }
                val guest = Guest(fromUid, message.displayName.trim().ifBlank { "Guest" }, pending)
                mutex.withLock {
                    guests[fromUid] = guest
                    rebuildParticipantsSnapshot()
                }
                val currentSettings = currentSettings()
                sendToGuest(
                    fromUid,
                    ServerWelcome(
                        protocolVersion = TogetherProtocolVersion,
                        sessionId = sessionId,
                        participantId = fromUid,
                        role = ServerRole.GUEST,
                        isPending = pending,
                        settings = currentSettings,
                    ),
                )
                onEvent?.invoke(
                    if (pending) TogetherServerEvent.JoinRequested(participant(guest))
                    else TogetherServerEvent.ParticipantJoined(participant(guest)),
                )
            }

            is ControlRequest -> {
                if (message.sessionId == sessionId && message.participantId == fromUid && guestCanSend(fromUid)) {
                    onEvent?.invoke(TogetherServerEvent.ControlRequested(message))
                }
            }

            is AddTrackRequest -> {
                if (message.sessionId == sessionId && message.participantId == fromUid && guestCanSend(fromUid)) {
                    onEvent?.invoke(TogetherServerEvent.AddTrackRequested(message))
                }
            }

            is HeartbeatPing -> {
                if (message.sessionId == sessionId && hasGuest(fromUid)) {
                    sendToGuest(
                        fromUid,
                        HeartbeatPong(
                            sessionId = sessionId,
                            pingId = message.pingId,
                            clientElapsedRealtimeMs = message.clientElapsedRealtimeMs,
                            serverElapsedRealtimeMs = SystemClock.elapsedRealtime(),
                        ),
                    )
                }
            }

            is ClientLeave -> {
                if (message.sessionId == sessionId && message.participantId == fromUid) {
                    removeGuest(fromUid, "Left")
                }
            }

            else -> Unit
        }
    }

    private suspend fun guestCanSend(uid: String): Boolean =
        mutex.withLock { guests[uid]?.pending == false }

    private suspend fun hasGuest(uid: String): Boolean = mutex.withLock { guests.containsKey(uid) }

    private suspend fun removeGuest(uid: String, reason: String) {
        val removed = mutex.withLock {
            val guest = guests.remove(uid)
            if (guest != null) rebuildParticipantsSnapshot()
            guest
        }
        if (removed != null) {
            try {
                guestsRef.child(uid).removeValue().await()
            } catch (t: Throwable) {
                onEvent?.invoke(TogetherServerEvent.Error("Failed to remove guest", t))
            }
            onEvent?.invoke(TogetherServerEvent.ParticipantLeft(uid, reason))
        }
    }

    private suspend fun sendToGuest(uid: String, message: TogetherMessage) {
        try {
            sessionRef.child("guestInbox").child(uid).push().child("msg").setValue(
                TogetherJson.json.encodeToString(TogetherMessage.serializer(), message),
            ).await()
        } catch (t: Throwable) {
            onEvent?.invoke(TogetherServerEvent.Error("Failed to send message to participant", t))
            throw t
        }
    }

    private fun participant(guest: Guest) =
        TogetherParticipant(
            id = guest.participantId,
            name = guest.name,
            isHost = false,
            isPending = guest.pending,
            isConnected = true,
        )

    private fun rebuildParticipantsSnapshot() {
        lastParticipants = buildList {
            add(
                TogetherParticipant(
                    id = hostId,
                    name = hostDisplayName,
                    isHost = true,
                    isPending = false,
                    isConnected = connected,
                ),
            )
            addAll(guests.values.sortedBy { it.name.lowercase() }.map(::participant))
        }
    }

    private fun removeListeners() {
        hostInboxListener?.let(inboxRef::removeEventListener)
        guestsListener?.let(guestsRef::removeEventListener)
        hostInboxListener = null
        guestsListener = null
    }
}
