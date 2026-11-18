/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ruben.spotitube.together

import androidx.compose.runtime.Immutable
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

sealed interface TogetherClientEvent {
    data class Welcome(val welcome: ServerWelcome) : TogetherClientEvent
    data class RoomState(val state: TogetherRoomState) : TogetherClientEvent
    data class JoinDecision(val decision: com.ruben.spotitube.together.JoinDecision) : TogetherClientEvent
    data class ServerIssue(val message: String, val code: String? = null) : TogetherClientEvent
    data class Error(val message: String, val throwable: Throwable? = null) : TogetherClientEvent
    data object Disconnected : TogetherClientEvent
}

@Immutable
sealed class TogetherClientState {
    data object Idle : TogetherClientState()
    data class ConnectingRemote(val sessionId: String) : TogetherClientState()
    data class ConnectedRemote(val sessionId: String) : TogetherClientState()
}

class TogetherClient(
    externalScope: CoroutineScope,
    clientId: String = UUID.randomUUID().toString(),
) {
    private val scope = CoroutineScope(externalScope.coroutineContext + SupervisorJob())
    private val _state = MutableStateFlow<TogetherClientState>(TogetherClientState.Idle)
    val state = _state.asStateFlow()
    private val _events = MutableSharedFlow<TogetherClientEvent>(extraBufferCapacity = 64)
    val events = _events.asSharedFlow()

    private var selfParticipantId: String? = null
    private val clientId = clientId.trim().ifBlank { UUID.randomUUID().toString() }.take(64)
    private var onlineSessionId: String? = null
    private var onlineUid: String? = null
    private var onlineGuestRef: DatabaseReference? = null
    private var onlineInboxRef: DatabaseReference? = null
    private var onlineInboxListener: ChildEventListener? = null
    private var onlineStateListener: ValueEventListener? = null
    private var onlineHostConnectedListener: ValueEventListener? = null

    @Volatile
    private var serverTimeOffsetMs: Long = 0L
    private var offsetListener: ValueEventListener? = null

    fun serverNowMs(): Long = System.currentTimeMillis() + serverTimeOffsetMs

    fun connectOnline(sessionId: String, displayName: String, photoBase64: String = "") {
        scope.launch {
            disconnect()
            _state.value = TogetherClientState.ConnectingRemote(sessionId)
            val uid = TogetherFirebase.currentUid()
            if (uid == null) {
                _events.tryEmit(TogetherClientEvent.Error(TogetherFirebase.loginRequiredMessage()))
                _state.value = TogetherClientState.Idle
                return@launch
            }

            attachServerTimeOffsetListener()

            val sessionRef = TogetherFirebase.session(sessionId)
            val guestRef = sessionRef.child("guests").child(uid)
            val inboxRef = sessionRef.child("guestInbox").child(uid)
            try {
                inboxRef.removeValue().await()
                guestRef.onDisconnect().removeValue().await()
                sessionRef.child("profiles").child(uid).onDisconnect().removeValue().await()
                guestRef.setValue(mapOf("displayName" to displayName.trim().ifBlank { "Guest" })).await()
                if (photoBase64.isNotBlank()) {
                    sessionRef.child("profiles").child(uid).setValue(photoBase64).await()
                }
                onlineSessionId = sessionId
                onlineUid = uid
                onlineGuestRef = guestRef
                onlineInboxRef = inboxRef
                attachOnlineListeners(sessionRef, inboxRef)
                val hello = ClientHello(
                    protocolVersion = TogetherProtocolVersion,
                    sessionId = sessionId,
                    sessionKey = "",
                    clientId = clientId,
                    displayName = displayName.trim().ifBlank { "Guest" },
                )
                enqueueOnlineMessage(sessionRef.child("hostInbox"), uid, hello)
            } catch (t: Throwable) {
                _events.tryEmit(TogetherClientEvent.Error("Failed to connect to session", t))
                disconnect()
            }
        }
    }

    suspend fun disconnect() {
        val activeSessionId = onlineSessionId
        val uid = onlineUid
        val guestRef = onlineGuestRef
        if (activeSessionId != null && uid != null) {
            try {
                enqueueOnlineMessage(
                    TogetherFirebase.session(activeSessionId).child("hostInbox"),
                    uid,
                    ClientLeave(activeSessionId, uid),
                )
            } catch (t: Throwable) {
                _events.tryEmit(TogetherClientEvent.Error("Failed to leave session", t))
            }
        }
        removeOnlineListeners()
        selfParticipantId = null
        onlineSessionId = null
        onlineUid = null
        onlineGuestRef = null
        onlineInboxRef = null
        if (activeSessionId != null && uid != null) {
            try {
                runCatching { TogetherFirebase.session(activeSessionId).child("profiles").child(uid).removeValue().await() }
                guestRef?.removeValue()?.await()
            } catch (t: Throwable) {
                _events.tryEmit(TogetherClientEvent.Error("Failed to remove guest from session", t))
            }
            _events.tryEmit(TogetherClientEvent.Disconnected)
        }
        _state.value = TogetherClientState.Idle
    }

    fun requestControl(sessionId: String, action: ControlAction) {
        val participantId = selfParticipantId ?: return
        scope.launch {
            sendMessage(ControlRequest(sessionId, participantId, action))
        }
    }

    fun requestAddTrack(sessionId: String, track: TogetherTrack, mode: AddTrackMode) {
        val participantId = selfParticipantId ?: return
        scope.launch {
            sendMessage(AddTrackRequest(sessionId, participantId, track, mode))
        }
    }

    private suspend fun sendMessage(message: TogetherMessage) {
        val sessionId = onlineSessionId
        val uid = onlineUid
        if (sessionId != null && uid != null) {
            enqueueOnlineMessage(TogetherFirebase.session(sessionId).child("hostInbox"), uid, message)
        }
    }

    private suspend fun enqueueOnlineMessage(
        inbox: DatabaseReference,
        fromUid: String,
        message: TogetherMessage,
    ) {
        inbox.push().setValue(
            mapOf(
                "fromUid" to fromUid,
                "msg" to TogetherJson.json.encodeToString(TogetherMessage.serializer(), message),
            ),
        ).await()
    }

    private fun attachServerTimeOffsetListener() {
        if (offsetListener != null) return
        val offsetRef = TogetherFirebase.db.getReference(".info/serverTimeOffset")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                serverTimeOffsetMs = snapshot.getValue(Long::class.java) ?: 0L
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        offsetListener = listener
        offsetRef.addValueEventListener(listener)
    }

    private fun attachOnlineListeners(sessionRef: DatabaseReference, inboxRef: DatabaseReference) {
        val inboxListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                scope.launch {
                    try {
                        processOnlineInboxChild(snapshot)
                    } catch (t: Throwable) {
                        _events.tryEmit(TogetherClientEvent.Error("Failed to process session message", t))
                    } finally {
                        try {
                            snapshot.ref.removeValue().await()
                        } catch (t: Throwable) {
                            _events.tryEmit(TogetherClientEvent.Error("Failed to remove processed session message", t))
                        }
                    }
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) = Unit
            override fun onChildRemoved(snapshot: DataSnapshot) = Unit
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) = Unit
            override fun onCancelled(error: DatabaseError) {
                _events.tryEmit(TogetherClientEvent.Error("Session inbox listener failed", error.toException()))
            }
        }
        onlineInboxListener = inboxListener
        inboxRef.addChildEventListener(inboxListener)

        val stateListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val json = snapshot.child("json").getValue(String::class.java)
                    ?: snapshot.getValue(String::class.java) ?: return
                val ts = snapshot.child("ts").getValue(Long::class.java) ?: 0L
                val roomStateMessage = runCatching {
                    TogetherJson.json.decodeFromString(TogetherMessage.serializer(), json) as? RoomStateMessage
                }.getOrElse {
                    _events.tryEmit(TogetherClientEvent.Error("Failed to decode room state", it))
                    return
                } ?: return
                if (roomStateMessage.state.sessionId == onlineSessionId) {
                    _events.tryEmit(
                        TogetherClientEvent.RoomState(
                            roomStateMessage.state.copy(sentAtServerMs = ts)
                        )
                    )
                }
            }

            override fun onCancelled(error: DatabaseError) {
                _events.tryEmit(TogetherClientEvent.Error("Room state listener failed", error.toException()))
            }
        }
        onlineStateListener = stateListener
        sessionRef.child("state").addValueEventListener(stateListener)

        val hostConnectedListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) == true
                if (!connected && _state.value is TogetherClientState.ConnectedRemote) {
                    _events.tryEmit(TogetherClientEvent.Error("Host left"))
                    scope.launch { disconnect() }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                _events.tryEmit(TogetherClientEvent.Error("Host status listener failed", error.toException()))
            }
        }
        onlineHostConnectedListener = hostConnectedListener
        sessionRef.child("meta").child("hostConnected").addValueEventListener(hostConnectedListener)
    }

    private suspend fun processOnlineInboxChild(snapshot: DataSnapshot) {
        val messageJson = snapshot.child("msg").getValue(String::class.java) ?: return
        val message = try {
            TogetherJson.json.decodeFromString(TogetherMessage.serializer(), messageJson)
        } catch (t: Throwable) {
            _events.tryEmit(TogetherClientEvent.Error("Failed to decode message", t))
            return
        }
        val sessionId = onlineSessionId ?: return
        when (message) {
            is ServerWelcome -> if (message.sessionId == sessionId) {
                selfParticipantId = onlineUid
                _state.value = TogetherClientState.ConnectedRemote(sessionId)
                _events.tryEmit(TogetherClientEvent.Welcome(message))
            }
            is JoinDecision -> if (message.sessionId == sessionId && message.participantId == onlineUid) {
                _events.tryEmit(TogetherClientEvent.JoinDecision(message))
            }
            is KickParticipant -> if (message.sessionId == sessionId && message.participantId == onlineUid) {
                _events.tryEmit(TogetherClientEvent.Error(message.reason?.trim().orEmpty().ifBlank { "Kicked" }))
                disconnect()
            }
            is BanParticipant -> if (message.sessionId == sessionId && message.participantId == onlineUid) {
                _events.tryEmit(TogetherClientEvent.Error(message.reason?.trim().orEmpty().ifBlank { "Banned" }))
                disconnect()
            }
            is ServerError -> _events.tryEmit(TogetherClientEvent.ServerIssue(message.message, message.code))
            else -> Unit
        }
    }

    private fun removeOnlineListeners() {
        offsetListener?.let { listener ->
            TogetherFirebase.db.getReference(".info/serverTimeOffset").removeEventListener(listener)
        }
        offsetListener = null

        onlineInboxListener?.let { listener -> onlineInboxRef?.removeEventListener(listener) }
        onlineStateListener?.let { listener ->
            onlineSessionId?.let { TogetherFirebase.session(it).child("state").removeEventListener(listener) }
        }
        onlineHostConnectedListener?.let { listener ->
            onlineSessionId?.let {
                TogetherFirebase.session(it).child("meta").child("hostConnected").removeEventListener(listener)
            }
        }
        onlineInboxListener = null
        onlineStateListener = null
        onlineHostConnectedListener = null
    }
}
