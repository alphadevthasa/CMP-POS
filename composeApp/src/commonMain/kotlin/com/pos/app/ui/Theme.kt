package com.pos.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.pos.app.res.Res
import com.pos.app.res.dmsans_bold
import com.pos.app.res.dmsans_medium
import com.pos.app.res.dmsans_regular
import com.pos.app.res.dmsans_semibold
import com.pos.app.res.jakarta_bold
import com.pos.app.res.jakarta_extrabold
import com.pos.app.res.jakarta_medium
import com.pos.app.res.jakarta_regular
import com.pos.app.res.jakarta_semibold
import kotlin.math.abs
import org.jetbrains.compose.resources.Font

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

/**
 * DM Sans = general text (body, labels).
 * Plus Jakarta Sans = headings, product names, prices, totals.
 */
@Composable
fun PosTheme(content: @Composable () -> Unit) {
    val dmSans = FontFamily(
        Font(Res.font.dmsans_regular, FontWeight.Normal),
        Font(Res.font.dmsans_medium, FontWeight.Medium),
        Font(Res.font.dmsans_semibold, FontWeight.SemiBold),
        Font(Res.font.dmsans_bold, FontWeight.Bold),
    )
    val jakarta = FontFamily(
        Font(Res.font.jakarta_regular, FontWeight.Normal),
        Font(Res.font.jakarta_medium, FontWeight.Medium),
        Font(Res.font.jakarta_semibold, FontWeight.SemiBold),
        Font(Res.font.jakarta_bold, FontWeight.Bold),
        Font(Res.font.jakarta_extrabold, FontWeight.ExtraBold),
    )

    val base = Typography()
    val typography = base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = jakarta),
        displayMedium = base.displayMedium.copy(fontFamily = jakarta),
        displaySmall = base.displaySmall.copy(fontFamily = jakarta),
        headlineLarge = base.headlineLarge.copy(fontFamily = jakarta),
        headlineMedium = base.headlineMedium.copy(fontFamily = jakarta),
        headlineSmall = base.headlineSmall.copy(fontFamily = jakarta),
        titleLarge = base.titleLarge.copy(fontFamily = jakarta),
        titleMedium = base.titleMedium.copy(fontFamily = jakarta),
        titleSmall = base.titleSmall.copy(fontFamily = jakarta),
        bodyLarge = base.bodyLarge.copy(fontFamily = dmSans),
        bodyMedium = base.bodyMedium.copy(fontFamily = dmSans),
        bodySmall = base.bodySmall.copy(fontFamily = dmSans),
        labelLarge = base.labelLarge.copy(fontFamily = dmSans),
        labelMedium = base.labelMedium.copy(fontFamily = dmSans),
        labelSmall = base.labelSmall.copy(fontFamily = dmSans),
    )

    MaterialTheme(colorScheme = PosColors, typography = typography, content = content)
}
