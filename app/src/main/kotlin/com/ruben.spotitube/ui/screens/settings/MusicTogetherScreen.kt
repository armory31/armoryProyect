/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ruben.spotitube.ui.screens.settings

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.ruben.spotitube.ArmoryColors
import com.ruben.spotitube.ArmoryTopBar
import com.ruben.spotitube.ui.LocalPlayerAwareWindowInsets
import com.ruben.spotitube.ui.LocalPlayerConnection
import com.ruben.spotitube.R
import com.ruben.spotitube.constants.TogetherAllowGuestsToAddTracksKey
import com.ruben.spotitube.constants.TogetherAllowGuestsToControlPlaybackKey
import com.ruben.spotitube.constants.TogetherCustomAvatarUriKey
import com.ruben.spotitube.constants.TogetherDisplayNameKey
import com.ruben.spotitube.constants.TogetherRequireHostApprovalToJoinKey
import com.ruben.spotitube.constants.TogetherWelcomeShownKey
import com.ruben.spotitube.rememberAccountName
import com.ruben.spotitube.rememberAvatarUrl
import com.ruben.spotitube.together.TogetherRole
import com.ruben.spotitube.together.TogetherRoomSettings
import com.ruben.spotitube.together.TogetherSessionState
import com.ruben.spotitube.ui.component.TextFieldDialog
import com.ruben.spotitube.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import com.ruben.spotitube.ui.component.IconButton as AtIconButton

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun MusicTogetherScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    initialCode: String? = null,
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current

    val (welcomeShown, setWelcomeShown) = rememberPreference(TogetherWelcomeShownKey, false)
    var welcomeDismissedThisSession by rememberSaveable { mutableStateOf(false) }
    val showWelcome = !welcomeShown && !welcomeDismissedThisSession

    if (showWelcome) {
        WelcomeDialog(
            onGotIt = { dontShowAgain ->
                welcomeDismissedThisSession = true
                if (dontShowAgain) setWelcomeShown(true)
            },
            onDismiss = { welcomeDismissedThisSession = true },
        )
    }

    val (customName, setCustomName) = rememberPreference(
        TogetherDisplayNameKey,
        defaultValue = "",
    )
    val (customAvatar, setCustomAvatar) = rememberPreference(
        TogetherCustomAvatarUriKey,
        defaultValue = "",
    )

    val accountName = rememberAccountName()
    val accountAvatar = rememberAvatarUrl()
    val displayName = customName.ifBlank { accountName }
    val displayAvatar = customAvatar.ifBlank { accountAvatar.orEmpty() }
    val (allowAddTracks, setAllowAddTracksRaw) = rememberPreference(
        TogetherAllowGuestsToAddTracksKey, defaultValue = true
    )
    val (allowControlPlayback, setAllowControlPlaybackRaw) = rememberPreference(
        TogetherAllowGuestsToControlPlaybackKey, defaultValue = false
    )
    val (requireApproval, setRequireApprovalRaw) = rememberPreference(
        TogetherRequireHostApprovalToJoinKey, defaultValue = false
    )

    val sessionStateFlow = remember(playerConnection) {
        playerConnection?.service?.togetherSessionState ?: MutableStateFlow(TogetherSessionState.Idle)
    }
    val sessionState by sessionStateFlow.collectAsStateWithLifecycle()

    val avatarsFlow = remember(playerConnection) {
        playerConnection?.service?.togetherAvatars ?: MutableStateFlow<Map<String, String>>(emptyMap())
    }
    val avatars by avatarsFlow.collectAsStateWithLifecycle()

    val isHosting = sessionState is TogetherSessionState.HostingOnline
    val isJoining = sessionState is TogetherSessionState.JoiningOnline
    val isHostRole = when (val state = sessionState) {
        is TogetherSessionState.HostingOnline -> true
        is TogetherSessionState.Joined -> state.role is TogetherRole.Host
        else -> false
    }
    val isCreatingSessionLoading = sessionState is TogetherSessionState.HostingOnline &&
            (sessionState as TogetherSessionState.HostingOnline).roomState == null
    val isJoinedAsGuest = (sessionState as? TogetherSessionState.Joined)?.role is TogetherRole.Guest
    val isWaitingApproval = run {
        val joined = sessionState as? TogetherSessionState.Joined ?: return@run false
        joined.role is TogetherRole.Guest &&
                joined.roomState.participants.firstOrNull { it.id == joined.selfParticipantId }?.isPending == true
    }
    val isJoinedAsAcceptedGuest = isJoinedAsGuest && !isWaitingApproval
    val disableJoinUi = isHostRole || isCreatingSessionLoading || isJoinedAsGuest

    var showNameDialog by rememberSaveable { mutableStateOf(false) }
    var showJoinDialog by rememberSaveable { mutableStateOf(false) }

    val hostParticipants = (sessionState as? TogetherSessionState.HostingOnline)?.roomState?.participants.orEmpty()
    var confirmKickParticipantId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmBanParticipantId by rememberSaveable { mutableStateOf<String?>(null) }
    val confirmKickName = hostParticipants.firstOrNull { it.id == confirmKickParticipantId }?.name
    val confirmBanName = hostParticipants.firstOrNull { it.id == confirmBanParticipantId }?.name

    LaunchedEffect(disableJoinUi, isJoining, isHosting) {
        if (disableJoinUi || isJoining || isHosting) showJoinDialog = false
    }

    fun pushSettingsToActiveSession(
        addTracks: Boolean = allowAddTracks,
        controlPlayback: Boolean = allowControlPlayback,
        approval: Boolean = requireApproval,
    ) {
        if (isHosting) {
            playerConnection?.service?.updateTogetherSettings(
                TogetherRoomSettings(
                    allowGuestsToAddTracks = addTracks,
                    allowGuestsToControlPlayback = controlPlayback,
                    requireHostApprovalToJoin = approval,
                )
            )
        }
    }

    val setAllowAddTracks: (Boolean) -> Unit = { v ->
        setAllowAddTracksRaw(v); pushSettingsToActiveSession(addTracks = v)
    }
    val setAllowControlPlayback: (Boolean) -> Unit = { v ->
        setAllowControlPlaybackRaw(v); pushSettingsToActiveSession(controlPlayback = v)
    }
    val setRequireApproval: (Boolean) -> Unit = { v ->
        setRequireApprovalRaw(v); pushSettingsToActiveSession(approval = v)
    }

    // ── Dialogs ──────────────────────────────────────────────────────────────

    if (showNameDialog) {
        TogetherProfileDialog(
            initialName = displayName,
            accountName = accountName,
            currentDisplayAvatar = displayAvatar,
            hasCustomProfile = customName.isNotEmpty() || customAvatar.isNotEmpty(),
            onConfirm = { newName, pendingUri ->
                if (newName != accountName) {
                    setCustomName(newName)
                } else {
                    setCustomName("")
                }
                if (pendingUri != null) {
                    val pendingPath = runCatching { Uri.parse(pendingUri).path }.getOrNull()
                    context.filesDir.listFiles()?.filter { file ->
                        file.name.startsWith("together_avatar") && file.name.endsWith(".jpg") && file.absolutePath != pendingPath
                    }?.forEach { it.delete() }
                    setCustomAvatar(pendingUri)
                }
                showNameDialog = false
            },
            onResetToAccount = {
                setCustomName("")
                setCustomAvatar("")
                context.filesDir.listFiles()?.filter { file ->
                    file.name.startsWith("together_avatar") && file.name.endsWith(".jpg")
                }?.forEach { it.delete() }
                showNameDialog = false
            },
            onDismiss = {
                showNameDialog = false
            },
        )
    }

    var joinInput by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(initialCode) {
        val trimmed = initialCode?.filter { it in '0'..'9' }?.take(6).orEmpty()
        if (trimmed.length == 6) {
            joinInput = trimmed
        }
    }

    val canJoin = remember(joinInput) {
        joinInput.length == 6 && joinInput.all { it in '0'..'9' }
    }

    if (showJoinDialog) {
        TextFieldDialog(
            title = { Text(stringResource(R.string.together_room_code)) },
            placeholder = { Text(stringResource(R.string.together_room_code_hint)) },
            singleLine = true,
            maxLines = 1,
            isInputValid = { it.trim().length == 6 && it.trim().all { char -> char in '0'..'9' } },
            onDone = { raw ->
                val trimmed = raw.filter { it in '0'..'9' }.take(6)
                joinInput = trimmed
                playerConnection?.service?.joinTogetherOnline(trimmed, displayName, displayAvatar)
            },
            onDismiss = { showJoinDialog = false },
        )
    }

    // Kick / Ban dialogs
    if (confirmKickParticipantId != null) {
        AlertDialog(
            onDismissRequest = { confirmKickParticipantId = null },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(stringResource(R.string.together_kick)) },
            text = {
                Text(
                    stringResource(R.string.together_kick_confirm, confirmKickName ?: stringResource(R.string.unknown)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pid = confirmKickParticipantId ?: return@Button
                        confirmKickParticipantId = null
                        playerConnection?.service?.kickTogetherParticipant(pid)
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.together_kick), fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmKickParticipantId = null }, shape = RoundedCornerShape(16.dp)) {
                    Text(stringResource(R.string.dismiss))
                }
            },
        )
    }

    if (confirmBanParticipantId != null) {
        AlertDialog(
            onDismissRequest = { confirmBanParticipantId = null },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(stringResource(R.string.together_ban)) },
            text = {
                Text(
                    stringResource(R.string.together_ban_confirm, confirmBanName ?: stringResource(R.string.unknown)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pid = confirmBanParticipantId ?: return@Button
                        confirmBanParticipantId = null
                        playerConnection?.service?.banTogetherParticipant(pid)
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.together_ban), fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmBanParticipantId = null }, shape = RoundedCornerShape(16.dp)) {
                    Text(stringResource(R.string.dismiss))
                }
            },
        )
    }

    // ── Screen content ────────────────────────────────────────────────────────

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ArmoryTopBar(
                title = { Text(stringResource(R.string.music_together), style = MaterialTheme.typography.headlineMedium, color = ArmoryColors.Gold2, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                onBack = navController::navigateUp,
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Column(
            Modifier
                .padding(innerPadding)
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Horizontal
                    )
                )
                .verticalScroll(rememberScrollState()),
        ) {

            // Status card
            StatusCard(
                state = sessionState,
                avatars = avatars,
                onCopyCode = { code ->
                    clipboard.setText(AnnotatedString(code))
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
                },
                onShareCode = { code ->
                    val message = context.getString(
                        R.string.together_share_message,
                        com.ruben.spotitube.BuildConfig.SHARE_HOST,
                        code,
                    )
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, message)
                            },
                            null,
                        )
                    )
                },
                onLeave = { playerConnection?.service?.leaveTogether() },
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 4.dp, bottom = 12.dp),
            )

            val hostingOnline = sessionState as? TogetherSessionState.HostingOnline
            val hostRoomState = hostingOnline?.roomState
            val hostSettings = hostingOnline?.settings
            if (hostRoomState != null && isHostRole) {
                OnlineParticipantsCard(
                    participants = hostRoomState.participants,
                    avatars = avatars,
                    hostApprovalEnabled = hostSettings?.requireHostApprovalToJoin == true,
                    onApprove = { pid, approved ->
                        playerConnection?.service?.approveTogetherParticipant(pid, approved)
                    },
                    onKick = { confirmKickParticipantId = it },
                    onBan = { confirmBanParticipantId = it },
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp),
                )
            }

            if (!isJoinedAsGuest) {
                HostSectionCard(
                    displayName = displayName,
                    avatarUrl = displayAvatar,
                    allowAddTracks = allowAddTracks,
                    allowControlPlayback = allowControlPlayback,
                    requireApproval = requireApproval,
                    onShowNameDialog = { showNameDialog = true },
                    onAllowAddTracksChange = setAllowAddTracks,
                    onAllowControlPlaybackChange = setAllowControlPlayback,
                    onRequireApprovalChange = setRequireApproval,
                    isStartEnabled = !isCreatingSessionLoading && !isJoining && !isHosting &&
                            sessionState !is TogetherSessionState.Joined,
                    isLoading = isCreatingSessionLoading,
                    onStartSession = {
                        val settings = TogetherRoomSettings(
                            allowGuestsToAddTracks = allowAddTracks,
                            allowGuestsToControlPlayback = allowControlPlayback,
                            requireHostApprovalToJoin = requireApproval,
                        )
                        playerConnection?.service?.startTogetherOnlineHost(displayName, settings, displayAvatar)
                    },
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp),
                )
            }

            // Join section
            JoinSectionCard(
                joinInput = joinInput,
                onJoinInputChange = { joinInput = it.filter { char -> char in '0'..'9' }.take(6) },
                canJoin = canJoin,
                disableJoinUi = disableJoinUi,
                isJoined = isJoinedAsAcceptedGuest,
                isWaitingApproval = isWaitingApproval,
                isJoining = isJoining,
                onShowJoinDialog = { showJoinDialog = true },
                onPasteFromClipboard = {
                    val text = clipboard.getText()?.text?.trim() ?: ""
                    if (text.isNotBlank()) {
                        val pasted = text.filter { it in '0'..'9' }.take(6)
                        joinInput = pasted
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (pasted.length == 6) {
                            playerConnection?.service?.joinTogetherOnline(pasted, displayName, displayAvatar)
                        }
                    }
                },
                onJoin = {
                    val trimmed = joinInput.trim()
                    playerConnection?.service?.joinTogetherOnline(trimmed, displayName, displayAvatar)
                },
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
            )

            Spacer(Modifier.windowInsetsBottomHeight(LocalPlayerAwareWindowInsets.current))
        }
    }
}

