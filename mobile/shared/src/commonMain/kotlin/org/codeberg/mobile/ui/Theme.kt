// SPDX-License-Identifier: AGPL-3.0-or-later
package org.codeberg.mobile.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

// Every colour, radius and size below comes from brand/tokens.json (generated into Tokens.kt). Change it there.
// Fonts come from Fonts.kt.

/** The logo blue and its tints, kept under their old names for existing call sites. */
val CodebergBlue = BrandColors.brandBlue
val BlueAccent = BrandColors.accent
val BlueSoft = BrandColors.accentSoft

/**
 * Dark scheme using the full Codeberg palette (Design/README.md):
 * - Primary: Blue (#44ADFF / #2185D0) & Green (#73CCC6 / #97E1E7 / #144B49)
 * - Highlights: Yellow (#FFEA69 / #FFF8C8) & Orange (#E79125 / #A7681A)
 * - Plain text: Crisp White (#FFFFFF) and Ice/Pale Blue (#C2FAFF / #B5DDFF)
 */
private val DarkColors = darkColorScheme(
    primary = BrandColors.brightBlue,
    onPrimary = BrandColors.navy,
    primaryContainer = BrandColors.brandBlue,
    onPrimaryContainer = BrandColors.white,
    secondary = BrandColors.green,
    onSecondary = BrandColors.navy,
    secondaryContainer = BrandColors.darkGreen,
    onSecondaryContainer = BrandColors.iceBlue,
    tertiary = BrandColors.yellow,
    onTertiary = BrandColors.black,
    tertiaryContainer = BrandColors.brown,
    onTertiaryContainer = BrandColors.cream,
    background = BrandColors.darkBg,
    onBackground = BrandColors.white,
    surface = BrandColors.darkBg,
    onSurface = BrandColors.white,
    surfaceVariant = BrandColors.darkSurface,
    onSurfaceVariant = BrandColors.paleBlue,
    outline = BrandColors.darkOutline,
    outlineVariant = BrandColors.darkBorder,
    error = BrandColors.orange,
    onError = BrandColors.black,
)

/**
 * Light scheme using the full Codeberg palette (Design/README.md):
 * - Crisp White (#FFFFFF) background with high-contrast Navy/Black (#0B3049 / #000000) text
 * - Primary Blue (#2185D0) and Primary Green (#144B49 / #73CCC6)
 * - Warm Yellow (#FFEA69 / #FFF8C8) & Orange (#E79125 / #A7681A) highlights
 */
private val LightColors = lightColorScheme(
    primary = BrandColors.brandBlue,
    onPrimary = BrandColors.white,
    primaryContainer = BrandColors.paleBlue,
    onPrimaryContainer = BrandColors.navy,
    secondary = BrandColors.darkGreen,
    onSecondary = BrandColors.white,
    secondaryContainer = BrandColors.iceBlue,
    onSecondaryContainer = BrandColors.darkGreen,
    tertiary = BrandColors.brown,
    onTertiary = BrandColors.white,
    tertiaryContainer = BrandColors.cream,
    onTertiaryContainer = BrandColors.brown,
    background = BrandColors.lightBg,
    onBackground = BrandColors.black,
    surface = BrandColors.lightBg,
    onSurface = BrandColors.navy,
    surfaceVariant = BrandColors.lightSurface,
    onSurfaceVariant = BrandColors.darkGreen,
    outline = BrandColors.lightOutline,
    outlineVariant = BrandColors.lightBorder,
    error = BrandColors.brown,
    onError = BrandColors.white,
)

/** Generous, consistent corners. */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(BrandRadius.xs),
    small = RoundedCornerShape(BrandRadius.sm),
    medium = RoundedCornerShape(BrandRadius.md),
    large = RoundedCornerShape(BrandRadius.lg),
    extraLarge = RoundedCornerShape(BrandRadius.xxl),
)

/** Dark by default, as requested. */
@Composable
fun CodebergTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    val typography = rememberAppTypography()
    val mono = monoFamily()
    CompositionLocalProvider(LocalMonoFamily provides mono) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            shapes = AppShapes,
            typography = typography,
            content = content,
        )
    }
}
