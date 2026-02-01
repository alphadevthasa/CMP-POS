package com.pos.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PosColors = lightColorScheme(
    primary = Color(0xFF1F6F5C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA7F0D8),
    onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFF4B635B),
    surface = Color(0xFFFBFDFA),
    background = Color(0xFFF2F6F3),
    error = Color(0xFFBA1A1A),
)

@Composable
fun PosTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = PosColors, content = content)
}
