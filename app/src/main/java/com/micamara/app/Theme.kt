package com.micamara.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AppColors = darkColorScheme(
    primary = Color(0xFF4FD1C5),
    onPrimary = Color(0xFF00201D),
    secondary = Color(0xFF7C9CFF),
    background = Color(0xFF0B1220),
    onBackground = Color(0xFFE6ECF5),
    surface = Color(0xFF131C2E),
    onSurface = Color(0xFFE6ECF5),
    surfaceVariant = Color(0xFF1B2740),
    onSurfaceVariant = Color(0xFFA9B6CC),
    outline = Color(0xFF2C3A57),
    error = Color(0xFFFF6B6B),
)

@Composable
fun MiCamaraTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AppColors, content = content)
}
