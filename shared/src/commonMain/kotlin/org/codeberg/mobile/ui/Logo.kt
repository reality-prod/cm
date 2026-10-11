// SPDX-License-Identifier: AGPL-3.0-or-later
package org.codeberg.mobile.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Codeberg logo icon - the berg/mountain shape from the official SVG.
 * Uses the exact path from the provided SVG (trademark of Codeberg e.V.).
 */
@Composable
fun CodebergIcon(modifier: Modifier = Modifier, size: Int = 64) {
    val path = remember {
        // Berg icon path from official Codeberg SVG with fill="#2185d0"
        PathParser().parsePathString(
            "M11249.461-1883.696c-12.74 0-23.067 10.327-23.067 23.067 0 4.334 1.22 8.58 3.522 12.251l19.232-24.863" +
                "c.138-.18.486-.18.624 0l19.233 24.864c2.302-3.672 3.523-7.919 3.523-12.252 0-12.74-10.327-23.067-23.067-23.067"
        ).toPath()
    }
    Canvas(modifier.size(size.dp)) {
        // Simplified scaling: just scale the path to fit the canvas
        // The path coordinates are relative, so we can scale uniformly
        val scaleFactor = this.size.width / 23.067f / 2f // 23.067 is the width of the path
        scale(scaleFactor, Offset(this.size.width / 2f, this.size.height / 2f)) {
            drawPath(path, Color(0xFF2185D0))
        }
    }
}

/**
 * Codeberg logo - uses the icon.
 */
@Composable
fun CodebergLogo(modifier: Modifier = Modifier, size: Int = 64) {
    CodebergIcon(modifier = modifier, size = size)
}

/**
 * Full Codeberg logo - larger version for sign-in screens.
 * Renders the berg icon in Codeberg Blue (#2185D0).
 */
@Composable
fun CodebergFullLogo(modifier: Modifier = Modifier) {
    CodebergIcon(modifier = modifier.size(120.dp))
}
