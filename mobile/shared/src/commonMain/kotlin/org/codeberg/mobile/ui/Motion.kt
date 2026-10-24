// SPDX-License-Identifier: AGPL-3.0-or-later
package org.codeberg.mobile.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Arrangement

// Motion rules for the whole app. Short, springy, never blocking: nothing waits for an animation to finish.
//   Loading:      a shimmer that matches the shape of what is coming, instead of a spinner.
//   Press:        cards and rows ease down to 97% and back, so a tap always feels answered.
//   Navigation:   pages slide and fade; tabs crossfade.

/** A soft moving highlight over a placeholder. Use it on the shape of the content that is loading. */
fun Modifier.shimmer(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -600f,
        targetValue = 1400f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 1300, easing = LinearEasing)),
        label = "shimmer-x",
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.outlineVariant
    background(
        Brush.linearGradient(
            colors = listOf(base, highlight, base),
            start = Offset(x, 0f),
            end = Offset(x + 500f, 500f),
        ),
    )
}

/** Eases the element down slightly while it is pressed. Pair it with the same [InteractionSource] as the click. */
fun Modifier.pressScale(interaction: InteractionSource): Modifier = composed {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 520f),
        label = "press-scale",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Placeholder cards in the shape of repository cards. Shown while content loads, instead of a spinner. */
@Composable
fun SkeletonRows(count: Int = 4, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(count) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(104.dp)
                    .clip(RoundedCornerShape(BrandRadius.lg))
                    .shimmer(),
            )
        }
    }
}
