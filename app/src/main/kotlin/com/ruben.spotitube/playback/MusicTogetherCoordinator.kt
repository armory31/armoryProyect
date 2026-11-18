package com.ruben.spotitube.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.ruben.spotitube.R
import com.ruben.spotitube.extensions.SilentHandler
import com.ruben.spotitube.extensions.mediaItems
import com.ruben.spotitube.extensions.metadata
import com.ruben.spotitube.extensions.toMediaItem
import com.ruben.spotitube.models.MediaMetadata
import com.ruben.spotitube.playback.queues.ListQueue
import com.ruben.spotitube.playback.queues.Queue
import com.ruben.spotitube.together.AddTrackMode
import com.ruben.spotitube.together.ControlAction
import com.ruben.spotitube.together.TogetherClient
import com.ruben.spotitube.together.TogetherClientEvent
import com.ruben.spotitube.together.TogetherFirebase
import com.ruben.spotitube.together.TogetherOnlineApi
import com.ruben.spotitube.together.TogetherOnlineApiException
import com.ruben.spotitube.together.TogetherOnlineHost
import com.ruben.spotitube.together.TogetherParticipant
import com.ruben.spotitube.together.TogetherRole
import com.ruben.spotitube.together.TogetherRoomSettings
import com.ruben.spotitube.together.TogetherRoomState
import com.ruben.spotitube.together.TogetherServerEvent
import com.ruben.spotitube.together.TogetherSessionState
import com.ruben.spotitube.together.TogetherTrack
import com.ruben.spotitube.utils.md5
import com.ruben.spotitube.utils.reportException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.time.Duration.Companion.milliseconds