// ── Host Section Card ──────────────────────────────────────────────────────

@Composable
private fun HostSectionCard(
    displayName: String,
    avatarUrl: String? = null,
    allowAddTracks: Boolean,
    allowControlPlayback: Boolean,
    requireApproval: Boolean,
    onShowNameDialog: () -> Unit,
    onAllowAddTracksChange: (Boolean) -> Unit,
    onAllowControlPlaybackChange: (Boolean) -> Unit,
    onRequireApprovalChange: (Boolean) -> Unit,
    isStartEnabled: Boolean,
    isLoading: Boolean,
    onStartSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.fire),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.together_online_session),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.together_online_mode),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(50.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painterResource(R.drawable.wifi),
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Text(
                            stringResource(R.string.together_online_mode),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }

            SettingsItemRow(
                icon = R.drawable.person,
                avatarUrl = avatarUrl,
                title = stringResource(R.string.together_display_name),
                subtitle = displayName,
                onClick = onShowNameDialog,
            )
            Divider()
            ToggleRow(
                icon = R.drawable.playlist_add,
                title = stringResource(R.string.together_allow_guests_add),
                checked = allowAddTracks,
                onCheckedChange = onAllowAddTracksChange,
            )
            Divider()
            ToggleRow(
                icon = R.drawable.play,
                title = stringResource(R.string.together_allow_guests_control),
                checked = allowControlPlayback,
                onCheckedChange = onAllowControlPlaybackChange,
            )
            Divider()
            ToggleRow(
                icon = R.drawable.lock,
                title = stringResource(R.string.together_require_approval),
                checked = requireApproval,
                onCheckedChange = onRequireApprovalChange,
            )

            Spacer(Modifier.height(8.dp))

            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val scale by animateFloatAsState(
                targetValue = if (isPressed) 0.97f else 1f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "scale",
            )

            Button(
                enabled = isStartEnabled,
                onClick = onStartSession,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 10.dp)
                    .scale(scale),
                shape = RoundedCornerShape(18.dp),
                interactionSource = interactionSource,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = ArmoryColors.AbsoluteBlack,
                    disabledContentColor = ArmoryColors.AbsoluteBlack,
                ),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.loading), fontWeight = FontWeight.SemiBold, color = ArmoryColors.AbsoluteBlack)
                } else {
                    Icon(painterResource(R.drawable.fire), null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.start_session), fontWeight = FontWeight.SemiBold, color = ArmoryColors.AbsoluteBlack)
                }
            }
        }
    }
}

