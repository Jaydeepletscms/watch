package com.example.watchparty.ui.join

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
import com.example.watchparty.net.DiscoveredRoom

@Composable
fun JoinScreen(
    hostIp: String,
    roomCode: String,
    roomPassword: String,
    discoveredRooms: List<DiscoveredRoom>,
    isScanningRooms: Boolean,
    isConnecting: Boolean,
    errorMessage: String?,
    onHostIpChange: (String) -> Unit,
    onRoomCodeChange: (String) -> Unit,
    onRoomPasswordChange: (String) -> Unit,
    onSelectDiscoveredRoom: (DiscoveredRoom, String) -> Unit,
    onOpenQrScanner: () -> Unit,
    onJoinClick: () -> Unit,
    onBackClick: () -> Unit
) {
    BackHandler(onBack = onBackClick)
    val context = LocalContext.current
    var cameraPermissionDenied by remember { mutableStateOf(false) }
    var passwordPromptRoom by remember { mutableStateOf<DiscoveredRoom?>(null) }
    var promptPasswordInput by remember { mutableStateOf("") }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            cameraPermissionDenied = false
            onOpenQrScanner()
        } else {
            cameraPermissionDenied = true
        }
    }

    // Password Dialog when tapping a password-protected room from Auto-Finder
    passwordPromptRoom?.let { targetRoom ->
        AlertDialog(
            onDismissRequest = { passwordPromptRoom = null },
            containerColor = WpCard,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = WpWarning,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Enter Password for Room ${targetRoom.roomCode}",
                        style = MaterialTheme.typography.titleMedium,
                        color = WpTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = "${targetRoom.hostName} ke room mein join karne ke liye password daalein:",
                        style = MaterialTheme.typography.bodySmall,
                        color = WpTextMuted
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = promptPasswordInput,
                        onValueChange = { promptPasswordInput = it },
                        label = { Text("Room Password / PIN") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("password_prompt_input"),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WpSecondary,
                            unfocusedBorderColor = WpDivider,
                            focusedContainerColor = WpSurface,
                            unfocusedContainerColor = WpSurface
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pwd = promptPasswordInput.trim()
                        passwordPromptRoom = null
                        onSelectDiscoveredRoom(targetRoom, pwd)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WpPrimary)
                ) {
                    Text("Join Party", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { passwordPromptRoom = null }) {
                    Text("Cancel", color = WpTextMuted)
                }
            }
        )
    }

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
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.testTag("join_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = WpTextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.join_party),
                    style = MaterialTheme.typography.headlineMedium,
                    color = WpTextPrimary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 1. AUTO ROOM FINDER ON WIFI / HOTSPOT CARD
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = WpCard)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.WifiTethering,
                                contentDescription = null,
                                tint = WpSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Nearby Rooms on WiFi (Auto-Finder)",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = WpTextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Same WiFi ya Hotspot ke rooms yahan apne-aap dikhenge",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = WpTextMuted
                                )
                            }
                        }

                        if (isScanningRooms) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = WpSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (discoveredRooms.isEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(WpSurface)
                                .border(1.dp, WpDivider, RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Radar,
                                contentDescription = null,
                                tint = WpSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Scanning WiFi & Hotspot for active Watch Parties...",
                                style = MaterialTheme.typography.bodySmall,
                                color = WpTextMuted
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            discoveredRooms.forEach { room ->
                                DiscoveredRoomRow(
                                    room = room,
                                    onJoinRoomClick = {
                                        if (room.hasPassword) {
                                            promptPasswordInput = roomPassword
                                            passwordPromptRoom = room
                                        } else {
                                            onSelectDiscoveredRoom(room, "")
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Manual & QR Join Card
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
                    OutlinedButton(
                        onClick = {
                            val hasPerm = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasPerm) {
                                onOpenQrScanner()
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("scan_qr_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = WpSecondary)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.QrCodeScanner,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.scan_qr_code),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    if (cameraPermissionDenied) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Camera permission denied. Niche Host IP aur Room code manually daalein.",
                            style = MaterialTheme.typography.bodySmall,
                            color = WpError
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = WpDivider)
                        Text(
                            text = "  ya manually connect karein  ",
                            style = MaterialTheme.typography.bodySmall,
                            color = WpTextMuted
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f), color = WpDivider)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Host IP input
                    OutlinedTextField(
                        value = hostIp,
                        onValueChange = onHostIpChange,
                        label = { Text(stringResource(R.string.host_ip_label)) },
                        placeholder = { Text("192.168.1.5", color = WpTextMuted) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Dns,
                                contentDescription = null,
                                tint = WpSecondary
                            )
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("host_ip_input"),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WpPrimary,
                            unfocusedBorderColor = WpDivider,
                            focusedContainerColor = WpSurface,
                            unfocusedContainerColor = WpSurface
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Room code input
                    OutlinedTextField(
                        value = roomCode,
                        onValueChange = { onRoomCodeChange(it.uppercase()) },
                        label = { Text(stringResource(R.string.room_code_label)) },
                        placeholder = { Text("4F2", color = WpTextMuted) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Key,
                                contentDescription = null,
                                tint = WpPrimary
                            )
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("room_code_input"),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Next
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WpPrimary,
                            unfocusedBorderColor = WpDivider,
                            focusedContainerColor = WpSurface,
                            unfocusedContainerColor = WpSurface
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Optional Room Password input
                    OutlinedTextField(
                        value = roomPassword,
                        onValueChange = onRoomPasswordChange,
                        label = { Text("Room Password (If Required)") },
                        placeholder = { Text("Leave empty for open rooms", color = WpTextMuted) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = null,
                                tint = if (roomPassword.isNotBlank()) WpWarning else WpTextMuted
                            )
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("join_room_password_input"),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WpSecondary,
                            unfocusedBorderColor = WpDivider,
                            focusedContainerColor = WpSurface,
                            unfocusedContainerColor = WpSurface
                        )
                    )

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = WpError.copy(alpha = 0.14f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = errorMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = WpError,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = onJoinClick,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Refresh,
                                        contentDescription = "Retry",
                                        tint = WpError
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    val canJoin = hostIp.trim().length >= 7 && !isConnecting
                    Button(
                        onClick = onJoinClick,
                        enabled = canJoin,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("join_submit_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WpPrimary,
                            disabledContainerColor = WpPrimary.copy(alpha = 0.35f)
                        )
                    ) {
                        if (isConnecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Connecting to Host...",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Login,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.join_btn),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoveredRoomRow(
    room: DiscoveredRoom,
    onJoinRoomClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(WpSurface)
            .border(1.dp, WpSecondary.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .clickable(onClick = onJoinRoomClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("discovered_room_${room.roomCode}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(WpPrimary.copy(alpha = 0.22f))
                    .border(1.dp, WpPrimary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = room.roomCode,
                    style = MaterialTheme.typography.bodySmall,
                    color = WpTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${room.hostName}'s Party",
                        style = MaterialTheme.typography.titleMedium,
                        color = WpTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = if (room.hasPassword) Icons.Filled.Lock else Icons.Filled.LockOpen,
                        contentDescription = if (room.hasPassword) "Password Protected" else "Open Room",
                        tint = if (room.hasPassword) WpWarning else WpSuccess,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Text(
                    text = "Code: ${room.roomCode} • ${room.participantCount}/${room.maxParticipants} online • ${room.hostIp}",
                    style = MaterialTheme.typography.bodySmall,
                    color = WpTextMuted
                )
            }
        }

        Button(
            onClick = onJoinRoomClick,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = WpPrimary),
            modifier = Modifier.height(38.dp)
        ) {
            Text(
                text = if (room.hasPassword) "🔒 Join" else "Join",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
