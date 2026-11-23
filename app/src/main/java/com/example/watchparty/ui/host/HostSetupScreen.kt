package com.example.watchparty.ui.host

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
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
import com.example.watchparty.media.VideoProbeResult
import java.util.Locale

@Composable
fun HostSetupScreen(
    probeResult: VideoProbeResult?,
    isGeneratingSample: Boolean,
    selectedChunkSec: Int,
    roomPassword: String,
    hasEnoughStorage: Boolean,
    onVideoPicked: (Uri) -> Unit,
    onGenerateSampleVideo: () -> Unit,
    onSelectChunkSec: (Int) -> Unit,
    onRoomPasswordChange: (String) -> Unit,
    onCreateRoomClick: () -> Unit,
    onBackClick: () -> Unit
) {
    BackHandler(onBack = onBackClick)
    val context = LocalContext.current

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            onVideoPicked(uri)
        }
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
            // Top Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.testTag("host_setup_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = WpTextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.create_party),
                    style = MaterialTheme.typography.headlineMedium,
                    color = WpTextPrimary
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Video Selection Card
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
                    Text(
                        text = "Movie ya Video Select Karein",
                        style = MaterialTheme.typography.titleMedium,
                        color = WpTextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Apne phone se koi bhi MP4 video select karein. Sabhi dosto ke phone par original HD quality mein bina lag ke chalegi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = WpTextMuted
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = { videoPickerLauncher.launch(arrayOf("video/mp4", "video/*")) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("select_video_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = WpPrimary)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.select_video),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = onGenerateSampleVideo,
                        enabled = !isGeneratingSample,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("generate_sample_video_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = WpSecondary)
                    ) {
                        if (isGeneratingSample) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = WpSecondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Preparing HD Sample Video...")
                        } else {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.generate_sample_video))
                        }
                    }

                    if (probeResult != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        VideoProbeBadge(probeResult = probeResult)
                    }

                    if (!hasEnoughStorage) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Phone storage kam hai! Kam se kam 100 MB free space rakhein.",
                            style = MaterialTheme.typography.bodySmall,
                            color = WpError
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Room Security & Password Card (Optional)
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
                    Text(
                        text = "Room Password (Optional)",
                        style = MaterialTheme.typography.titleMedium,
                        color = WpTextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Agar aap room ko private rakhna chahte hain toh PIN ya password set karein. Khali chhodne par WiFi ke dost direct join kar sakenge.",
                        style = MaterialTheme.typography.bodySmall,
                        color = WpTextMuted
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = roomPassword,
                        onValueChange = { if (it.length <= 16) onRoomPasswordChange(it) },
                        placeholder = {
                            Text("No password (Open Room on WiFi)", color = WpTextMuted)
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = null,
                                tint = if (roomPassword.isNotBlank()) WpSecondary else WpTextMuted
                            )
                        },
                        trailingIcon = {
                            if (roomPassword.isNotEmpty()) {
                                IconButton(onClick = { onRoomPasswordChange("") }) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Clear Password",
                                        tint = WpTextMuted
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("host_room_password_input"),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WpSecondary,
                            unfocusedBorderColor = WpDivider,
                            focusedContainerColor = WpSurface,
                            unfocusedContainerColor = WpSurface
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sync Quality Mode Selector (Replaces technical chunk selector with user-friendly Sync Mode)
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
                    Text(
                        text = stringResource(R.string.stream_quality_label),
                        style = MaterialTheme.typography.titleMedium,
                        color = WpTextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Apne WiFi ya Hotspot ke hisaab se sync mode chunein.",
                        style = MaterialTheme.typography.bodySmall,
                        color = WpTextMuted
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SyncModeChip(
                            label = stringResource(R.string.stream_instant),
                            selected = selectedChunkSec == 10,
                            onClick = { onSelectChunkSec(10) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sync_mode_fast")
                        )
                        SyncModeChip(
                            label = stringResource(R.string.stream_smooth),
                            selected = selectedChunkSec == 30,
                            onClick = { onSelectChunkSec(30) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sync_mode_smooth")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tips Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = WpSurface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Lightbulb,
                            contentDescription = null,
                            tint = WpSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Tips for Best Experience:",
                            style = MaterialTheme.typography.titleMedium,
                            color = WpTextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TipRow(text = "Auto Room Finder se dost bina IP type kiye 1-tap mein join kar sakte hain")
                    TipRow(text = "Room mein Live Voice, Soundboard aur Chat ek saath use karein")
                    TipRow(text = "Video par Left/Right Swipe karke Brightness aur Volume adjust karein")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            val canCreateRoom = probeResult?.isSupported == true && hasEnoughStorage
            Button(
                onClick = onCreateRoomClick,
                enabled = canCreateRoom,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("create_room_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = WpPrimary,
                    disabledContainerColor = WpPrimary.copy(alpha = 0.35f)
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayCircleFilled,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.create_room),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun VideoProbeBadge(probeResult: VideoProbeResult) {
    val borderColor = if (probeResult.isSupported) WpSuccess else WpError
    val bgColor = if (probeResult.isSupported) WpSuccess.copy(alpha = 0.1f) else WpError.copy(alpha = 0.1f)
    val sizeMb = String.format(Locale.US, "%.1f MB", probeResult.fileSizeBytes / (1024.0 * 1024.0))
    val durSec = probeResult.durationMs / 1000L

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.dp, borderColor.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Filled.VideoFile,
                    contentDescription = null,
                    tint = borderColor,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = probeResult.fileName,
                    style = MaterialTheme.typography.labelLarge,
                    color = WpTextPrimary
                )
            }
            Icon(
                imageVector = if (probeResult.isSupported) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = borderColor,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        if (probeResult.isSupported) {
            Text(
                text = "${probeResult.width}x${probeResult.height} • $sizeMb • ${durSec}s • Ready for HD Sync ✓",
                style = MaterialTheme.typography.bodySmall,
                color = WpSuccess,
                fontWeight = FontWeight.SemiBold
            )
        } else {
            Text(
                text = probeResult.errorMessage ?: stringResource(R.string.unsupported_format_error),
                style = MaterialTheme.typography.bodySmall,
                color = WpError,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SyncModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (selected) WpPrimary.copy(alpha = 0.22f) else WpSurface
    val border = if (selected) WpPrimary else WpDivider
    val textColor = if (selected) WpTextPrimary else WpTextMuted

    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.5.dp, border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun TipRow(text: String) {
    Row(
        modifier = Modifier.padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "•",
            color = WpSecondary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = text,
            color = WpTextMuted,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
