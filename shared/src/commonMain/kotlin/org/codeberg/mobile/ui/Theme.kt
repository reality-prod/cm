// SPDX-License-Identifier: AGPL-3.0-or-later
package org.codeberg.mobile.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Exact fill color of the Codeberg logo SVG. */
val CodebergBlue = Color(0xFF2185D0)

/** Lighter tones of the same hue (about 206 degrees) used as accents on dark surfaces. */
val BlueAccent = Color(0xFF5AA9E6)
val BlueSoft = Color(0xFF9CCBF2)

// Dark-first surfaces: near-black with a blue cast, white text, one bright accent.
private val DarkColors = darkColorScheme(
    primary = BlueAccent,
    onPrimary = Color(0xFF06121F),
    primaryContainer = Color(0xFF123556),
    onPrimaryContainer = Color(0xFFD6EAFB),
    secondary = BlueSoft,
    onSecondary = Color(0xFF06121F),
    background = Color(0xFF0B0F14),
    onBackground = Color(0xFFF4F7FA),
    surface = Color(0xFF0B0F14),
    onSurface = Color(0xFFF4F7FA),
    surfaceVariant = Color(0xFF131A23),
    onSurfaceVariant = Color(0xFF9AA9BA),
    outline = Color(0xFF2A3647),
    outlineVariant = Color(0xFF1C2532),
)

// Light mode: white-forward, with a darker tone of the brand blue for AA-contrast text.
private val LightColors = lightColorScheme(
    primary = Color(0xFF1A6DAF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEBF8),
    onPrimaryContainer = Color(0xFF0A2C49),
    secondary = CodebergBlue,
    background = Color.White,
    onBackground = Color(0xFF0E1822),
    surface = Color.White,
    onSurface = Color(0xFF0E1822),
    surfaceVariant = Color(0xFFF1F6FA),
    onSurfaceVariant = Color(0xFF52627A),
    outline = Color(0xFFD3DEE9),
    outlineVariant = Color(0xFFE6EDF4),
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

// System font on purpose: SF Pro on iOS, Roboto on Android. Tight tracking and weight do the modern look.
val AppTypography = Typography(
    headlineLarge = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, letterSpacing = 0.1.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
)

/** Dark by default, as requested. */
@Composable
fun CodebergTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}
