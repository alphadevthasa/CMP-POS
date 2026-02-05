package com.pos.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import kotlin.math.abs

private val PosColors = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCFBF1),
    onPrimaryContainer = Color(0xFF0B3B36),
    secondary = Color(0xFF475569),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E8F0),
    onSecondaryContainer = Color(0xFF1E293B),
    background = Color(0xFFF1F5F9),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    error = Color(0xFFDC2626),
    onError = Color.White,
)

private val AvatarPalette = listOf(
    Color(0xFFE0F2F1),
    Color(0xFFE3F2FD),
    Color(0xFFFFF3E0),
    Color(0xFFFCE4EC),
    Color(0xFFF3E5F5),
    Color(0xFFE8F5E9),
    Color(0xFFFFF8E1),
    Color(0xFFECEFF1),
)

val AvatarText = Color(0xFF334155)

fun productAvatarColor(name: String): Color =
    AvatarPalette[abs(name.hashCode()) % AvatarPalette.size]

@Composable
fun PosTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = PosColors, content = content)
}
