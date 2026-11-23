package com.example.watchparty.ui.room

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.WpBackground
import com.example.ui.theme.WpPrimary
import com.example.ui.theme.WpSecondary
import com.example.ui.theme.WpTextPrimary
import com.example.ui.theme.WpWarning

@Composable
fun StatusBanner(
    bannerText: String?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = !bannerText.isNullOrBlank(),
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        val text = bannerText.orEmpty()
        val isPause = text.contains("pause", ignoreCase = true)
        val isSync = text.contains("sync", ignoreCase = true)
        val accentColor = when {
            isPause -> WpWarning
            isSync -> WpSecondary
            else -> WpPrimary
        }

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(WpBackground.copy(alpha = 0.86f))
                .border(1.dp, accentColor.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when {
                isPause -> Icon(
                    imageVector = Icons.Filled.PauseCircle,
                    contentDescription = null,
                    tint = WpWarning,
                    modifier = Modifier.size(18.dp)
                )
                isSync -> Icon(
                    imageVector = Icons.Filled.Sync,
                    contentDescription = null,
                    tint = WpSecondary,
                    modifier = Modifier.size(18.dp)
                )
                text.contains("%") -> CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = WpSecondary
                )
                else -> Icon(
                    imageVector = Icons.Filled.CloudDownload,
                    contentDescription = null,
                    tint = WpPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = WpTextPrimary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun CountdownOverlay(
    countdownNumber: Int?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = countdownNumber != null,
        enter = scaleIn() + fadeIn(),
        exit = scaleOut() + fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(WpPrimary.copy(alpha = 0.88f))
                .border(3.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = (countdownNumber ?: 1).toString(),
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}
