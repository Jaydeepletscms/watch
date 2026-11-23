package com.example.watchparty.ui.room

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.WpBackground
import com.example.ui.theme.WpCard
import com.example.ui.theme.WpDivider
import com.example.ui.theme.WpPrimary
import com.example.ui.theme.WpPrimaryVariant
import com.example.ui.theme.WpSecondary
import com.example.ui.theme.WpSurface
import com.example.ui.theme.WpTextMuted
import com.example.ui.theme.WpTextPrimary
import com.example.ui.theme.WpWarning
import com.example.watchparty.media.SoundEffectItem
import com.example.watchparty.media.SoundboardEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ChatUiMessage(
    val id: Long,
    val from: String,
    val text: String,
    val timestamp: Long,
    val isMe: Boolean = false,
    val isSystem: Boolean = false,
    val replyToText: String? = null
)

private val QUICK_EMOJIS = listOf("❤️", "🔥", "👏", "😂", "😮", "🎉")
private val QUICK_CHATS = listOf(
    "Kya scene hai 🔥",
    "Haha 😂",
    "Wait karo ⏸️",
    "Mast movie hai 🍿"
)

@Composable
fun ChatPanel(
    messages: List<ChatUiMessage>,
    onSendMessage: (text: String, replyTo: String?) -> Unit,
    onSendReaction: (String) -> Unit,
    onSendSoundEffect: (SoundEffectItem) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var draft by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<ChatUiMessage?>(null) }
    var showSoundboardStrip by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(WpBackground)
            .imePadding()
    ) {
        // 1. Messages List
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(WpCard),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ChatBubbleOutline,
                            contentDescription = null,
                            tint = WpSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Watch Party Live Chat & Soundboard",
                        style = MaterialTheme.typography.titleMedium,
                        color = WpTextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Niche 📢 SFX dabake Airhorn/Taliyan bajao ya dosto ko message bhejo!",
                        style = MaterialTheme.typography.bodySmall,
                        color = WpTextMuted
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    ChatMessageBubble(
                        message = msg,
                        onReplyClick = { replyingTo = msg }
                    )
                }
            }
        }

        // 2. Expandable Live Soundboard (Meme & Theatre Sound Effects) Strip
        AnimatedVisibility(visible = showSoundboardStrip) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WpCard)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SoundboardEngine.SOUNDBOARD_ITEMS.forEach { sfx ->
                    Row(
                        modifier = Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(WpWarning.copy(alpha = 0.16f))
                            .border(1.dp, WpWarning.copy(alpha = 0.65f), RoundedCornerShape(18.dp))
                            .clickable { onSendSoundEffect(sfx) }
                            .padding(horizontal = 12.dp)
                            .testTag("soundboard_btn_${sfx.id}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = sfx.emoji, fontSize = 15.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = sfx.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = WpTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 3. Quick Reactions, Soundboard Toggle & Quick Phrases Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(WpSurface)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Live Soundboard Toggle Chip
            Row(
                modifier = Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(if (showSoundboardStrip) WpWarning else WpWarning.copy(alpha = 0.18f))
                    .border(1.dp, WpWarning, RoundedCornerShape(17.dp))
                    .clickable { showSoundboardStrip = !showSoundboardStrip }
                    .padding(horizontal = 11.dp)
                    .testTag("toggle_soundboard_button"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Campaign,
                    contentDescription = "Live Soundboard",
                    tint = if (showSoundboardStrip) Color.Black else WpWarning,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "SFX 📢",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (showSoundboardStrip) Color.Black else WpWarning,
                    fontWeight = FontWeight.Bold
                )
            }

            QUICK_EMOJIS.forEachIndexed { idx, emoji ->
                Box(
                    modifier = Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(WpCard)
                        .border(1.dp, WpDivider, RoundedCornerShape(17.dp))
                        .clickable { onSendReaction(emoji) }
                        .padding(horizontal = 11.dp)
                        .testTag("reaction_button_$idx"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = emoji, fontSize = 16.sp)
                }
            }

            QUICK_CHATS.forEach { phrase ->
                Box(
                    modifier = Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(WpPrimary.copy(alpha = 0.14f))
                        .border(1.dp, WpPrimary.copy(alpha = 0.4f), RoundedCornerShape(17.dp))
                        .clickable { onSendMessage(phrase, null) }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = phrase,
                        style = MaterialTheme.typography.bodySmall,
                        color = WpTextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // 4. Reply Preview Bar (when replying to a message)
        AnimatedVisibility(visible = replyingTo != null) {
            replyingTo?.let { target ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(WpCard)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Reply,
                            contentDescription = null,
                            tint = WpSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Replying to ${target.from}: ${target.text}",
                            style = MaterialTheme.typography.bodySmall,
                            color = WpSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = { replyingTo = null },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Cancel Reply",
                            tint = WpTextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // 5. Input Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(WpSurface)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = {
                    Text(
                        text = stringResource(R.string.chat_placeholder),
                        style = MaterialTheme.typography.bodyMedium,
                        color = WpTextMuted
                    )
                },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_input_field"),
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (draft.isNotBlank()) {
                            val replySummary = replyingTo?.let { "${it.from}: ${it.text}" }
                            onSendMessage(draft.trim(), replySummary)
                            draft = ""
                            replyingTo = null
                        }
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = WpPrimary,
                    unfocusedBorderColor = WpDivider,
                    focusedContainerColor = WpCard,
                    unfocusedContainerColor = WpCard
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = {
                    if (draft.isNotBlank()) {
                        val replySummary = replyingTo?.let { "${it.from}: ${it.text}" }
                        onSendMessage(draft.trim(), replySummary)
                        draft = ""
                        replyingTo = null
                    }
                },
                enabled = draft.isNotBlank(),
                modifier = Modifier
                    .size(48.dp)
                    .testTag("chat_send_button"),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = WpPrimary,
                    disabledContainerColor = WpPrimary.copy(alpha = 0.35f)
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.send),
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(
    message: ChatUiMessage,
    onReplyClick: () -> Unit
) {
    val timeStr = remember(message.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
    }

    if (message.isSystem) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodySmall,
                color = WpSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(WpSurface)
                    .border(1.dp, WpDivider, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
        return
    }

    val isMe = message.isMe
    val bubbleShape = if (isMe) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isMe) {
            val initial = message.from.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "U"
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(WpSecondary.copy(alpha = 0.22f))
                    .border(1.dp, WpSecondary.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.bodySmall,
                    color = WpSecondary,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(bubbleShape)
                .background(
                    if (isMe) {
                        Brush.linearGradient(listOf(WpPrimary, WpPrimaryVariant))
                    } else {
                        Brush.linearGradient(listOf(WpCard, WpCard))
                    }
                )
                .border(
                    width = 1.dp,
                    color = if (isMe) WpPrimary.copy(alpha = 0.4f) else WpDivider,
                    shape = bubbleShape
                )
                .clickable(onClick = onReplyClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
        ) {
            if (!isMe) {
                Text(
                    text = message.from,
                    style = MaterialTheme.typography.bodySmall,
                    color = WpSecondary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
            }

            if (!message.replyToText.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.25f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = message.replyToText,
                        style = MaterialTheme.typography.bodySmall,
                        color = WpTextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = timeStr,
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.65f)
            )
        }
    }
}
