package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ShieldDarkColorScheme = darkColorScheme(
    primary = ShieldCyan,
    onPrimary = ShieldObsidian,
    secondary = ShieldEmerald,
    onSecondary = ShieldObsidian,
    tertiary = ShieldAmber,
    error = ShieldCrimson,
    onError = TextPrimary,
    background = ShieldObsidian,
    onBackground = TextPrimary,
    surface = ShieldSurface,
    onSurface = TextPrimary,
    surfaceVariant = ShieldSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = ShieldOutline
)

@Composable
fun ShieldGuardTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ShieldDarkColorScheme,
        typography = Typography,
        content = content
    )
}
