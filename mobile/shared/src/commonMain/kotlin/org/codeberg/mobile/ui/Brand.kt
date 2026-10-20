// SPDX-License-Identifier: AGPL-3.0-or-later
package org.codeberg.mobile.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// The Codeberg logo, drawn from the official logo kit (brand/logo-kit). Nothing here is retraced or recoloured:
// the mark comes in a blue and a white variant, and the rules are:
//   - white on dark backgrounds, blue on light ones (the kit's own rule)
//   - never stretched, skewed, tinted or rearranged; the mark and the logotype stay together as one lockup
//   - the logo is CC0 1.0; Codeberg and its logo are trademarks of Codeberg e.V.

/** Draws one logo variant at [width]. Height follows the variant's own proportions, so nothing is distorted. */
@Composable
fun CodebergLogo(art: LogoArt, width: Dp, modifier: Modifier = Modifier) {
    val paths = remember(art) { art.parts.map { PathParser().parsePathString(it.path).toPath() } }
    val height = width * (art.height / art.width)
    Canvas(modifier.size(width, height)) {
        val factor = size.width / art.width
        scale(factor, Offset.Zero) {
            art.parts.forEachIndexed { index, part -> drawLogoPart(part, paths[index]) }
        }
    }
}

private fun DrawScope.drawLogoPart(part: LogoPart, path: androidx.compose.ui.graphics.Path) {
    withTransform({
        translate(part.translateX, part.translateY)
        scale(part.scaleX, part.scaleY, Offset.Zero)
    }) {
        when (val fill = part.fill) {
            is LogoFill.Solid -> drawPath(path, fill.color)
            is LogoFill.Linear -> drawPath(
                path,
                Brush.linearGradient(
                    *fill.stops.toTypedArray(),
                    start = Offset(fill.x1, fill.y1),
                    end = Offset(fill.x2, fill.y2),
                ),
            )
        }
    }
}

/** The mark alone (the blue circle with the mountain), in the variant that suits the current theme. */
@Composable
fun CodebergMark(size: Dp, modifier: Modifier = Modifier) {
    val onDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    CodebergLogo(if (onDark) LogoKit.IconWhite else LogoKit.IconBlue, size, modifier)
}

/** The horizontal lockup: mark and logotype together, the way the kit provides them. Used for headers. */
@Composable
fun Wordmark(modifier: Modifier = Modifier, width: Dp = 112.dp) {
    val onDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    CodebergLogo(if (onDark) LogoKit.HorizontalWhite else LogoKit.HorizontalBlue, width, modifier)
}

/** The vertical lockup from the official logo kit (mark stacked above the logotype). */
@Composable
fun VerticalLogo(modifier: Modifier = Modifier, width: Dp = 96.dp) {
    val onDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    CodebergLogo(if (onDark) LogoKit.VerticalWhite else LogoKit.VerticalBlue, width, modifier)
}

/** Centred hero for sign-in and the lock screen: the mark and a title, with a soft brand glow behind it. */
@Composable
fun BrandHero(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.foundation.layout.Box(
            Modifier
                .size(200.dp)
                .background(Brush.radialGradient(listOf(BrandColors.brandBlue.copy(alpha = 0.30f), androidx.compose.ui.graphics.Color.Transparent))),
            contentAlignment = Alignment.Center,
        ) {
            CodebergMark(96.dp)
        }
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
