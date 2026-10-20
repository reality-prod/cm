// SPDX-License-Identifier: AGPL-3.0-or-later
package org.codeberg.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.codeberg.mobile.data.ApiException
import org.codeberg.mobile.data.AppState
import org.codeberg.mobile.data.ThemeMode
import org.codeberg.mobile.data.errorText
import org.codeberg.mobile.data.hostOf

fun compact(n: Int): String = if (n >= 1000) "${n / 1000}.${(n % 1000) / 100}k" else n.toString()

enum class ChipTone { Neutral, Blue, Green, Yellow, Orange }

@Composable
fun Avatar(name: String, size: Dp = 44.dp) {
    // Uses the official Codeberg palette gradients (Blue, Green, and Amber/Orange) so avatars are distinct.
    val bucket = (name.hashCode() and 0x7FFFFFFF) % 3
    val gradient = when (bucket) {
        0 -> listOf(BrandColors.brightBlue, BrandColors.brandBlue)
        1 -> listOf(BrandColors.green, BrandColors.darkGreen)
        else -> listOf(BrandColors.orange, BrandColors.brown)
    }
    Box(
        Modifier.size(size).clip(CircleShape).background(Brush.linearGradient(gradient)),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.firstOrNull()?.uppercase() ?: "?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp)
    }
}

@Composable
fun ThemeSwitcherPill(mode: ThemeMode, onSelect: (ThemeMode) -> Unit, modifier: Modifier = Modifier) {
    val s = MaterialTheme.colorScheme
    Row(
        modifier
            .clip(CircleShape)
            .background(s.surfaceVariant)
            .border(1.dp, s.outlineVariant, CircleShape)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThemeMode.entries.forEach { item ->
            val active = item == mode
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(if (active) BrandColors.brandBlue else Color.Transparent)
                    .clickable { onSelect(item) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    item.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) Color.White else s.onSurface,
                )
            }
        }
    }
}

@Composable
fun TopBar(title: String, subtitle: String?, onBack: () -> Unit, onShare: (() -> Unit)? = null) {
    val s = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(s.surfaceVariant).border(1.dp, s.outlineVariant, CircleShape).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { Text("\u2039", fontSize = 28.sp, color = s.onSurface) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = s.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onShare != null) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(s.surfaceVariant).border(1.dp, s.outlineVariant, CircleShape).clickable(onClick = onShare),
                contentAlignment = Alignment.Center,
            ) { Text("\u2197", fontSize = 18.sp, color = s.onSurface) }
        }
    }
}

@Composable
fun PillTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val s = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().clip(CircleShape).background(s.surfaceVariant).border(1.dp, s.outlineVariant, CircleShape).padding(4.dp)) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).clip(CircleShape).background(if (on) s.primary else Color.Transparent)
                    .clickable { onSelect(i) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) s.onPrimary else s.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@Composable
fun Chip(text: String, tinted: Boolean = false, tone: ChipTone = if (tinted) ChipTone.Blue else ChipTone.Neutral) {
    val s = MaterialTheme.colorScheme
    val isDark = s.background.luminance() < 0.5f
    val (bg, fg, border) = when (tone) {
        ChipTone.Yellow -> if (isDark) {
            Triple(BrandColors.yellow.copy(alpha = 0.22f), BrandColors.yellow, BrandColors.yellow.copy(alpha = 0.55f))
        } else {
            Triple(BrandColors.cream, BrandColors.brown, BrandColors.yellow)
        }
        ChipTone.Green -> if (isDark) {
            Triple(BrandColors.darkGreen, BrandColors.iceBlue, BrandColors.green)
        } else {
            Triple(BrandColors.iceBlue, BrandColors.darkGreen, BrandColors.green)
        }
        ChipTone.Orange -> if (isDark) {
            Triple(BrandColors.brown.copy(alpha = 0.45f), BrandColors.cream, BrandColors.orange)
        } else {
            Triple(BrandColors.cream, BrandColors.brown, BrandColors.orange)
        }
        ChipTone.Blue -> if (isDark) {
            Triple(BrandColors.brandBlue.copy(alpha = 0.35f), BrandColors.paleBlue, BrandColors.brightBlue)
        } else {
            Triple(BrandColors.paleBlue.copy(alpha = 0.55f), BrandColors.navy, BrandColors.brandBlue)
        }
        ChipTone.Neutral -> Triple(s.surfaceVariant, s.onSurface, s.outlineVariant)
    }
    Box(
        Modifier
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, border, CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = fg)
    }
}

@Composable
fun IconTile(label: String, accent: Boolean) {
    val s = MaterialTheme.colorScheme
    val isDark = s.background.luminance() < 0.5f
    val bg = if (accent) {
        if (isDark) BrandColors.darkGreen else BrandColors.iceBlue
    } else {
        if (isDark) BrandColors.brandBlue.copy(alpha = 0.25f) else BrandColors.paleBlue.copy(alpha = 0.45f)
    }
    val fg = if (accent) {
        if (isDark) BrandColors.lightGreen else BrandColors.darkGreen
    } else {
        if (isDark) BrandColors.paleBlue else BrandColors.navy
    }
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, fg.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = fg) }
}

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Err(val message: String) : Load<Nothing>
    data class Ok<T>(val value: T) : Load<T>
}

/** Runs [load] once per [key]: spinner, then error with Retry, or [content]. */
@Composable
fun <T> Loadable(key: Any, modifier: Modifier = Modifier.fillMaxSize(), load: suspend () -> T, content: @Composable (T) -> Unit) {
    var attempt by remember { mutableIntStateOf(0) }
    val state by produceState<Load<T>>(Load.Loading, key, attempt) {
        value = Load.Loading
        value = try {
            Load.Ok(load())
        } catch (e: ApiException) {
            Load.Err(errorText(e.error))
        }
    }
    when (val s = state) {
        Load.Loading -> Box(modifier, Alignment.Center) { CircularProgressIndicator(strokeWidth = 3.dp) }
        is Load.Err -> Box(modifier, Alignment.Center) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(s.message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { attempt++ }, shape = RoundedCornerShape(16.dp)) { Text("Retry") }
            }
        }
        is Load.Ok<T> -> content(s.value)
    }
}

/** Shows the real host before anything leaves the app. The URL was already validated by safeHttpUrl. */
@Composable
fun LinkDialog(url: String, onDismiss: () -> Unit) {
    val uri = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Open link in your browser?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(hostOf(url), fontWeight = FontWeight.Bold)
                Text(url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { uri.openUri(url); onDismiss() }) { Text("Open") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