// ── Join Section Card ──────────────────────────────────────────────────────

@Composable
private fun JoinSectionCard(
    joinInput: String,
    onJoinInputChange: (String) -> Unit,
    canJoin: Boolean,
    disableJoinUi: Boolean,
    isJoined: Boolean,
    isWaitingApproval: Boolean,
    isJoining: Boolean,
    onShowJoinDialog: () -> Unit,
    onPasteFromClipboard: () -> Unit,
    onJoin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {

            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f),
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f),
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.multi_user),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.together_join_section),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.together_room_code),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Code input row with paste button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(
                            enabled = !disableJoinUi && !isJoining && !isJoined && !isWaitingApproval,
                            onClick = onShowJoinDialog,
                        ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            painterResource(R.drawable.lock),
                            contentDescription = null,
                            tint = if (canJoin)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = if (joinInput.isBlank())
                                stringResource(R.string.together_room_code_hint)
                            else
                                joinInput.trim(),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = if (joinInput.isNotBlank()) FontFamily.Monospace
                                else FontFamily.Default
                            ),
                            color = if (joinInput.isNotBlank())
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (joinInput.isNotBlank() && !disableJoinUi) {
                            Icon(
                                painterResource(R.drawable.close),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .clickable { onJoinInputChange("") },
                            )
                        }
                    }
                }

                if (!disableJoinUi && !isJoining && !isJoined && !isWaitingApproval) {
                    FilledTonalButton(
                        onClick = onPasteFromClipboard,
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Icon(
                            painterResource(R.drawable.copy),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.paste),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val scale by animateFloatAsState(
                targetValue = if (isPressed) 0.97f else 1f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "joinBtnScale",
            )

            FilledTonalButton(
                enabled = canJoin && !disableJoinUi && !isJoining && !isJoined && !isWaitingApproval,
                onClick = onJoin,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 10.dp)
                    .scale(scale),
                shape = RoundedCornerShape(18.dp),
                interactionSource = interactionSource,
            ) {
                when {
                    isJoining -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.connecting), fontWeight = FontWeight.SemiBold)
                    }
                    isWaitingApproval -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.together_waiting_approval), fontWeight = FontWeight.SemiBold)
                    }
                    isJoined -> {
                        Icon(painterResource(R.drawable.check), null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.joined), fontWeight = FontWeight.SemiBold)
                    }
                    else -> {
                        Icon(painterResource(R.drawable.join), null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.join), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberAvatarBitmap(base64: String?): androidx.compose.ui.graphics.ImageBitmap? {
    return remember(base64) {
        if (base64.isNullOrBlank()) null
        else runCatching {
            val bytes = Base64.decode(base64, Base64.NO_WRAP)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            bitmap?.asImageBitmap()
        }.getOrNull()
    }
}

// ── Status Card ────────────────────────────────────────────────────────────

@Composable
private fun StatusCard(
    state: TogetherSessionState,
    avatars: Map<String, String> = emptyMap(),
    onCopyCode: (String) -> Unit,
    onShareCode: (String) -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isActive = state !is TogetherSessionState.Idle
    val isError = state is TogetherSessionState.Error
    val isWaitingApproval = run {
        val joined = state as? TogetherSessionState.Joined ?: return@run false
        joined.role is TogetherRole.Guest &&
                joined.roomState.participants
                    .firstOrNull { it.id == joined.selfParticipantId }?.isPending == true
    }
    val statusColor = when {
        isError -> MaterialTheme.colorScheme.error
        isActive -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        if (isActive && !isError)
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                MaterialTheme.colorScheme.surfaceContainerLow,
                            )
                        else if (isError)
                            listOf(
                                MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                                MaterialTheme.colorScheme.surfaceContainerLow,
                            )
                        else
                            listOf(
                                MaterialTheme.colorScheme.surfaceContainerLow,
                                MaterialTheme.colorScheme.surfaceContainerLow,
                            )
                    )
                )
                .padding(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Status row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        statusColor.copy(alpha = 0.18f),
                                        statusColor.copy(alpha = 0.08f),
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(if (isError) R.drawable.error else R.drawable.fire),
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                stringResource(R.string.together_status),
                                style = MaterialTheme.typography.labelLarge,
                                color = statusColor,
                            )
                            if (isActive && !isError) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                )
                            }
                        }
                        Text(
                            text = when (state) {
                                TogetherSessionState.Idle -> stringResource(R.string.together_idle)
                                is TogetherSessionState.HostingOnline -> stringResource(R.string.together_hosting)
                                is TogetherSessionState.JoiningOnline -> stringResource(R.string.together_joining)
                                is TogetherSessionState.Joined ->
                                    if (isWaitingApproval) stringResource(R.string.together_waiting_approval)
                                    else stringResource(R.string.together_connected)
                                is TogetherSessionState.Error -> stringResource(R.string.together_error_state)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (isActive) {
                        FilledTonalButton(onClick = onLeave, shape = RoundedCornerShape(14.dp)) {
                            Icon(painterResource(R.drawable.leave), null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.leave), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // Inline content
                when (state) {
                    is TogetherSessionState.HostingOnline -> {
                        OnlineRoomCodeCard(
                            code = state.code,
                            onCopy = { onCopyCode(state.code) },
                            onShare = { onShareCode(state.code) },
                        )
                    }
                    is TogetherSessionState.Joined -> {
                        if (!isWaitingApproval) {
                            ParticipantsCard(
                                participants = state.roomState.participants,
                                avatars = avatars,
                            )
                        }
                    }
                    is TogetherSessionState.Error -> {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        ) {
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(14.dp),
                            )
                        }
                    }
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun OnlineRoomCodeCard(
    code: String,
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.together_room_code),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                code,
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Monospace),
                fontWeight = FontWeight.Bold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onCopy) { Text(stringResource(R.string.copy_link)) }
                TextButton(onClick = onShare) { Text(stringResource(R.string.share)) }
            }
        }
    }
}

