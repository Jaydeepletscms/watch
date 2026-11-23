package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val WatchPartyColorScheme = darkColorScheme(
    primary = WpPrimary,
    onPrimary = Color.White,
    primaryContainer = WpCard,
    onPrimaryContainer = WpTextPrimary,
    secondary = WpSecondary,
    onSecondary = Color(0xFF0A1414),
    secondaryContainer = WpCard,
    onSecondaryContainer = WpSecondary,
    tertiary = WpSuccess,
    error = WpError,
    onError = Color.White,
    background = WpBackground,
    onBackground = WpTextPrimary,
    surface = WpSurface,
    onSurface = WpTextPrimary,
    surfaceVariant = WpCard,
    onSurfaceVariant = WpTextMuted,
    outline = WpDivider
)

val WatchPartyShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp), // 12 dp buttons
    large = RoundedCornerShape(16.dp),  // 16 dp cards
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun WatchPartyTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = WatchPartyColorScheme,
        typography = Typography,
        shapes = WatchPartyShapes,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    WatchPartyTheme(content = content)
}
