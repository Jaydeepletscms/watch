package com.example.watchparty.ui.room

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.WpBackground
import com.example.ui.theme.WpCard
import com.example.ui.theme.WpDivider
import com.example.ui.theme.WpSurface
import com.example.ui.theme.WpTextPrimary
import kotlin.math.roundToInt

data class FloatingReactionItem(
    val id: Long,
    val emoji: String,
    val sender: String,
    val horizontalFraction: Float // 0.1f .. 0.85f
)

private val PARTY_EMOJIS = listOf("❤️", "🔥", "👏", "😂", "😮", "🎉")

@Composable
fun ReactionsBar(
    onEmojiClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(WpSurface)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        PARTY_EMOJIS.forEachIndexed { index, emoji ->
            Box(
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(WpCard)
                    .border(1.dp, WpDivider, RoundedCornerShape(12.dp))
                    .clickable { onEmojiClick(emoji) }
                    .testTag("reaction_button_$index"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = emoji,
                    fontSize = 20.sp
                )
            }
        }
    }
}

@Composable
fun FloatingReactionsOverlay(
    reactions: List<FloatingReactionItem>,
    onReactionFinished: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        reactions.forEach { item ->
            key(item.id) {
                val progress = remember { Animatable(0f) }
                LaunchedEffect(item.id) {
                    progress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 1900, easing = LinearOutSlowInEasing)
                    )
                    onReactionFinished(item.id)
                }

                val yOffsetPx = -((progress.value * 360f).roundToInt())
                val alpha = (1f - (progress.value * 0.85f)).coerceIn(0f, 1f)
                val xBias = (item.horizontalFraction - 0.5f) * 240f

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset { IntOffset(xBias.roundToInt(), yOffsetPx - 24) }
                        .alpha(alpha),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = item.emoji,
                        fontSize = 28.sp
                    )
                    Text(
                        text = item.sender,
                        style = MaterialTheme.typography.bodySmall,
                        color = WpTextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(WpBackground.copy(alpha = 0.72f))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}
