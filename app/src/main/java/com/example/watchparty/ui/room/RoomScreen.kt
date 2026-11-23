package com.example.watchparty.ui.room

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.ui.theme.WpBackground
import com.example.ui.theme.WpCard
import com.example.ui.theme.WpDivider
import com.example.ui.theme.WpError
import com.example.ui.theme.WpPrimary
import com.example.ui.theme.WpSecondary
import com.example.ui.theme.WpSuccess
import com.example.ui.theme.WpSurface
import com.example.ui.theme.WpTextMuted
import com.example.ui.theme.WpTextPrimary
import com.example.ui.theme.WpWarning
import com.example.watchparty.media.SoundEffectItem
import com.example.watchparty.net.ParticipantDto
import com.example.watchparty.net.Protocol
import com.example.watchparty.util.DeviceHealthStatus
import com.example.watchparty.util.Qr
import kotlinx.coroutines.delay
import java.util.Locale

enum class CinemaFilterMode(val label: String, val overlayColor: Color) {
    STANDARD("Standard", Color.Transparent),
    WARM_CINEMA("Warm Cinema", Color(0x22FF9E44)),
    NIGHT_COMFORT("Night Dim", Color(0x48000000)),
    COOL_THEATRE("Cool Theatre", Color(0x1E26D0CE))
}

