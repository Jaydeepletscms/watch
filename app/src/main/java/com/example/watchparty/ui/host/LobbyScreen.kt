package com.example.watchparty.ui.host

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.WpBackground
import com.example.ui.theme.WpCard
import com.example.ui.theme.WpDivider
import com.example.ui.theme.WpPrimary
import com.example.ui.theme.WpSecondary
import com.example.ui.theme.WpSuccess
import com.example.ui.theme.WpSurface
import com.example.ui.theme.WpTextMuted
import com.example.ui.theme.WpTextPrimary
import com.example.ui.theme.WpWarning
import com.example.watchparty.net.ParticipantDto
import com.example.watchparty.net.Protocol
import com.example.watchparty.util.Qr

@Composable
fun LobbyScreen(
    isHost: Boolean,
    roomCode: String,
    roomPassword: String = "",
    hostIp: String,
    port: Int = 8080,
    participants: List<ParticipantDto>,
    isRoomLocked: Boolean,
    onToggleLock: () -> Unit,
    onAddSimulatedParticipant: () -> Unit,
    onStartPartyClick: () -> Unit,
    onLeaveLobbyClick: () -> Unit
) {
    BackHandler(onBack = onLeaveLobbyClick)

    val qrBitmap: Bitmap = remember(hostIp, port, roomCode, roomPassword) {
        val payload = Qr.encodePayload(
            ip = hostIp,
            port = port,
            roomCode = roomCode,
            password = roomPassword
        )
        Qr.generateQrBitmap(payload, 480)
    }

    // Section 11.5: Start enabled when at least 1 participant (besides host) has joined
    val nonHostCount = participants.count { !it.isHost }
    val canStartParty = isHost && nonHostCount >= 1

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WpBackground)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .widthIn(max = 540.dp)
                .align(Alignment.TopCenter)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onLeaveLobbyClick,
                        modifier = Modifier.testTag("lobby_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Leave Lobby",
                            tint = WpTextPrimary
                        )
                    }
                    Text(
                        text = "Lobby",
                        style = MaterialTheme.typography.headlineMedium,
                        color = WpTextPrimary
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(WpPrimary.copy(alpha = 0.2f))
                        .border(1.dp, WpPrimary, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Room $roomCode",
                        style = MaterialTheme.typography.titleMedium,
                        color = WpTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // QR Code & WS URL Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = WpCard)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(196.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "Room QR Code",
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "ws://$hostIp:$port",
                        style = MaterialTheme.typography.titleMedium,
                        color = WpSecondary,
                        fontWeight = FontWeight.SemiBold
                    )

                    if (roomPassword.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(WpWarning.copy(alpha = 0.16f))
                                .border(1.dp, WpWarning.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = null,
                                tint = WpWarning,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Password: $roomPassword",
                                style = MaterialTheme.typography.bodySmall,
                                color = WpWarning,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (isHost) {
                            "Auto-Finder on hai! Dost Join screen kholte hi 1-tap mein ya QR scan karke jud sakte hain."
                        } else {
                            "Host ke party start karne ka intezar ho raha hai..."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = WpTextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Participants Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = WpCard)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Participants (${participants.size}/${Protocol.MAX_ROOM_PARTICIPANTS})",
                            style = MaterialTheme.typography.titleMedium,
                            color = WpTextPrimary
                        )
                        if (isRoomLocked) {
                            Text(
                                text = "LOCKED",
                                style = MaterialTheme.typography.bodySmall,
                                color = WpWarning,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    participants.forEach { p ->
                        ParticipantLobbyRow(participant = p)
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (isHost && participants.size < Protocol.MAX_ROOM_PARTICIPANTS) {
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(
                            onClick = onAddSimulatedParticipant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("add_simulated_participant_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PersonAdd,
                                contentDescription = null,
                                tint = WpSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "+ Add Simulated Friend (Loopback WS Client)",
                                color = WpSecondary,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (isHost) {
                OutlinedButton(
                    onClick = onToggleLock,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("lock_room_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (isRoomLocked) WpWarning else WpTextPrimary
                    )
                ) {
                    Icon(
                        imageVector = if (isRoomLocked) Icons.Filled.LockOpen else Icons.Filled.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isRoomLocked) stringResource(R.string.unlock_room) else stringResource(R.string.lock_room),
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onStartPartyClick,
                    enabled = canStartParty,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("start_party_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WpPrimary,
                        disabledContainerColor = WpPrimary.copy(alpha = 0.35f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (canStartParty) {
                            stringResource(R.string.start_party)
                        } else {
                            "Start party (Waiting for 1+ participant)"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun ParticipantLobbyRow(participant: ParticipantDto) {
    val initial = participant.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "U"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(WpSurface)
            .border(1.dp, WpDivider, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (participant.isHost) WpPrimary else WpSecondary.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (participant.isHost) Color.White else WpSecondary,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = participant.name,
                style = MaterialTheme.typography.bodyLarge,
                color = WpTextPrimary,
                fontWeight = FontWeight.Medium
            )
        }

        Text(
            text = if (participant.isHost) "Host" else "Joined",
            style = MaterialTheme.typography.bodySmall,
            color = if (participant.isHost) WpPrimary else WpSuccess,
            fontWeight = FontWeight.SemiBold
        )
    }
}
