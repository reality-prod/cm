// SPDX-License-Identifier: AGPL-3.0-or-later
package org.codeberg.mobile.ui

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.codeberg.mobile.resources.Res
import org.codeberg.mobile.resources.ibm_plex_mono_medium
import org.codeberg.mobile.resources.ibm_plex_mono_regular
import org.codeberg.mobile.resources.inter_bold
import org.codeberg.mobile.resources.inter_extrabold
import org.codeberg.mobile.resources.inter_medium
import org.codeberg.mobile.resources.inter_regular
import org.codeberg.mobile.resources.inter_semibold
import org.codeberg.mobile.resources.space_grotesk_bold
import org.codeberg.mobile.resources.space_grotesk_medium
import org.codeberg.mobile.resources.space_grotesk_regular
import org.codeberg.mobile.resources.space_grotesk_semibold
import org.jetbrains.compose.resources.Font

// The type system. Codeberg's default typeface is Inter, so Inter carries the whole app. Space Grotesk is used
// sparingly, only for onboarding headings. IBM Plex Mono is for counts, stats and code. All are SIL OFL (brand/fonts/).
//   Text:    Inter. Default for everything: headings, titles, body, labels and buttons.
//   Display: Space Grotesk. Onboarding headings only (see displayFamily).
//   Mono:    IBM Plex Mono. Numbers, counts and code.

/** Space Grotesk. Onboarding headings only; everywhere else the app uses Inter. */
@Composable
fun displayFamily(): FontFamily = FontFamily(
    Font(Res.font.space_grotesk_regular, FontWeight.Normal),
    Font(Res.font.space_grotesk_medium, FontWeight.Medium),
    Font(Res.font.space_grotesk_semibold, FontWeight.SemiBold),
    Font(Res.font.space_grotesk_bold, FontWeight.Bold),
)

/** Inter, for body text, labels and buttons. */
@Composable
fun textFamily(): FontFamily = FontFamily(
    Font(Res.font.inter_regular, FontWeight.Normal),
    Font(Res.font.inter_medium, FontWeight.Medium),
    Font(Res.font.inter_semibold, FontWeight.SemiBold),
    Font(Res.font.inter_bold, FontWeight.Bold),
    Font(Res.font.inter_extrabold, FontWeight.ExtraBold),
)

/** IBM Plex Mono, for numbers and code. Provided through a local so any composable can ask for it. */
@Composable
fun monoFamily(): FontFamily = FontFamily(
    Font(Res.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(Res.font.ibm_plex_mono_medium, FontWeight.Medium),
)

val LocalMonoFamily = compositionLocalOf<FontFamily> { FontFamily.Monospace }

/** The app's typography: Inter everywhere, with tight tracking on the large sizes. */
@Composable
fun rememberAppTypography(): Typography {
    val text = textFamily()
    return remember(text) {
        Typography(
            displayLarge = TextStyle(fontFamily = text, fontSize = 48.sp, lineHeight = 52.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.4).sp),
            displayMedium = TextStyle(fontFamily = text, fontSize = 40.sp, lineHeight = 46.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.1).sp),
            displaySmall = TextStyle(fontFamily = text, fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.9).sp),
            headlineLarge = TextStyle(fontFamily = text, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.7).sp),
            headlineMedium = TextStyle(fontFamily = text, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
            headlineSmall = TextStyle(fontFamily = text, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
            titleLarge = TextStyle(fontFamily = text, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
            titleMedium = TextStyle(fontFamily = text, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp),
            titleSmall = TextStyle(fontFamily = text, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
            bodyLarge = TextStyle(fontFamily = text, fontSize = 16.sp, lineHeight = 24.sp),
            bodyMedium = TextStyle(fontFamily = text, fontSize = 14.sp, lineHeight = 21.sp),
            bodySmall = TextStyle(fontFamily = text, fontSize = 12.sp, lineHeight = 17.sp, letterSpacing = 0.1.sp),
            labelLarge = TextStyle(fontFamily = text, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
            labelMedium = TextStyle(fontFamily = text, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
            labelSmall = TextStyle(fontFamily = text, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
        )
    }
}