@OptIn(UnstableApi::class)
@Composable
fun RoomScreen(
    isHost: Boolean,
    roomCode: String,
    roomPassword: String = "",
    hostIp: String,
    exoPlayer: ExoPlayer,
    isPlaying: Boolean,
    globalPositionMs: Long,
    totalDurationMs: Long,
    statusBanner: String?,
    countdownNumber: Int?,
    canStartAnyway: Boolean,
    isRoomLocked: Boolean,
    isMicOn: Boolean,
    isLocalSpeaking: Boolean,
    isSpeakerOn: Boolean,
    activeRemoteSpeaker: String?,
    videoVolume: Float,
    voiceVolume: Float,
    isAutoDuckEnabled: Boolean,
    playbackSpeed: Float,
    deviceHealth: DeviceHealthStatus,
    participants: List<ParticipantDto>,
    chatMessages: List<ChatUiMessage>,
    floatingReactions: List<FloatingReactionItem>,
    onPlayPauseToggle: () -> Unit,
    onStartAnywayClick: () -> Unit,
    onHostSeekTo: (Long) -> Unit,
    onToggleMic: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onVideoVolumeChange: (Float) -> Unit,
    onVoiceVolumeChange: (Float) -> Unit,
    onToggleAutoDuck: () -> Unit,
    onPlaybackSpeedChange: (Float) -> Unit,
    onAddSimulatedParticipant: () -> Unit,
    onSendChat: (text: String, replyTo: String?) -> Unit,
    onSendReaction: (String) -> Unit,
    onSendSoundEffect: (SoundEffectItem) -> Unit = {},
    onReactionFinished: (Long) -> Unit,
    onToggleLock: () -> Unit,
    onKickParticipant: (String) -> Unit,
    onMuteParticipant: (String) -> Unit,
    onLeaveOrEndParty: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val configuration = LocalConfiguration.current
    val isSystemLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var isExpandedFullscreen by remember { mutableStateOf(false) }
    val isFullScreenMode = isSystemLandscape || isExpandedFullscreen

    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var cinemaFilter by remember { mutableStateOf(CinemaFilterMode.STANDARD) }
    var screenBrightness by remember { mutableFloatStateOf(0.85f) }
    var showAdvancedSettings by remember { mutableStateOf(false) }
    var showInviteDialog by remember { mutableStateOf(false) }
    var showLeaveDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var selectedParticipantForAction by remember { mutableStateOf<ParticipantDto?>(null) }
    var showLandscapeChat by remember { mutableStateOf(false) }

    // Track unread messages and floating toast when fullscreen chat is closed
    var lastSeenMessageCount by remember { mutableIntStateOf(chatMessages.size) }
    var fullscreenToastMessage by remember { mutableStateOf<ChatUiMessage?>(null) }

    LaunchedEffect(chatMessages.size, showLandscapeChat, isFullScreenMode) {
        if (showLandscapeChat || !isFullScreenMode) {
            lastSeenMessageCount = chatMessages.size
            fullscreenToastMessage = null
        } else if (chatMessages.size > lastSeenMessageCount) {
            val latest = chatMessages.lastOrNull()
            if (latest != null && !latest.isMe) {
                fullscreenToastMessage = latest
                delay(3800L)
                if (fullscreenToastMessage?.id == latest.id) {
                    fullscreenToastMessage = null
                }
            }
        }
    }

    val unreadCountInFullscreen = (chatMessages.size - lastSeenMessageCount).coerceAtLeast(0)

    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    fun toggleFullScreenOrientation() {
        if (isFullScreenMode) {
            isExpandedFullscreen = false
            showLandscapeChat = false
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            isExpandedFullscreen = true
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    fun cycleResizeMode() {
        resizeMode = when (resizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
    }

    val resizeModeLabel = when (resizeMode) {
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Zoom"
        AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Stretch"
        else -> "Fit"
    }

    BackHandler {
        when {
            showLandscapeChat -> showLandscapeChat = false
            isFullScreenMode -> toggleFullScreenOrientation()
            else -> showLeaveDialog = true
        }
    }

    if (showInviteDialog) {
        InviteFriendsDialog(
            roomCode = roomCode,
            roomPassword = roomPassword,
            hostIp = hostIp,
            isHost = isHost,
            isRoomLocked = isRoomLocked,
            participantCount = participants.size,
            onToggleLock = onToggleLock,
            onAddSimulatedFriend = onAddSimulatedParticipant,
            onDismiss = { showInviteDialog = false }
        )
    }

    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            containerColor = WpCard,
            title = {
                Text(
                    text = if (isHost) "End Watch Party?" else "Leave Watch Party?",
                    color = WpTextPrimary
                )
            },
            text = {
                Text(
                    text = if (isHost) {
                        "Kya aap room band karna chahte hain? Sabhi dost disconnect ho jayenge."
                    } else {
                        "Kya aap watch party chhodna chahte hain?"
                    },
                    color = WpTextMuted
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLeaveDialog = false
                        onLeaveOrEndParty()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WpError)
                ) {
                    Text(if (isHost) "End Party" else "Leave", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveDialog = false }) {
                    Text("Cancel", color = WpTextMuted)
                }
            }
        )
    }

    if (showAdvancedSettings) {
        AdvancedPlayerSettingsDialog(
            videoVolume = videoVolume,
            voiceVolume = voiceVolume,
            isAutoDuckEnabled = isAutoDuckEnabled,
            playbackSpeed = playbackSpeed,
            screenBrightness = screenBrightness,
            selectedFilter = cinemaFilter,
            resizeModeLabel = resizeModeLabel,
            isHost = isHost,
            onVideoVolumeChange = onVideoVolumeChange,
            onVoiceVolumeChange = onVoiceVolumeChange,
            onToggleAutoDuck = onToggleAutoDuck,
            onPlaybackSpeedChange = onPlaybackSpeedChange,
            onBrightnessChange = { value ->
                screenBrightness = value
                activity?.window?.let { win ->
                    val params = win.attributes
                    params.screenBrightness = value.coerceIn(0.1f, 1.0f)
                    win.attributes = params
                }
            },
            onSelectFilter = { cinemaFilter = it },
            onCycleResizeMode = { cycleResizeMode() },
            onRotateScreen = { toggleFullScreenOrientation() },
            onDismiss = { showAdvancedSettings = false }
        )
    }

    selectedParticipantForAction?.let { target ->
        AlertDialog(
            onDismissRequest = { selectedParticipantForAction = null },
            containerColor = WpCard,
            title = {
                Text(text = target.name, color = WpTextPrimary)
            },
            text = {
                Text(
                    text = "Status: Connected • Live Synced ✓",
                    color = WpSuccess
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onKickParticipant(target.userId)
                        selectedParticipantForAction = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WpError)
                ) {
                    Text("Remove from Party", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        onMuteParticipant(target.userId)
                        selectedParticipantForAction = null
                    }
                ) {
                    Text("Mute / Unmute User", color = WpSecondary)
                }
            }
        )
    }

    if (isFullScreenMode) {
        // FULL-SCREEN MODE: Video + Top Invite/Chat Bar + Floating Toast + Slide-Over Chat Drawer with Explicit Close Header
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            VideoPlayerSurface(
                exoPlayer = exoPlayer,
                isHost = isHost,
                isPlaying = isPlaying,
                isFullScreen = true,
                resizeMode = resizeMode,
                resizeModeLabel = resizeModeLabel,
                cinemaFilter = cinemaFilter,
                screenBrightness = screenBrightness,
                videoVolume = videoVolume,
                globalPositionMs = globalPositionMs,
                totalDurationMs = totalDurationMs,
                statusBanner = statusBanner,
                countdownNumber = countdownNumber,
                floatingReactions = floatingReactions,
                onPlayPauseToggle = onPlayPauseToggle,
                onSeekTo = onHostSeekTo,
                onBrightnessChange = { value ->
                    val clamped = value.coerceIn(0.05f, 1.0f)
                    screenBrightness = clamped
                    activity?.window?.let { win ->
                        val params = win.attributes
                        params.screenBrightness = clamped
                        win.attributes = params
                    }
                },
                onVideoVolumeChange = onVideoVolumeChange,
                onToggleFullScreen = { toggleFullScreenOrientation() },
                onCycleResizeMode = { cycleResizeMode() },
                onOpenAdvancedSettings = { showAdvancedSettings = true },
                onReactionFinished = onReactionFinished,
                modifier = Modifier.fillMaxSize()
            )

            // Top Right Floating Bar in Fullscreen (Only shown when Chat Drawer is closed)
            AnimatedVisibility(
                visible = !showLandscapeChat,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(top = 10.dp, end = 14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Top Room Code & Invite Button (Mid-party add friend)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(WpBackground.copy(alpha = 0.8f))
                            .border(1.dp, WpPrimary.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                            .clickable { showInviteDialog = true }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                            .testTag("fullscreen_invite_button"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PersonAdd,
                            contentDescription = "Invite Friend",
                            tint = WpPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Code: $roomCode",
                            style = MaterialTheme.typography.bodySmall,
                            color = WpTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Open Live Chat Button with Unread Count Badge
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(WpBackground.copy(alpha = 0.8f))
                            .border(1.dp, WpSecondary.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                            .clickable { showLandscapeChat = true }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                            .testTag("open_fullscreen_chat_button"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Chat,
                            contentDescription = "Open Live Chat",
                            tint = WpSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Live Chat",
                            style = MaterialTheme.typography.bodySmall,
                            color = WpTextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (unreadCountInFullscreen > 0) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(WpError),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = unreadCountInFullscreen.toString(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Floating Incoming Chat Pill Toast at Bottom-Left when Fullscreen Chat is Closed
            AnimatedVisibility(
                visible = !showLandscapeChat && fullscreenToastMessage != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(start = 16.dp, bottom = 56.dp)
            ) {
                fullscreenToastMessage?.let { toast ->
                    Row(
                        modifier = Modifier
                            .widthIn(max = 320.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(WpBackground.copy(alpha = 0.88f))
                            .border(1.dp, WpSecondary.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                            .clickable {
                                fullscreenToastMessage = null
                                showLandscapeChat = true
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${toast.from}: ",
                            style = MaterialTheme.typography.bodySmall,
                            color = WpSecondary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = toast.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = WpTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Reply",
                            style = MaterialTheme.typography.bodySmall,
                            color = WpPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Tap-outside Scrim + Slide-Over Right Chat Drawer with Prominent Close Button
            AnimatedVisibility(
                visible = showLandscapeChat,
                enter = slideInHorizontally { it } + fadeIn(),
                exit = slideOutHorizontally { it } + fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Left Video Area Scrim: tapping anywhere here closes the chat drawer!
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(Color.Black.copy(alpha = 0.25f))
                            .clickable { showLandscapeChat = false }
                    )

                    // Right-side Chat Drawer Panel
                    Column(
                        modifier = Modifier
                            .width(350.dp)
                            .fillMaxHeight()
                            .background(WpBackground.copy(alpha = 0.96f))
                            .border(width = 1.dp, color = WpDivider)
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                    ) {
                        // Dedicated Fullscreen Chat Header with Prominent Close / Hide Button!
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(WpSurface)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Chat,
                                    contentDescription = null,
                                    tint = WpSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Party Chat & Voice",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = WpTextPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Room $roomCode • ${participants.size} online",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = WpTextMuted
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Invite button inside drawer header too
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(WpCard)
                                        .border(1.dp, WpPrimary.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                                        .clickable { showInviteDialog = true }
                                        .padding(horizontal = 8.dp, vertical = 5.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Filled.PersonAdd,
                                            contentDescription = "Add Friend",
                                            tint = WpPrimary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "+Add",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = WpTextPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                // Explicit Close / Hide Chat Button
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(WpError.copy(alpha = 0.18f))
                                        .border(1.dp, WpError, RoundedCornerShape(14.dp))
                                        .clickable { showLandscapeChat = false }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                        .testTag("close_fullscreen_chat_button"),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Close Chat",
                                        tint = WpError,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Hide",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = WpError,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = WpDivider)

                        ParticipantsStrip(
                            participants = participants,
                            isHost = isHost,
                            isMicOn = isMicOn,
                            isLocalSpeaking = isLocalSpeaking,
                            isSpeakerOn = isSpeakerOn,
                            activeRemoteSpeaker = activeRemoteSpeaker,
                            onToggleMic = onToggleMic,
                            onToggleSpeaker = onToggleSpeaker,
                            onParticipantClick = { selectedParticipantForAction = it }
                        )

                        HorizontalDivider(color = WpDivider)

                        ChatPanel(
                            messages = chatMessages,
                            onSendMessage = onSendChat,
                            onSendReaction = onSendReaction,
                            onSendSoundEffect = onSendSoundEffect,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    } else {
        // PORTRAIT LAYOUT
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(WpBackground)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            // 1. Top Room Bar (Clickable Room Code + Invite Pill, LIVE SYNC badge, Battery/Temp, Pro Settings, Menu)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WpSurface)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Clickable Room Code + Invite Pill (shows QR, IP, and Room Code anytime mid-party!)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(WpPrimary.copy(alpha = 0.18f))
                            .border(1.dp, WpPrimary, RoundedCornerShape(12.dp))
                            .clickable { showInviteDialog = true }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("top_room_code_invite_button"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.QrCode2,
                            contentDescription = "Show Room Code & QR",
                            tint = WpSecondary,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Code: $roomCode",
                            style = MaterialTheme.typography.titleMedium,
                            color = WpTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(WpPrimary)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "+Add",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Live Sync Status Dot
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(WpSuccess.copy(alpha = 0.14f))
                            .padding(horizontal = 7.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(WpSuccess)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "SYNC",
                            style = MaterialTheme.typography.bodySmall,
                            color = WpSuccess,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val isHot = deviceHealth.temperatureCelsius >= 41.0f
                    val isLowBattery = deviceHealth.batteryPercent <= 20 && !deviceHealth.isCharging
                    val healthColor = if (isHot || isLowBattery) WpWarning else WpTextMuted

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(WpCard)
                            .padding(horizontal = 7.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isLowBattery) Icons.Filled.BatteryAlert else Icons.Filled.BatteryStd,
                            contentDescription = "Battery",
                            tint = healthColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "${deviceHealth.batteryPercent}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = healthColor
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Filled.Thermostat,
                            contentDescription = "Temperature",
                            tint = healthColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = String.format(Locale.US, "%.0f°", deviceHealth.temperatureCelsius),
                            style = MaterialTheme.typography.bodySmall,
                            color = healthColor
                        )
                    }

                    IconButton(
                        onClick = { showAdvancedSettings = true },
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("advanced_player_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = "Player & Audio Settings",
                            tint = WpSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("room_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "Room Options",
                                tint = WpTextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(WpCard)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Invite Friend (QR & Code $roomCode)", color = WpTextPrimary) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.PersonAdd,
                                        contentDescription = null,
                                        tint = WpSuccess
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    showInviteDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Full Screen / Rotate", color = WpTextPrimary) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.ScreenRotation,
                                        contentDescription = null,
                                        tint = WpSecondary
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    toggleFullScreenOrientation()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Pro Video & Voice Settings", color = WpTextPrimary) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Tune,
                                        contentDescription = null,
                                        tint = WpPrimary
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    showAdvancedSettings = true
                                }
                            )
                            if (isHost) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (isRoomLocked) "Unlock Room" else "Lock Room",
                                            color = WpTextPrimary
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (isRoomLocked) Icons.Filled.LockOpen else Icons.Filled.Lock,
                                            contentDescription = null,
                                            tint = WpSecondary
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        onToggleLock()
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (isHost) stringResource(R.string.end_party) else stringResource(R.string.leave_party),
                                        color = WpError
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = null,
                                        tint = WpError
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    showLeaveDialog = true
                                }
                            )
                        }
                    }
                }
            }

            // 2. 16:9 ExoPlayer Video Box with Double-Tap Seek, Swipe Gestures & Interactive Controls
            VideoPlayerSurface(
                exoPlayer = exoPlayer,
                isHost = isHost,
                isPlaying = isPlaying,
                isFullScreen = false,
                resizeMode = resizeMode,
                resizeModeLabel = resizeModeLabel,
                cinemaFilter = cinemaFilter,
                screenBrightness = screenBrightness,
                videoVolume = videoVolume,
                globalPositionMs = globalPositionMs,
                totalDurationMs = totalDurationMs,
                statusBanner = statusBanner,
                countdownNumber = countdownNumber,
                floatingReactions = floatingReactions,
                onPlayPauseToggle = onPlayPauseToggle,
                onSeekTo = onHostSeekTo,
                onBrightnessChange = { value ->
                    val clamped = value.coerceIn(0.05f, 1.0f)
                    screenBrightness = clamped
                    activity?.window?.let { win ->
                        val params = win.attributes
                        params.screenBrightness = clamped
                        win.attributes = params
                    }
                },
                onVideoVolumeChange = onVideoVolumeChange,
                onToggleFullScreen = { toggleFullScreenOrientation() },
                onCycleResizeMode = { cycleResizeMode() },
                onOpenAdvancedSettings = { showAdvancedSettings = true },
                onReactionFinished = onReactionFinished,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            )

            // 3. Compact Synchronized Transport Bar
            PlaybackTransportSection(
                isHost = isHost,
                isPlaying = isPlaying,
                globalPositionMs = globalPositionMs,
                totalDurationMs = totalDurationMs,
                canStartAnyway = canStartAnyway,
                onPlayPauseToggle = onPlayPauseToggle,
                onStartAnywayClick = onStartAnywayClick,
                onHostSeekTo = onHostSeekTo,
                onToggleFullScreen = { toggleFullScreenOrientation() },
                onCycleResizeMode = { cycleResizeMode() },
                resizeModeLabel = resizeModeLabel
            )

            HorizontalDivider(color = WpDivider)

            // 4. Voice Sharing & Participants Strip
            ParticipantsStrip(
                participants = participants,
                isHost = isHost,
                isMicOn = isMicOn,
                isLocalSpeaking = isLocalSpeaking,
                isSpeakerOn = isSpeakerOn,
                activeRemoteSpeaker = activeRemoteSpeaker,
                onToggleMic = onToggleMic,
                onToggleSpeaker = onToggleSpeaker,
                onParticipantClick = { selectedParticipantForAction = it }
            )

            HorizontalDivider(color = WpDivider)

            // 5. Modern Bubble Chat Panel + Live Soundboard + Quick Reactions/Phrases
            ChatPanel(
                messages = chatMessages,
                onSendMessage = onSendChat,
                onSendReaction = onSendReaction,
                onSendSoundEffect = onSendSoundEffect,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * In-Room Live Invite & Room Code Dialog
 * Allows adding new friends mid-party with scannable QR code, Host IP, Room Code copy/share, and 1-tap simulator.
 */
@Composable
private fun InviteFriendsDialog(
    roomCode: String,
    roomPassword: String = "",
    hostIp: String,
    isHost: Boolean,
    isRoomLocked: Boolean,
    participantCount: Int,
    onToggleLock: () -> Unit,
    onAddSimulatedFriend: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val qrBitmap: Bitmap = remember(hostIp, roomCode, roomPassword) {
        val payload = Qr.encodePayload(
            ip = hostIp,
            port = 8080,
            roomCode = roomCode,
            password = roomPassword
        )
        Qr.generateQrBitmap(payload, 420)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = WpCard,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Add Friend to Party",
                    style = MaterialTheme.typography.titleLarge,
                    color = WpTextPrimary,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = WpTextMuted
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // QR Code Box
                Box(
                    modifier = Modifier
                        .size(168.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "Party QR Code",
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Room Code & IP Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(WpSurface)
                        .border(1.dp, WpDivider, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Room Code: $roomCode",
                        style = MaterialTheme.typography.headlineMedium,
                        color = WpSecondary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Host IP: $hostIp",
                        style = MaterialTheme.typography.bodyMedium,
                        color = WpTextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (roomPassword.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "🔒 Password: $roomPassword",
                            style = MaterialTheme.typography.bodySmall,
                            color = WpWarning,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Naya dost Auto-Finder ya QR se join karte hi exact second par sync ho jayega!",
                        style = MaterialTheme.typography.bodySmall,
                        color = WpTextMuted
                    )
                }

                // Copy & Share Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val pwdSuffix = if (roomPassword.isNotBlank()) " | Password: $roomPassword" else ""
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val inviteText = "Join Watch Party! Host IP: $hostIp | Room Code: $roomCode$pwdSuffix"
                            clipboard?.setPrimaryClip(ClipData.newPlainText("Watch Party Invite", inviteText))
                            Toast.makeText(context, "Invite copied!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = null,
                            tint = WpSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy Code", color = WpSecondary)
                    }

                    Button(
                        onClick = {
                            val inviteText = "Join my Watch Party on same WiFi!\nHost IP: $hostIp\nRoom Code: $roomCode$pwdSuffix"
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, inviteText)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Watch Party Code"))
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = WpPrimary)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share", color = Color.White)
                    }
                }

                if (isHost) {
                    if (isRoomLocked) {
                        OutlinedButton(
                            onClick = onToggleLock,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.LockOpen,
                                contentDescription = null,
                                tint = WpWarning,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Room is Locked • Tap to Unlock", color = WpWarning)
                        }
                    }

                    if (participantCount < Protocol.MAX_ROOM_PARTICIPANTS) {
                        TextButton(
                            onClick = {
                                onAddSimulatedFriend()
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PersonAdd,
                                contentDescription = null,
                                tint = WpSuccess,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "+ Add Simulated Friend Mid-Party (${participantCount}/6)",
                                color = WpSuccess,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = WpSecondary, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayerSurface(
    exoPlayer: ExoPlayer,
    isHost: Boolean,
    isPlaying: Boolean,
    isFullScreen: Boolean,
    resizeMode: Int,
    resizeModeLabel: String,
    cinemaFilter: CinemaFilterMode,
    screenBrightness: Float,
    videoVolume: Float,
    globalPositionMs: Long,
    totalDurationMs: Long,
    statusBanner: String?,
    countdownNumber: Int?,
    floatingReactions: List<FloatingReactionItem>,
    onPlayPauseToggle: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onVideoVolumeChange: (Float) -> Unit,
    onToggleFullScreen: () -> Unit,
    onCycleResizeMode: () -> Unit,
    onOpenAdvancedSettings: () -> Unit,
    onReactionFinished: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var controlsVisible by remember { mutableStateOf(true) }
    var isControlsLocked by remember { mutableStateOf(false) }
    var doubleTapSeekNotice by remember { mutableStateOf<String?>(null) }

    // Swipe Gesture HUD State (Left = Brightness, Right = Volume)
    var swipeHudText by remember { mutableStateOf<String?>(null) }
    var swipeHudFraction by remember { mutableFloatStateOf(0f) }
    var isSwipeHudBrightness by remember { mutableStateOf(true) }
    var isSwipingLeftHalf by remember { mutableStateOf(true) }
    var dragBrightnessAccum by remember(screenBrightness) { mutableFloatStateOf(screenBrightness) }
    var dragVolumeAccum by remember(videoVolume) { mutableFloatStateOf(videoVolume) }

    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(3500L)
            controlsVisible = false
        }
    }

    LaunchedEffect(doubleTapSeekNotice) {
        if (doubleTapSeekNotice != null) {
            delay(850L)
            doubleTapSeekNotice = null
        }
    }

    LaunchedEffect(swipeHudText) {
        if (swipeHudText != null) {
            delay(950L)
            swipeHudText = null
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .pointerInput(isHost, isControlsLocked, globalPositionMs, totalDurationMs) {
                detectTapGestures(
                    onTap = {
                        controlsVisible = !controlsVisible
                    },
                    onDoubleTap = { offset ->
                        if (isHost && !isControlsLocked) {
                            if (offset.x < size.width / 2f) {
                                val target = (globalPositionMs - 10_000L).coerceAtLeast(0L)
                                doubleTapSeekNotice = "⏪ -10s"
                                onSeekTo(target)
                            } else {
                                val target = (globalPositionMs + 10_000L).coerceAtMost(totalDurationMs.coerceAtLeast(10_000L))
                                doubleTapSeekNotice = "⏩ +10s"
                                onSeekTo(target)
                            }
                        }
                    }
                )
            }
            .pointerInput(isControlsLocked) {
                if (!isControlsLocked) {
                    detectVerticalDragGestures(
                        onDragStart = { startOffset ->
                            isSwipingLeftHalf = startOffset.x < size.width / 2f
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            val heightPx = size.height.coerceAtLeast(1).toFloat()
                            val delta = (-dragAmount / heightPx) * 1.35f
                            if (isSwipingLeftHalf) {
                                val nextB = (dragBrightnessAccum + delta).coerceIn(0.05f, 1.0f)
                                dragBrightnessAccum = nextB
                                onBrightnessChange(nextB)
                                isSwipeHudBrightness = true
                                swipeHudFraction = nextB
                                swipeHudText = "☀️ Brightness: ${(nextB * 100).toInt()}%"
                            } else {
                                val nextV = (dragVolumeAccum + delta).coerceIn(0.0f, 1.0f)
                                dragVolumeAccum = nextV
                                onVideoVolumeChange(nextV)
                                isSwipeHudBrightness = false
                                swipeHudFraction = nextV
                                swipeHudText = "🔊 Volume: ${(nextV * 100).toInt()}%"
                            }
                        }
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    keepScreenOn = true
                    this.resizeMode = resizeMode
                }
            },
            update = { view ->
                if (view.player !== exoPlayer) {
                    view.player = exoPlayer
                }
                if (view.resizeMode != resizeMode) {
                    view.resizeMode = resizeMode
                }
            }
        )

        // Cinema Ambience Filter Overlay
        if (cinemaFilter != CinemaFilterMode.STANDARD) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(cinemaFilter.overlayColor)
            )
        }

        // Floating Emoji Reactions Overlay
        FloatingReactionsOverlay(
            reactions = floatingReactions,
            onReactionFinished = onReactionFinished
        )

        // Top-Center Status Banner (Syncing / Paused / Waiting / Auto-Reconnecting)
        StatusBanner(
            bannerText = statusBanner,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp, start = 16.dp, end = 16.dp)
        )

        // Double-Tap Seek Feedback Badge
        AnimatedVisibility(
            visible = doubleTapSeekNotice != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(WpBackground.copy(alpha = 0.85f))
                    .border(1.dp, WpSecondary, RoundedCornerShape(20.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text(
                    text = doubleTapSeekNotice.orEmpty(),
                    style = MaterialTheme.typography.titleLarge,
                    color = WpSecondary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Vertical Swipe Gesture HUD Pill (Brightness on Left Swipe, Volume on Right Swipe)
        AnimatedVisibility(
            visible = swipeHudText != null && doubleTapSeekNotice == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            val hudColor = if (isSwipeHudBrightness) WpWarning else WpSecondary
            Column(
                modifier = Modifier
                    .width(190.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(WpBackground.copy(alpha = 0.88f))
                    .border(1.dp, hudColor.copy(alpha = 0.7f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = swipeHudText.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    color = WpTextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { swipeHudFraction.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = hudColor,
                    trackColor = WpDivider
                )
            }
        }

        // Center 3-2-1 Countdown Overlay
        CountdownOverlay(
            countdownNumber = countdownNumber,
            modifier = Modifier.align(Alignment.Center)
        )

        // Interactive Video Controls Overlay
        AnimatedVisibility(
            visible = controlsVisible && countdownNumber == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.42f))
            ) {
                // Top-left Lock button
                IconButton(
                    onClick = { isControlsLocked = !isControlsLocked },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(WpBackground.copy(alpha = 0.7f))
                ) {
                    Icon(
                        imageVector = if (isControlsLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                        contentDescription = "Lock Controls",
                        tint = if (isControlsLocked) WpWarning else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (!isControlsLocked) {
                    if (isHost) {
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    val target = (globalPositionMs - 10_000L).coerceAtLeast(0L)
                                    onSeekTo(target)
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(WpBackground.copy(alpha = 0.7f))
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Replay10,
                                    contentDescription = "Rewind 10s",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            IconButton(
                                onClick = onPlayPauseToggle,
                                modifier = Modifier
                                    .size(58.dp)
                                    .clip(CircleShape)
                                    .background(WpPrimary)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    val target = (globalPositionMs + 10_000L).coerceAtMost(totalDurationMs.coerceAtLeast(10_000L))
                                    onSeekTo(target)
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(WpBackground.copy(alpha = 0.7f))
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Forward10,
                                    contentDescription = "Forward 10s",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    // Bottom-right Video Overlay Action Bar (Aspect Ratio, Advanced Settings, Fullscreen / Rotate)
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(WpBackground.copy(alpha = 0.78f))
                                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                                .clickable(onClick = onCycleResizeMode)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AspectRatio,
                                contentDescription = "Aspect Ratio",
                                tint = WpSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = resizeModeLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(WpBackground.copy(alpha = 0.78f))
                                .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                                .clickable(onClick = onOpenAdvancedSettings),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Tune,
                                contentDescription = "Advanced Player Options",
                                tint = Color.White,
                                modifier = Modifier.size(17.dp)
                            )
                        }

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(WpPrimary)
                                .clickable(onClick = onToggleFullScreen)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("fullscreen_toggle_button"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isFullScreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                contentDescription = "Toggle Full Screen Orientation",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isFullScreen) "Exit Full" else "Full Screen",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaybackTransportSection(
    isHost: Boolean,
    isPlaying: Boolean,
    globalPositionMs: Long,
    totalDurationMs: Long,
    canStartAnyway: Boolean,
    onPlayPauseToggle: () -> Unit,
    onStartAnywayClick: () -> Unit,
    onHostSeekTo: (Long) -> Unit,
    onToggleFullScreen: () -> Unit,
    onCycleResizeMode: () -> Unit,
    resizeModeLabel: String
) {
    val safeTotal = totalDurationMs.coerceAtLeast(1L)
    val progressFraction = (globalPositionMs.toFloat() / safeTotal.toFloat()).coerceIn(0f, 1f)
    var sliderDragPos by remember(globalPositionMs) { mutableFloatStateOf(progressFraction) }
    var isDragging by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(WpCard)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatTimeMs(globalPositionMs),
                style = MaterialTheme.typography.bodySmall,
                color = WpTextPrimary,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.width(10.dp))

            if (isHost) {
                Slider(
                    value = if (isDragging) sliderDragPos else progressFraction,
                    onValueChange = {
                        isDragging = true
                        sliderDragPos = it
                    },
                    onValueChangeFinished = {
                        isDragging = false
                        val targetMs = (sliderDragPos * safeTotal).toLong()
                        onHostSeekTo(targetMs)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(24.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = WpPrimary,
                        activeTrackColor = WpPrimary,
                        inactiveTrackColor = WpDivider
                    )
                )
            } else {
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = WpSecondary,
                    trackColor = WpDivider
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = formatTimeMs(totalDurationMs),
                style = MaterialTheme.typography.bodySmall,
                color = WpTextMuted
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (isHost) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onPlayPauseToggle,
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("host_play_pause_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = WpPrimary)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isPlaying) "Pause All" else "Resume All",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (canStartAnyway) {
                        OutlinedButton(
                            onClick = onStartAnywayClick,
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("start_anyway_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FastForward,
                                contentDescription = null,
                                tint = WpSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.start_anyway),
                                style = MaterialTheme.typography.bodySmall,
                                color = WpSecondary
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "HD Synchronized Playback",
                    style = MaterialTheme.typography.bodySmall,
                    color = WpSuccess,
                    fontWeight = FontWeight.Medium
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(WpSurface)
                        .border(1.dp, WpDivider, RoundedCornerShape(8.dp))
                        .clickable(onClick = onCycleResizeMode)
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.AspectRatio,
                        contentDescription = "Screen Fit",
                        tint = WpSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = resizeModeLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = WpTextPrimary
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(WpPrimary.copy(alpha = 0.2f))
                        .border(1.dp, WpPrimary, RoundedCornerShape(8.dp))
                        .clickable(onClick = onToggleFullScreen)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.ScreenRotation,
                        contentDescription = "Full Screen Rotate",
                        tint = WpTextPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Full Screen",
                        style = MaterialTheme.typography.bodySmall,
                        color = WpTextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun AdvancedPlayerSettingsDialog(
    videoVolume: Float,
    voiceVolume: Float,
    isAutoDuckEnabled: Boolean,
    playbackSpeed: Float,
    screenBrightness: Float,
    selectedFilter: CinemaFilterMode,
    resizeModeLabel: String,
    isHost: Boolean,
    onVideoVolumeChange: (Float) -> Unit,
    onVoiceVolumeChange: (Float) -> Unit,
    onToggleAutoDuck: () -> Unit,
    onPlaybackSpeedChange: (Float) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onSelectFilter: (CinemaFilterMode) -> Unit,
    onCycleResizeMode: () -> Unit,
    onRotateScreen: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = WpCard,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Pro Cinema & Voice Controls",
                    style = MaterialTheme.typography.titleLarge,
                    color = WpTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Movie Audio Volume
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            tint = WpPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Movie Volume: ${(videoVolume * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = WpTextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Slider(
                        value = videoVolume,
                        onValueChange = onVideoVolumeChange,
                        colors = SliderDefaults.colors(
                            thumbColor = WpPrimary,
                            activeTrackColor = WpPrimary
                        )
                    )
                }

                // 2. Voice Sharing Volume
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.RecordVoiceOver,
                            contentDescription = null,
                            tint = WpSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Friends Voice Volume: ${(voiceVolume * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = WpTextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Slider(
                        value = voiceVolume,
                        onValueChange = onVoiceVolumeChange,
                        colors = SliderDefaults.colors(
                            thumbColor = WpSecondary,
                            activeTrackColor = WpSecondary
                        )
                    )
                }

                // 3. Auto-Ducking Toggle (Lower Movie Volume automatically when someone speaks!)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(WpSurface)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.GraphicEq,
                            contentDescription = null,
                            tint = WpSuccess,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Smart Voice Ducking",
                                style = MaterialTheme.typography.bodyMedium,
                                color = WpTextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Bolte waqt movie volume auto-low karein",
                                style = MaterialTheme.typography.bodySmall,
                                color = WpTextMuted
                            )
                        }
                    }
                    Switch(
                        checked = isAutoDuckEnabled,
                        onCheckedChange = { onToggleAutoDuck() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = WpSuccess
                        )
                    )
                }

                // 4. Screen Brightness
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.BrightnessMedium,
                            contentDescription = null,
                            tint = WpWarning,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Screen Brightness: ${(screenBrightness * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = WpTextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Slider(
                        value = screenBrightness,
                        onValueChange = onBrightnessChange,
                        valueRange = 0.1f..1.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = WpWarning,
                            activeTrackColor = WpWarning
                        )
                    )
                }

                // 5. Playback Speed (Host)
                if (isHost) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Speed,
                            contentDescription = null,
                            tint = WpSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Playback Speed:",
                            style = MaterialTheme.typography.bodySmall,
                            color = WpTextMuted
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(0.75f, 1.0f, 1.25f, 1.5f).forEach { spd ->
                            val isSelected = spd == playbackSpeed
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) WpSecondary.copy(alpha = 0.22f) else WpSurface)
                                    .border(1.dp, if (isSelected) WpSecondary else WpDivider, RoundedCornerShape(8.dp))
                                    .clickable { onPlaybackSpeedChange(spd) }
                                    .padding(vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${spd}x",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSelected) WpSecondary else WpTextMuted,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                // 6. Cinema Filter Presets
                Text(
                    text = "Cinema Color Mode:",
                    style = MaterialTheme.typography.bodySmall,
                    color = WpTextMuted
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CinemaFilterMode.entries.forEach { mode ->
                        val isSelected = mode == selectedFilter
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) WpPrimary else WpSurface)
                                .border(1.dp, if (isSelected) WpPrimary else WpDivider, RoundedCornerShape(8.dp))
                                .clickable { onSelectFilter(mode) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = mode.label.substringBefore(" "),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSelected) Color.White else WpTextMuted,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                // 7. Quick Screen Aspect & Orientation Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onCycleResizeMode,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Fit: $resizeModeLabel", color = WpSecondary)
                    }
                    Button(
                        onClick = {
                            onDismiss()
                            onRotateScreen()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = WpPrimary)
                    ) {
                        Text("Rotate Full", color = Color.White)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = WpSecondary, fontWeight = FontWeight.Bold)
            }
        }
    )
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private fun formatTimeMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    return String.format(Locale.US, "%02d:%02d", mins, secs)
}
