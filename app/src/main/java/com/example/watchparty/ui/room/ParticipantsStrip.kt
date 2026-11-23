package com.example.watchparty.ui.room

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ui.theme.WpCard
import com.example.ui.theme.WpDivider
import com.example.ui.theme.WpError
import com.example.ui.theme.WpPrimary
import com.example.ui.theme.WpSecondary
import com.example.ui.theme.WpSuccess
import com.example.ui.theme.WpSurface
import com.example.ui.theme.WpTextMuted
import com.example.ui.theme.WpTextPrimary
import com.example.watchparty.net.ParticipantDto

/**
 * Participants & Live Voice Sharing Strip
 * Shows live speaking glow rings, Mic On/Off button (with runtime RECORD_AUDIO permission),
 * Speaker Mute/Unmute toggle, and connected participants without any backend/chunk numbers.
 */
@Composable
fun ParticipantsStrip(
    participants: List<ParticipantDto>,
    isHost: Boolean,
    isMicOn: Boolean,
    isLocalSpeaking: Boolean,
    isSpeakerOn: Boolean,
    activeRemoteSpeaker: String?,
    onToggleMic: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onParticipantClick: (ParticipantDto) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onToggleMic()
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(WpSurface)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Live Voice Controls (Mic Toggle + Speaker Toggle)
        val micBg by animateColorAsState(
            targetValue = when {
                isLocalSpeaking -> WpSuccess
                isMicOn -> WpPrimary
                else -> WpCard
            },
            label = "micBg"
        )

        Row(
            modifier = Modifier
                .height(38.dp)
                .clip(RoundedCornerShape(19.dp))
                .background(micBg)
                .border(
                    width = 1.dp,
                    color = if (isMicOn) Color.White.copy(alpha = 0.3f) else WpDivider,
                    shape = RoundedCornerShape(19.dp)
                )
                .clickable {
                    if (isMicOn) {
                        onToggleMic()
                    } else {
                        val hasPerm = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasPerm) {
                            onToggleMic()
                        } else {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                }
                .padding(horizontal = 12.dp)
                .testTag("voice_mic_toggle_button"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isMicOn) Icons.Filled.Mic else Icons.Filled.MicOff,
                contentDescription = if (isMicOn) "Mute Mic" else "Unmute Mic",
                tint = if (isMicOn) Color.White else WpTextMuted,
                modifier = Modifier.size(17.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = when {
                    isLocalSpeaking -> "Speaking"
                    isMicOn -> "Mic On"
                    else -> "Voice"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (isMicOn) Color.White else WpTextPrimary,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Speaker Deaf/Unmute Button
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(WpCard)
                .border(1.dp, if (isSpeakerOn) WpSecondary.copy(alpha = 0.5f) else WpError.copy(alpha = 0.5f), CircleShape)
                .clickable(onClick = onToggleSpeaker)
                .testTag("voice_speaker_toggle_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                contentDescription = if (isSpeakerOn) "Mute Incoming Voice" else "Unmute Incoming Voice",
                tint = if (isSpeakerOn) WpSecondary else WpError,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // 2. Scrollable Participants Row
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            participants.forEach { participant ->
                val cleanName = participant.name.removeSuffix(" (Host)")
                val initial = cleanName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "U"
                val isSpeakingNow = participant.isSpeaking ||
                    (activeRemoteSpeaker != null && activeRemoteSpeaker.equals(participant.name, ignoreCase = true))

                val borderColor by animateColorAsState(
                    targetValue = when {
                        isSpeakingNow -> WpSuccess
                        participant.isMicOn -> WpSecondary.copy(alpha = 0.7f)
                        else -> WpDivider
                    },
                    label = "participantBorder"
                )

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSpeakingNow) WpSuccess.copy(alpha = 0.14f) else WpCard)
                        .border(
                            width = if (isSpeakingNow) 1.5.dp else 1.dp,
                            color = borderColor,
                            shape = RoundedCornerShape(20.dp)
                        )
                        .clickable(enabled = isHost && !participant.isHost) {
                            onParticipantClick(participant)
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(if (participant.isHost) WpPrimary else WpSecondary.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initial,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (participant.isHost) Color.White else WpSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = cleanName,
                        style = MaterialTheme.typography.bodySmall,
                        color = WpTextPrimary,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.width(5.dp))

                    when {
                        isSpeakingNow -> {
                            Icon(
                                imageVector = Icons.Filled.GraphicEq,
                                contentDescription = "Speaking",
                                tint = WpSuccess,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        participant.isMicOn -> {
                            Icon(
                                imageVector = Icons.Filled.Mic,
                                contentDescription = "Mic On",
                                tint = WpSecondary,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        else -> {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(WpSuccess)
                            )
                        }
                    }
                }
            }
        }
    }
}