class MusicTogetherCoordinator(
    private val context: Context,
    private val player: () -> ExoPlayer,
    private val scope: () -> CoroutineScope,
    private val ioScope: () -> CoroutineScope,
    private val ensureScopesActive: () -> Unit,
    private val playNext: (List<MediaItem>) -> Unit,
    private val addToQueue: (List<MediaItem>) -> Unit,
    private val onRemoteQueueReplaced: (Queue) -> Unit,
) {
    val togetherSessionState = MutableStateFlow<TogetherSessionState>(TogetherSessionState.Idle)
    val togetherAvatars = MutableStateFlow<Map<String, String>>(emptyMap())
    private var avatarsRef: DatabaseReference? = null
    private var avatarsListener: ValueEventListener? = null
    private var togetherOnlineHost: TogetherOnlineHost? = null
    private var togetherClient: TogetherClient? = null
    private var togetherBroadcastJob: Job? = null
    private var togetherOnlineConnectJob: Job? = null
    private var togetherClientEventsJob: Job? = null
    private var togetherSelfParticipantId: String? = null
    private var togetherLastAppliedQueueHash: String? = null

    @Volatile
    var isTogetherApplyingRemote: Boolean = false

    @Volatile
    var togetherSuppressEchoUntilElapsedMs: Long = 0L

    @Volatile
    private var togetherLastAppliedRoomStateSentAtServerMs: Long = 0L

    @Volatile
    var togetherLastRemoteAppliedPlayWhenReady: Boolean? = null

    @Volatile
    var togetherLastRemoteAppliedIndex: Int = -1

    @Volatile
    private var togetherLastSentControlAtElapsedMs: Long = 0L

    @Volatile
    private var togetherLastSentControlAction: ControlAction? = null

    @Volatile
    private var togetherPendingGuestControl: TogetherPendingGuestControl? = null

    private val togetherHostId: String = "host"
    private var lastTogetherNoticeAtElapsedMs: Long = 0L
    private var lastTogetherNoticeKey: String? = null

    fun isTogetherGuestSession(): Boolean {
        val joined = togetherSessionState.value as? TogetherSessionState.Joined
        return joined?.role is TogetherRole.Guest
    }

    private data class TogetherPendingGuestControl(
        val desiredIsPlaying: Boolean? = null,
        val desiredIndex: Int? = null,
        val desiredTrackId: String? = null,
        val requestedAtElapsedMs: Long,
        val expiresAtElapsedMs: Long,
    )

    fun showTogetherNotice(message: String, key: String? = null) {
        val now = android.os.SystemClock.elapsedRealtime()
        val normalizedKey = key ?: message
        if (normalizedKey == lastTogetherNoticeKey && now - lastTogetherNoticeAtElapsedMs < 1200L) return
        lastTogetherNoticeKey = normalizedKey
        lastTogetherNoticeAtElapsedMs = now
        scope().launch(SilentHandler) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun togetherOnlineErrorMessage(t: Throwable): String {
        if (t is TogetherOnlineApiException) {
            val code = t.statusCode
            return when {
                code == 404 -> context.getString(R.string.together_session_not_found)
                code != null && code in 500..599 -> context.getString(R.string.together_server_error)
                else -> t.message ?: context.getString(R.string.network_unavailable)
            }
        }
        val root = generateSequence(t) { it.cause }.lastOrNull() ?: t
        return when (root) {
            is UnknownHostException -> context.getString(R.string.together_server_unreachable)
            is ConnectException -> context.getString(R.string.together_server_unreachable)
            is SocketTimeoutException -> context.getString(R.string.together_connection_timed_out)
            is javax.net.ssl.SSLHandshakeException -> context.getString(R.string.together_server_unreachable)
            else -> context.getString(R.string.network_unavailable)
        }
    }

    private fun buildAvatarThumbnailBase64(avatarUri: String): String {
        return runCatching {
            if (avatarUri.isBlank()) return ""
            val path = Uri.parse(avatarUri).path ?: return ""
            val file = java.io.File(path)
            if (!file.exists()) return ""
            val bitmap = BitmapFactory.decodeFile(path) ?: return ""
            val scaled = Bitmap.createScaledBitmap(bitmap, 96, 96, true)
            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 70, baos)
            Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        }.getOrDefault("")
    }

    private fun startAvatarsListener(sessionId: String) {
        stopAvatarsListener()
        val ref = TogetherFirebase.session(sessionId).child("profiles")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                togetherAvatars.value = snapshot.children.mapNotNull { c ->
                    val k = c.key ?: return@mapNotNull null
                    val v = c.getValue(String::class.java).orEmpty()
                    if (v.isBlank()) null else k to v
                }.toMap()
            }

            override fun onCancelled(error: DatabaseError) {
                Timber.tag("Together").w(error.toException(), "avatars listener cancelled")
            }
        }
        avatarsRef = ref
        avatarsListener = listener
        ref.addValueEventListener(listener)
    }

    private fun stopAvatarsListener() {
        val ref = avatarsRef
        val listener = avatarsListener
        if (ref != null && listener != null) {
            ref.removeEventListener(listener)
        }
        avatarsRef = null
        avatarsListener = null
        togetherAvatars.value = emptyMap()
    }

    fun startTogetherOnlineHost(
        displayName: String,
        settings: TogetherRoomSettings,
        avatarUri: String = "",
    ) {
        ensureScopesActive()
        scope().launch(SilentHandler) {
            togetherSessionState.value = TogetherSessionState.Idle
        }

        ioScope().launch(SilentHandler) {
            stopTogetherInternal()

            val api = TogetherOnlineApi()
            val hostName = displayName.trim().ifBlank { context.getString(R.string.app_name) }

            val created =
                runCatching {
                    api.createSession(
                        hostDisplayName = hostName,
                        settings = settings,
                    )
                }.getOrElse { t ->
                    scope().launch(SilentHandler) {
                        togetherSessionState.value =
                            TogetherSessionState.Error(
                                message = togetherOnlineErrorMessage(t),
                                recoverable = true,
                            )
                    }
                    reportException(t)
                    return@launch
                }

            val onlineHost =
                TogetherOnlineHost(
                    externalScope = ioScope(),
                    sessionId = created.sessionId,
                    code = created.code,
                    hostId = togetherHostId,
                    hostDisplayName = hostName,
                    initialSettings = created.settings,
                )

            onlineHost.onEvent = { event ->
                ioScope().launch(SilentHandler) {
                    handleTogetherHostEvent(event) { onlineHost.currentSettings() }
                }
            }

            togetherOnlineHost = onlineHost
            startAvatarsListener(created.sessionId)

            scope().launch(SilentHandler) {
                togetherSessionState.value =
                    TogetherSessionState.HostingOnline(
                        sessionId = created.sessionId,
                        code = created.code,
                        settings = created.settings,
                        roomState = null,
                    )
            }

            togetherOnlineConnectJob?.cancel()
            togetherOnlineConnectJob =
                ioScope().launch(SilentHandler) {
                    onlineHost.connect()
                    onlineHost.publishProfile(buildAvatarThumbnailBase64(avatarUri))
                }

            togetherBroadcastJob =
                ioScope().launch(SilentHandler) {
                    while (togetherOnlineHost === onlineHost) {
                        val state =
                            buildTogetherRoomState(
                                sessionId = created.sessionId,
                                hostId = togetherHostId,
                            )
                        onlineHost.broadcastRoomState(state)
                        scope().launch(SilentHandler) {
                            val hosting =
                                togetherSessionState.value as? TogetherSessionState.HostingOnline
                            if (hosting?.sessionId == created.sessionId) {
                                val currentSettings = onlineHost.currentSettings()
                                togetherSessionState.value =
                                    hosting.copy(
                                        settings = currentSettings,
                                        roomState =
                                            state.copy(
                                                participants = onlineHost.currentParticipants(),
                                                settings = currentSettings,
                                            ),
                                    )
                            }
                        }
                        delay(750.milliseconds)
                    }
                }
        }
    }

    fun joinTogetherOnline(
        code: String,
        displayName: String,
        avatarUri: String = "",
    ) {
        ensureScopesActive()
        val trimmedCode = code.trim()
        if (trimmedCode.isBlank()) {
            scope().launch(SilentHandler) {
                togetherSessionState.value =
                    TogetherSessionState.Error(
                        message = context.getString(R.string.invalid_code),
                        recoverable = true,
                    )
            }
            return
        }

        scope().launch(SilentHandler) {
            togetherSessionState.value = TogetherSessionState.JoiningOnline(trimmedCode)
        }

        ioScope().launch(SilentHandler) {
            stopTogetherInternal()

            val api = TogetherOnlineApi()
            val resolved =
                runCatching { api.resolveCode(trimmedCode) }
                    .getOrElse { t ->
                        scope().launch(SilentHandler) {
                            togetherSessionState.value =
                                TogetherSessionState.Error(
                                    message = togetherOnlineErrorMessage(t),
                                    recoverable = true,
                                )
                        }
                        reportException(t)
                        return@launch
                    }

            val client = TogetherClient(ioScope())
            togetherClient = client
            togetherSelfParticipantId = null
            togetherLastAppliedQueueHash = null

            togetherClientEventsJob?.cancel()
            togetherClientEventsJob =
                ioScope().launch(SilentHandler) {
                    client.events.collect { event ->
                        when (event) {
                            is TogetherClientEvent.Welcome -> {
                                togetherSelfParticipantId = event.welcome.participantId
                                scope().launch(SilentHandler) {
                                    val state = togetherSessionState.value
                                    if (state is TogetherSessionState.JoiningOnline) {
                                        val selfName = displayName.trim().ifBlank { context.getString(R.string.together_role_guest) }
                                        val initial =
                                            TogetherRoomState(
                                                sessionId = resolved.sessionId,
                                                hostId = togetherHostId,
                                                participants =
                                                    listOf(
                                                        TogetherParticipant(
                                                            id = event.welcome.participantId,
                                                            name = selfName,
                                                            isHost = false,
                                                            isPending = event.welcome.isPending,
                                                            isConnected = true,
                                                        ),
                                                    ),
                                                settings = event.welcome.settings,
                                                queue = emptyList(),
                                                queueHash = "",
                                                currentIndex = 0,
                                                isPlaying = false,
                                                positionMs = 0L,
                                                repeatMode = 0,
                                                shuffleEnabled = false,
                                                sentAtServerMs = 0L,
                                            )
                                        togetherSessionState.value =
                                            TogetherSessionState.Joined(
                                                role = TogetherRole.Guest,
                                                sessionId = resolved.sessionId,
                                                selfParticipantId = event.welcome.participantId,
                                                roomState = initial,
                                            )
                                    }
                                }
                            }

                            is TogetherClientEvent.RoomState -> {
                                applyRemoteRoomState(event.state)
                            }

                            is TogetherClientEvent.JoinDecision -> {
                                if (!event.decision.approved) {
                                    scope().launch(SilentHandler) {
                                        togetherSessionState.value =
                                            TogetherSessionState.Error(
                                                message = context.getString(R.string.not_allowed),
                                                recoverable = true,
                                            )
                                    }
                                    ioScope().launch(SilentHandler) { stopTogetherInternal() }
                                }
                            }

                            is TogetherClientEvent.ServerIssue -> {
                                Timber.tag("Together").w("server issue (online) code=${event.code.orEmpty()} message=${event.message}")
                                when (event.code) {
                                    "GUEST_CONTROL_DISABLED" -> {
                                        showTogetherNotice(event.message, key = "GUEST_CONTROL_DISABLED")
                                        val joined =
                                            togetherSessionState.value as? TogetherSessionState.Joined
                                        if (joined?.role is TogetherRole.Guest) {
                                            togetherPendingGuestControl = null
                                            togetherLastSentControlAction = null
                                            scope().launch(SilentHandler) { applyRemoteRoomState(joined.roomState) }
                                        }
                                    }

                                    "GUEST_ADD_DISABLED" -> {
                                        showTogetherNotice(event.message, key = "GUEST_ADD_DISABLED")
                                    }

                                    "HOST_OFFLINE" -> {
                                        showTogetherNotice(event.message, key = "HOST_OFFLINE")
                                    }

                                    else -> {
                                        scope().launch(SilentHandler) {
                                            togetherSessionState.value =
                                                TogetherSessionState.Error(
                                                    message = event.message,
                                                    recoverable = true,
                                                )
                                        }
                                        ioScope().launch(SilentHandler) { stopTogetherInternal() }
                                    }
                                }
                            }

                            is TogetherClientEvent.Error -> {
                                scope().launch(SilentHandler) {
                                    togetherSessionState.value =
                                        TogetherSessionState.Error(
                                            message = event.message,
                                            recoverable = true,
                                        )
                                }
                                ioScope().launch(SilentHandler) { stopTogetherInternal() }
                            }

                            TogetherClientEvent.Disconnected -> {
                                val current = togetherSessionState.value
                                if (current is TogetherSessionState.Idle) return@collect
                                scope().launch(SilentHandler) {
                                    val currentState = togetherSessionState.value
                                    togetherSessionState.value =
                                        TogetherSessionState.Error(
                                            message =
                                                if (currentState is TogetherSessionState.Joined &&
                                                    currentState.role is TogetherRole.Guest
                                                ) {
                                                    context.getString(R.string.together_host_left_session)
                                                } else {
                                                    context.getString(R.string.network_unavailable)
                                                },
                                            recoverable = true,
                                        )
                                }
                                ioScope().launch(SilentHandler) { stopTogetherInternal() }
                            }
                        }
                    }
                }

            startAvatarsListener(resolved.sessionId)
            client.connectOnline(
                sessionId = resolved.sessionId,
                displayName = displayName.trim().ifBlank { context.getString(R.string.together_role_guest) },
                photoBase64 = buildAvatarThumbnailBase64(avatarUri),
            )
        }
    }

    fun leaveTogether() {
        ensureScopesActive()
        scope().launch(SilentHandler) {
            togetherSessionState.value = TogetherSessionState.Idle
        }
        ioScope().launch(SilentHandler) { stopTogetherInternal() }
    }

    fun updateTogetherSettings(settings: TogetherRoomSettings) {
        val onlineHost = togetherOnlineHost ?: return
        ioScope().launch(SilentHandler) {
            onlineHost.updateSettings(settings)
        }
    }

    fun approveTogetherParticipant(participantId: String, approved: Boolean) {
        val onlineHost = togetherOnlineHost ?: return
        ioScope().launch(SilentHandler) {
            onlineHost.approveParticipant(participantId, approved)
        }
    }

    fun kickTogetherParticipant(participantId: String, reason: String? = null) {
        val onlineHost = togetherOnlineHost ?: return
        ioScope().launch(SilentHandler) {
            onlineHost.kickParticipant(participantId, reason)
        }
    }

    fun banTogetherParticipant(participantId: String, reason: String? = null) {
        val onlineHost = togetherOnlineHost ?: return
        ioScope().launch(SilentHandler) {
            onlineHost.banParticipant(participantId, reason)
        }
    }

    fun requestTogetherControl(action: ControlAction) {
        val client =
            togetherClient ?: run {
                showTogetherNotice(context.getString(R.string.network_unavailable), key = "TOGETHER_CLIENT_MISSING")
                return
            }
        val state = togetherSessionState.value as? TogetherSessionState.Joined ?: return
        if (state.role !is TogetherRole.Guest) return
        if (!state.roomState.settings.allowGuestsToControlPlayback) {
            Timber.tag("Together").i("control blocked locally (disabled) action=${action::class.java.simpleName}")
            showTogetherNotice(context.getString(R.string.not_allowed), key = "GUEST_CONTROL_DISABLED_LOCAL")
            return
        }
        val now = android.os.SystemClock.elapsedRealtime()
        val lastAction = togetherLastSentControlAction
        val lastAt = togetherLastSentControlAtElapsedMs
        if (lastAction == action && now - lastAt < 350L) return
        togetherLastSentControlAction = action
        togetherLastSentControlAtElapsedMs = now

        val timeout = 5000L
        togetherPendingGuestControl =
            when (action) {
                ControlAction.Play ->
                    TogetherPendingGuestControl(desiredIsPlaying = true, requestedAtElapsedMs = now, expiresAtElapsedMs = now + timeout)
                ControlAction.Pause ->
                    TogetherPendingGuestControl(desiredIsPlaying = false, requestedAtElapsedMs = now, expiresAtElapsedMs = now + timeout)
                is ControlAction.SeekToIndex ->
                    TogetherPendingGuestControl(desiredIndex = action.index.coerceAtLeast(0), requestedAtElapsedMs = now, expiresAtElapsedMs = now + timeout)
                is ControlAction.SeekToTrack ->
                    TogetherPendingGuestControl(
                        desiredTrackId = action.trackId.trim().ifBlank { null },
                        requestedAtElapsedMs = now,
                        expiresAtElapsedMs = now + timeout,
                    )
                else -> togetherPendingGuestControl
            }
        client.requestControl(state.sessionId, action)
    }

    fun requestTogetherAddTrack(
        track: TogetherTrack,
        mode: AddTrackMode,
    ) {
        val client = togetherClient ?: return
        val state = togetherSessionState.value as? TogetherSessionState.Joined ?: return
        if (state.role !is TogetherRole.Guest) return
        if (!state.roomState.settings.allowGuestsToAddTracks) {
            Timber.tag("Together").i("add blocked locally (disabled) mode=$mode trackId=${track.id}")
            showTogetherNotice(context.getString(R.string.not_allowed), key = "GUEST_ADD_DISABLED_LOCAL")
            return
        }
        client.requestAddTrack(state.sessionId, track, mode)
    }

    private suspend fun handleTogetherHostEvent(
        event: TogetherServerEvent,
        currentSettings: suspend () -> TogetherRoomSettings,
    ) {
        when (event) {
            is TogetherServerEvent.ControlRequested -> {
                val settings = currentSettings()
                if (!settings.allowGuestsToControlPlayback) return
                applyHostControl(event.request.action)
            }

            is TogetherServerEvent.AddTrackRequested -> {
                val settings = currentSettings()
                if (!settings.allowGuestsToAddTracks) return
                applyHostAddTrack(event.request.track, event.request.mode)
            }

            is TogetherServerEvent.Error -> {
                val current = togetherSessionState.value
                if (current is TogetherSessionState.Idle) return
                togetherSessionState.value =
                    TogetherSessionState.Error(
                        message = event.message,
                        recoverable = true,
                    )
                ioScope().launch(SilentHandler) { stopTogetherInternal() }
            }

            else -> Unit
        }
    }

    private suspend fun applyHostControl(action: ControlAction) {
        val p = player()
        withContext(Dispatchers.Main) {
            when (action) {
                ControlAction.Play -> {
                    if (!p.playWhenReady) {
                        p.prepare()
                        p.playWhenReady = true
                    }
                }

                ControlAction.Pause -> {
                    if (p.playWhenReady) {
                        p.playWhenReady = false
                    }
                }

                is ControlAction.SeekTo -> {
                    p.seekTo(action.positionMs.coerceAtLeast(0L))
                    p.prepare()
                }

                ControlAction.SkipNext -> {
                    if (p.hasNextMediaItem()) {
                        p.seekToNext()
                        p.prepare()
                        p.playWhenReady = true
                    }
                }

                ControlAction.SkipPrevious -> {
                    if (p.hasPreviousMediaItem()) {
                        p.seekToPrevious()
                        p.prepare()
                        p.playWhenReady = true
                    }
                }

                is ControlAction.SeekToTrack -> {
                    val trackId = action.trackId.trim()
                    if (trackId.isNotBlank()) {
                        val idx =
                            p.mediaItems.indexOfFirst {
                                val metaId = it.metadata?.id
                                it.mediaId == trackId || metaId == trackId
                            }
                        if (idx >= 0 && idx < p.mediaItemCount) {
                            p.seekTo(idx, action.positionMs.coerceAtLeast(0L))
                            p.prepare()
                        }
                    }
                }

                is ControlAction.SeekToIndex -> {
                    val idx = action.index.coerceAtLeast(0)
                    if (idx < p.mediaItemCount) {
                        p.seekTo(idx, action.positionMs.coerceAtLeast(0L))
                        p.prepare()
                    }
                }

                is ControlAction.SetRepeatMode -> {
                    if (p.repeatMode != action.repeatMode) {
                        p.repeatMode = action.repeatMode
                    }
                }

                is ControlAction.SetShuffleEnabled -> {
                    if (p.shuffleModeEnabled != action.shuffleEnabled) {
                        p.shuffleModeEnabled = action.shuffleEnabled
                    }
                }
            }
        }
    }

    private suspend fun applyHostAddTrack(
        track: TogetherTrack,
        mode: AddTrackMode,
    ) {
        val mediaItem = track.toMediaMetadata().toMediaItem()
        withContext(Dispatchers.Main) {
            when (mode) {
                AddTrackMode.PLAY_NEXT -> playNext(listOf(mediaItem))
                AddTrackMode.ADD_TO_QUEUE -> addToQueue(listOf(mediaItem))
            }
        }
    }

    private suspend fun buildTogetherRoomState(
        sessionId: String,
        hostId: String,
    ): TogetherRoomState {
        val p = player()
        return withContext(Dispatchers.Main) {
            val tracks =
                p.mediaItems.mapNotNull { it.metadata }.map { meta ->
                    TogetherTrack(
                        id = meta.id,
                        title = meta.title,
                        artists = meta.artists.map { it.name },
                        durationSec = meta.duration,
                        thumbnailUrl = meta.thumbnailUrl,
                    )
                }

            val queueHash = md5(tracks.joinToString(separator = "|") { it.id })

            TogetherRoomState(
                sessionId = sessionId,
                hostId = hostId,
                settings = TogetherRoomSettings(),
                participants = emptyList(),
                queue = tracks,
                queueHash = queueHash,
                currentIndex = p.currentMediaItemIndex.coerceAtLeast(0),
                isPlaying = p.playWhenReady && p.playbackState != Player.STATE_ENDED,
                positionMs = p.currentPosition.coerceAtLeast(0L),
                repeatMode = p.repeatMode,
                shuffleEnabled = p.shuffleModeEnabled,
                sentAtServerMs = 0L,
            )
        }
    }

    suspend fun applyRemoteRoomState(state: TogetherRoomState) {
        val pid = togetherSelfParticipantId ?: return
        val now = android.os.SystemClock.elapsedRealtime()

        val pending = togetherPendingGuestControl
        if (pending != null) {
            val currentTrackId = state.queue.getOrNull(state.currentIndex.coerceAtLeast(0))?.id
            val mismatch =
                (pending.desiredIsPlaying != null && state.isPlaying != pending.desiredIsPlaying) ||
                    (pending.desiredIndex != null && state.currentIndex != pending.desiredIndex) ||
                    (pending.desiredTrackId != null && currentTrackId != pending.desiredTrackId)
            if (now >= pending.expiresAtElapsedMs) {
                if ((pending.desiredIndex != null || pending.desiredTrackId != null) &&
                    now - pending.requestedAtElapsedMs >= 1200L &&
                    mismatch
                ) {
                    showTogetherNotice(context.getString(R.string.together_song_change_failed), key = "GUEST_SEEK_TIMEOUT")
                }
                togetherPendingGuestControl = null
            } else {
                if (mismatch) return
                togetherPendingGuestControl = null
            }
        }

        val lastSentAt = togetherLastAppliedRoomStateSentAtServerMs
        val sentAt = state.sentAtServerMs
        if (sentAt > 0L && lastSentAt > 0L && sentAt <= lastSentAt) return

        val serverNow = togetherClient?.serverNowMs() ?: System.currentTimeMillis()
        val delta = (serverNow - state.sentAtServerMs).coerceIn(0L, 6000L)
        val targetPos =
            if (state.isPlaying) (state.positionMs + delta).coerceAtLeast(0L) else state.positionMs.coerceAtLeast(0L)

        val p = player()
        withContext(Dispatchers.Main) {
            isTogetherApplyingRemote = true
            togetherSuppressEchoUntilElapsedMs = android.os.SystemClock.elapsedRealtime() + 450L
            try {
                val desiredItems = state.queue.map { it.toMediaMetadata().toMediaItem() }
                val desiredIds = state.queue.map { it.id }
                val desiredHash = state.queueHash
                val localIds = p.mediaItems.mapNotNull { it.metadata?.id ?: it.mediaId }.filter { it.isNotBlank() }
                val localHash = if (localIds.isEmpty()) "" else md5(localIds.joinToString(separator = "|"))
                val needsRebuild =
                    desiredItems.isNotEmpty() &&
                        (
                            (desiredHash.isNotBlank() && desiredHash != localHash) ||
                                (desiredHash.isBlank() && desiredIds != localIds)
                        )

                if (desiredItems.isNotEmpty() && needsRebuild) {
                    togetherLastAppliedQueueHash = desiredHash.ifBlank { localHash }
                    val startIndex = state.currentIndex.coerceIn(0, desiredItems.lastIndex)
                    val queue = ListQueue(
                        title = context.getString(R.string.music_player),
                        items = desiredItems,
                        startIndex = startIndex,
                        position = targetPos,
                    )
                    onRemoteQueueReplaced(queue)
                    p.setMediaItems(desiredItems, startIndex, targetPos)
                    p.prepare()
                    p.repeatMode = state.repeatMode
                    p.shuffleModeEnabled = state.shuffleEnabled
                    p.playWhenReady = state.isPlaying
                    togetherLastRemoteAppliedIndex = startIndex
                } else {
                    val index = state.currentIndex.coerceAtLeast(0)
                    val indexChanged = p.mediaItemCount > 0 && index != p.currentMediaItemIndex
                    val stateChanged =
                        p.repeatMode != state.repeatMode ||
                            p.shuffleModeEnabled != state.shuffleEnabled ||
                            p.playWhenReady != state.isPlaying

                    if (indexChanged) {
                        p.seekTo(index.coerceAtMost(p.mediaItemCount - 1), targetPos)
                        p.prepare()
                        p.playWhenReady = state.isPlaying
                    } else if (stateChanged) {
                        if (p.repeatMode != state.repeatMode) p.repeatMode = state.repeatMode
                        if (p.shuffleModeEnabled != state.shuffleEnabled) p.shuffleModeEnabled = state.shuffleEnabled
                        if (p.playWhenReady != state.isPlaying) {
                            p.playWhenReady = state.isPlaying
                            val drift = kotlin.math.abs(p.currentPosition - targetPos)
                            if (drift > 100) {
                                p.seekTo(targetPos)
                                p.prepare()
                            }
                        }
                    } else {
                        val drift = kotlin.math.abs(p.currentPosition - targetPos)
                        val seekThreshold = 1500L
                        val threshold = if (state.isPlaying) seekThreshold else 200L

                        if (drift > threshold) {
                            p.seekTo(targetPos)
                            p.prepare()
                        }
                    }
                    togetherLastRemoteAppliedIndex = index
                }
                togetherLastRemoteAppliedPlayWhenReady = state.isPlaying
                togetherLastAppliedRoomStateSentAtServerMs = sentAt

                togetherSessionState.value =
                    TogetherSessionState.Joined(
                        role = TogetherRole.Guest,
                        sessionId = state.sessionId,
                        selfParticipantId = pid,
                        roomState = state,
                    )
            } finally {
                isTogetherApplyingRemote = false
            }
        }
    }

    suspend fun stopTogetherInternal() {
        stopAvatarsListener()
        togetherBroadcastJob?.cancel()
        togetherBroadcastJob = null

        togetherOnlineConnectJob?.cancel()
        togetherOnlineConnectJob = null

        togetherClientEventsJob?.cancel()
        togetherClientEventsJob = null

        togetherSelfParticipantId = null
        togetherLastAppliedQueueHash = null
        isTogetherApplyingRemote = false
        togetherSuppressEchoUntilElapsedMs = 0L
        togetherLastAppliedRoomStateSentAtServerMs = 0L
        togetherLastRemoteAppliedPlayWhenReady = null
        togetherLastRemoteAppliedIndex = -1
        togetherLastSentControlAtElapsedMs = 0L
        togetherLastSentControlAction = null
        togetherPendingGuestControl = null

        try {
            togetherClient?.disconnect()
        } catch (_: Exception) {}
        togetherClient = null

        try {
            togetherOnlineHost?.disconnect()
        } catch (_: Exception) {}
        togetherOnlineHost = null
    }

    fun TogetherTrack.toMediaMetadata(): MediaMetadata {
        return MediaMetadata(
            id = id,
            title = title,
            artists = artists.map { name -> MediaMetadata.Artist(id = null, name = name) },
            duration = durationSec,
            thumbnailUrl = thumbnailUrl,
            album = null,
            setVideoId = null,
            explicit = false,
        )
    }
}