// ── Reused composables ────────────────────────────────────────────────────

@Composable
private fun OnlineParticipantsCard(
    participants: List<com.ruben.spotitube.together.TogetherParticipant>,
    avatars: Map<String, String>,
    hostApprovalEnabled: Boolean,
    onApprove: (participantId: String, approved: Boolean) -> Unit,
    onKick: (participantId: String) -> Unit,
    onBan: (participantId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f),
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f),
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.list),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.together_participants),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.together_connected_count, participants.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            participants.forEachIndexed { index, participant ->
                key(participant.id) {
                    if (index != 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 76.dp, end = 18.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        )
                    }
                    val accent = if (participant.isHost)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.secondary
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        val avatarBitmap = rememberAvatarBitmap(avatars[participant.id])
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(accent.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (avatarBitmap != null) {
                                Image(
                                    bitmap = avatarBitmap,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                Icon(
                                    painterResource(
                                        if (participant.isHost) R.drawable.fire else R.drawable.person
                                    ),
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                participant.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = when {
                                    participant.isHost -> stringResource(R.string.together_role_host)
                                    participant.isPending && hostApprovalEnabled ->
                                        stringResource(R.string.together_pending_approval)
                                    else -> stringResource(R.string.together_role_guest)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (!participant.isHost) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (participant.isPending && hostApprovalEnabled) {
                                    AtIconButton(onClick = { onApprove(participant.id, true) }, onLongClick = {}) {
                                        Icon(painterResource(R.drawable.check), null)
                                    }
                                    AtIconButton(onClick = { onApprove(participant.id, false) }, onLongClick = {}) {
                                        Icon(painterResource(R.drawable.close), null)
                                    }
                                }
                                AtIconButton(onClick = { onKick(participant.id) }, onLongClick = {}) {
                                    Icon(painterResource(R.drawable.kick), null)
                                }
                                AtIconButton(onClick = { onBan(participant.id) }, onLongClick = {}) {
                                    Icon(painterResource(R.drawable.block), null)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ParticipantsCard(
    participants: List<com.ruben.spotitube.together.TogetherParticipant>,
    avatars: Map<String, String>,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    painterResource(R.drawable.person),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    stringResource(R.string.participants),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                participants.forEach { p ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        val avatarBitmap = rememberAvatarBitmap(avatars[p.id])
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (avatarBitmap != null) {
                                Image(
                                    bitmap = avatarBitmap,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                Icon(
                                    painterResource(R.drawable.person),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                        Text(
                            text = p.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeDialog(
    onGotIt: (dontShowAgain: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var dontShowAgain by rememberSaveable { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.fire),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Text(
                    stringResource(R.string.together_welcome_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.together_welcome_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        InstructionRow(
                            R.drawable.fire,
                            MaterialTheme.colorScheme.primary,
                            stringResource(R.string.together_welcome_host_title),
                            stringResource(R.string.together_welcome_host_body)
                        )
                        InstructionRow(
                            R.drawable.link,
                            MaterialTheme.colorScheme.tertiary,
                            stringResource(R.string.together_welcome_join_title),
                            stringResource(R.string.together_welcome_join_body)
                        )
                        InstructionRow(
                            R.drawable.lock,
                            MaterialTheme.colorScheme.secondary,
                            stringResource(R.string.together_welcome_permissions_title),
                            stringResource(R.string.together_welcome_permissions_body)
                        )
                    }
                }
                CheckboxRow(
                    checked = dontShowAgain,
                    onCheckedChange = { dontShowAgain = it },
                    label = stringResource(R.string.together_dont_show_again),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onGotIt(dontShowAgain) },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Icon(painterResource(R.drawable.check), null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.got_it), fontWeight = FontWeight.SemiBold)
            }
        },
    )
}

@Composable
private fun InstructionRow(
    icon: Int,
    accentColor: Color,
    title: String,
    body: String,
) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accentColor.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), null, tint = accentColor, modifier = Modifier.size(20.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Shared row primitives ─────────────────────────────────────────────────

@Composable
private fun Divider() = HorizontalDivider(
    modifier = Modifier.padding(start = 76.dp, end = 18.dp),
    thickness = 0.5.dp,
    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
)

@Composable
private fun SettingsItemRow(
    icon: Int,
    avatarUrl: String? = null,
    title: String,
    subtitle: String,
    subtitleMaxLines: Int = 1,
    onClick: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val alpha by animateFloatAsState(
        targetValue = if (isPressed) 0.7f else 1f,
        animationSpec = tween(100),
        label = "itemAlpha",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ) else Modifier
            )
            .graphicsLayer { this.alpha = alpha }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            if (!avatarUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                )
            } else {
                Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f), modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = subtitleMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onClick != null) {
            Spacer(Modifier.width(8.dp))
            Icon(
                painterResource(R.drawable.navigate_next),
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun TogetherProfileDialog(
    initialName: String,
    accountName: String,
    currentDisplayAvatar: String,
    hasCustomProfile: Boolean,
    onConfirm: (newName: String, pendingPhotoUri: String?) -> Unit,
    onResetToAccount: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var nameText by remember { mutableStateOf(initialName) }
    var pendingPhotoUriStr by remember { mutableStateOf<String?>(null) }

    fun cleanupPending() {
        pendingPhotoUriStr?.let { pendingUri ->
            runCatching {
                val path = Uri.parse(pendingUri).path
                if (!path.isNullOrEmpty()) File(path).delete()
            }
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, selectedUri))
                    } else {
                        @Suppress("DEPRECATION")
                        MediaStore.Images.Media.getBitmap(context.contentResolver, selectedUri)
                    }
                    val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 256, 256, true)

                    pendingPhotoUriStr?.let { oldPendingUri ->
                        runCatching {
                            val path = Uri.parse(oldPendingUri).path
                            if (!path.isNullOrEmpty()) File(path).delete()
                        }
                    }

                    val avatarFile = File(context.filesDir, "together_avatar_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(avatarFile).use { out ->
                        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }
                    val savedUriStr = Uri.fromFile(avatarFile).toString()
                    withContext(Dispatchers.Main) {
                        pendingPhotoUriStr = savedUriStr
                    }
                } catch (_: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Error al guardar imagen", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = {
            cleanupPending()
            onDismiss()
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.together_edit_profile_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val avatarToDisplay = pendingPhotoUriStr ?: currentDisplayAvatar
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { imagePickerLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center,
                ) {
                    if (avatarToDisplay.isNotBlank()) {
                        AsyncImage(
                            model = avatarToDisplay,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.person),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }

                OutlinedTextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    singleLine = true,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (hasCustomProfile) {
                    TextButton(
                        onClick = {
                            cleanupPending()
                            onResetToAccount()
                        },
                    ) {
                        Text(stringResource(R.string.together_use_account_profile))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = nameText.trim()
                    if (trimmed.isEmpty()) return@Button
                    onConfirm(trimmed, pendingPhotoUriStr)
                },
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(stringResource(R.string.ok_button), fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    cleanupPending()
                    onDismiss()
                },
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(stringResource(R.string.dismiss))
            }
        },
    )
}

@Composable
private fun ToggleRow(
    icon: Int,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f), modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(16.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            thumbContent = {
                Icon(
                    painterResource(if (checked) R.drawable.check else R.drawable.close),
                    null,
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                )
            },
        )
    }
}

@Composable
private fun CheckboxRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
