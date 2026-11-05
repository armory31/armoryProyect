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
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import java.util.concurrent.TimeUnit

sealed interface TogetherClientEvent {
    data class Welcome(val welcome: ServerWelcome) : TogetherClientEvent
    data class RoomState(val state: TogetherRoomState) : TogetherClientEvent
    data class JoinDecision(val decision: com.ruben.spotitube.together.JoinDecision) : TogetherClientEvent
    data class ServerIssue(val message: String, val code: String? = null) : TogetherClientEvent
    data class Error(val message: String, val throwable: Throwable? = null) : TogetherClientEvent
    data class HeartbeatPong(
        val pong: com.ruben.spotitube.together.HeartbeatPong,
        val receivedAtElapsedRealtimeMs: Long,
    ) : TogetherClientEvent
    data object Disconnected : TogetherClientEvent
}

@Immutable
sealed class TogetherClientState {
    data object Idle : TogetherClientState()
    data class Connecting(val joinInfo: TogetherJoinInfo) : TogetherClientState()
    data class Connected(val session: TogetherJoinInfo) : TogetherClientState()
    data class ConnectingRemote(val sessionId: String) : TogetherClientState()
    data class ConnectedRemote(val sessionId: String) : TogetherClientState()
}

class TogetherClient(
    externalScope: CoroutineScope,
    clientId: String = UUID.randomUUID().toString(),
) {
    private val client =
        HttpClient(OkHttp) {
            engine {
                config {
                    connectTimeout(15, TimeUnit.SECONDS)
                    readTimeout(30, TimeUnit.SECONDS)
                    writeTimeout(15, TimeUnit.SECONDS)
                    pingInterval(25, TimeUnit.SECONDS)
                    retryOnConnectionFailure(true)
                }
            }
            install(WebSockets) {
                pingIntervalMillis = 25_000
            }
        }

    private val scope = CoroutineScope(externalScope.coroutineContext + SupervisorJob())
    private val _state = MutableStateFlow<TogetherClientState>(TogetherClientState.Idle)
    val state = _state.asStateFlow()
    private val _events = MutableSharedFlow<TogetherClientEvent>(extraBufferCapacity = 64)
    val events = _events.asSharedFlow()

    private var session: WebSocketSession? = null
    private var loopJob: Job? = null
    private var selfParticipantId: String? = null
    private val clientId = clientId.trim().ifBlank { UUID.randomUUID().toString() }.take(64)
    private var onlineSessionId: String? = null
    private var onlineUid: String? = null
    private var onlineGuestRef: DatabaseReference? = null
    private var onlineInboxRef: DatabaseReference? = null
    private var onlineInboxListener: ChildEventListener? = null
    private var onlineStateListener: ValueEventListener? = null
    private var onlineHostConnectedListener: ValueEventListener? = null

    fun connect(joinInfo: TogetherJoinInfo, displayName: String) {
        scope.launch {
            disconnect()
            _state.value = TogetherClientState.Connecting(joinInfo)
            val urls = listOfNotNull(
                joinInfo.toWebSocketUrl(),
                alternateWebSocketSchemeOrNull(joinInfo.toWebSocketUrl()),
            ).distinct()
            var lastError: Throwable? = null
            for (candidate in urls) {
                try {
                    client.webSocket(urlString = candidate) {
                        session = this
                        val hello = ClientHello(
                            protocolVersion = TogetherProtocolVersion,
                            sessionId = joinInfo.sessionId,
                            sessionKey = joinInfo.sessionKey,
                            clientId = clientId,
                            displayName = displayName.trim(),
                        )
                        send(TogetherJson.json.encodeToString(TogetherMessage.serializer(), hello))
                        _state.value = TogetherClientState.Connected(joinInfo)
                        runLoop(this, joinInfo.sessionId)
                    }
                    return@launch
                } catch (t: Throwable) {
                    lastError = t
                }
            }
            _events.tryEmit(TogetherClientEvent.Error(connectionFailureMessage(lastError), lastError))
            _state.value = TogetherClientState.Idle
        }
    }

    fun connectOnline(sessionId: String, displayName: String) {
        scope.launch {
            disconnect()
            _state.value = TogetherClientState.ConnectingRemote(sessionId)
            val uid = TogetherFirebase.currentUid()
            if (uid == null) {
                _events.tryEmit(TogetherClientEvent.Error(TogetherFirebase.loginRequiredMessage()))
                _state.value = TogetherClientState.Idle
                return@launch
            }

            val sessionRef = TogetherFirebase.session(sessionId)
            val guestRef = sessionRef.child("guests").child(uid)
            val inboxRef = sessionRef.child("guestInbox").child(uid)
            try {
                inboxRef.removeValue().await()
                guestRef.onDisconnect().removeValue().await()
                guestRef.setValue(mapOf("displayName" to displayName.trim().ifBlank { "Guest" })).await()
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

    private fun alternateWebSocketSchemeOrNull(url: String): String? {
        val trimmed = url.trim()
        return when {
            trimmed.startsWith("ws://") -> "wss://${trimmed.removePrefix("ws://")}"
            trimmed.startsWith("wss://") -> "ws://${trimmed.removePrefix("wss://")}"
            else -> null
        }
    }

    private fun connectionFailureMessage(t: Throwable?): String {
        val root = generateSequence(t) { it.cause }.lastOrNull()
        val raw = root?.message?.trim().orEmpty()
        val reason =
            when (root) {
                is java.net.UnknownHostException -> "Server not found"
                is java.net.ConnectException -> "Connection refused"
                is java.net.SocketTimeoutException -> "Connection timed out"
                is javax.net.ssl.SSLHandshakeException -> "Secure connection failed"
                is IllegalArgumentException ->
                    if (raw.contains("ws", ignoreCase = true) && raw.contains("scheme", ignoreCase = true)) "Invalid server websocket URL" else null
                else -> null
            }
        val detail = reason ?: raw.takeIf { it.isNotBlank() }
        return if (detail == null) "Connection failed" else "Connection failed: $detail"
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
        loopJob?.cancel()
        loopJob?.cancelAndJoin()
        loopJob = null
        runCatching { session?.close(CloseReason(CloseReason.Codes.NORMAL, "Disconnect")) }
        session = null
        selfParticipantId = null
        onlineSessionId = null
        onlineUid = null
        onlineGuestRef = null
        onlineInboxRef = null
        if (activeSessionId != null && uid != null) {
            try {
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

    fun sendHeartbeat(sessionId: String, pingId: Long, clientElapsedRealtimeMs: Long) {
        scope.launch {
            sendMessage(HeartbeatPing(sessionId, pingId, clientElapsedRealtimeMs))
        }
    }

    private suspend fun sendMessage(message: TogetherMessage) {
        val sessionId = onlineSessionId
        val uid = onlineUid
        if (sessionId != null && uid != null) {
            enqueueOnlineMessage(TogetherFirebase.session(sessionId).child("hostInbox"), uid, message)
        } else {
            session?.send(TogetherJson.json.encodeToString(TogetherMessage.serializer(), message))
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
                val json = snapshot.getValue(String::class.java) ?: return
                val roomState = runCatching {
                    TogetherJson.json.decodeFromString(TogetherMessage.serializer(), json) as? RoomStateMessage
                }.getOrElse {
                    _events.tryEmit(TogetherClientEvent.Error("Failed to decode room state", it))
                    return
                } ?: return
                if (roomState.state.sessionId == onlineSessionId) {
                    _events.tryEmit(TogetherClientEvent.RoomState(roomState.state))
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
            is HeartbeatPong -> if (message.sessionId == sessionId) {
                _events.tryEmit(
                    TogetherClientEvent.HeartbeatPong(
                        message,
                        android.os.SystemClock.elapsedRealtime(),
                    ),
                )
            }
            is ServerError -> _events.tryEmit(TogetherClientEvent.ServerIssue(message.message, message.code))
            else -> Unit
        }
    }

    private fun removeOnlineListeners() {
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

    private suspend fun runLoop(session: WebSocketSession, sessionId: String) {
        loopJob =
            scope.launch {
                try {
                    while (true) {
                        val frame =
                            try {
                                session.incoming.receive()
                            } catch (_: ClosedReceiveChannelException) {
                                break
                            }
                        val text = (frame as? Frame.Text)?.readText() ?: continue
                        val message =
                            runCatching { TogetherJson.json.decodeFromString(TogetherMessage.serializer(), text) }
                                .getOrElse {
                                    _events.tryEmit(TogetherClientEvent.Error("Failed to decode message", it))
                                    continue
                                }
                        when (message) {
                            is ServerWelcome -> if (message.sessionId == sessionId) {
                                selfParticipantId = message.participantId
                                _events.tryEmit(TogetherClientEvent.Welcome(message))
                            }
                            is RoomStateMessage -> if (message.state.sessionId == sessionId) {
                                _events.tryEmit(TogetherClientEvent.RoomState(message.state))
                            }
                            is JoinDecision -> if (message.sessionId == sessionId && message.participantId == selfParticipantId) {
                                _events.tryEmit(TogetherClientEvent.JoinDecision(message))
                            }
                            is KickParticipant -> if (message.sessionId == sessionId && message.participantId == selfParticipantId) {
                                _events.tryEmit(TogetherClientEvent.Error(message.reason?.trim().orEmpty().ifBlank { "Kicked" }))
                                break
                            }
                            is BanParticipant -> if (message.sessionId == sessionId && message.participantId == selfParticipantId) {
                                _events.tryEmit(TogetherClientEvent.Error(message.reason?.trim().orEmpty().ifBlank { "Banned" }))
                                break
                            }
                            is HeartbeatPong -> if (message.sessionId == sessionId) {
                                _events.tryEmit(
                                    TogetherClientEvent.HeartbeatPong(
                                        message,
                                        android.os.SystemClock.elapsedRealtime(),
                                    ),
                                )
                            }
                            is ServerError -> _events.tryEmit(TogetherClientEvent.ServerIssue(message.message, message.code))
                            else -> Unit
                        }
                    }
                } catch (t: Throwable) {
                    _events.tryEmit(TogetherClientEvent.Error("Connection loop failed", t))
                } finally {
                    _events.tryEmit(TogetherClientEvent.Disconnected)
                    _state.value = TogetherClientState.Idle
                }
            }
        loopJob?.join()
    }
}
