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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.codeberg.mobile.data.ApiException
import org.codeberg.mobile.data.errorText
import org.codeberg.mobile.data.hostOf

fun compact(n: Int): String = if (n >= 1000) "${n / 1000}.${(n % 1000) / 100}k" else n.toString()

@Composable
fun Avatar(name: String, size: Dp = 44.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(Brush.linearGradient(listOf(BlueAccent, CodebergBlue))),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.firstOrNull()?.uppercase() ?: "?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp)
    }
}

@Composable
fun TopBar(title: String, subtitle: String?, onBack: () -> Unit) {
    val s = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(s.surface).border(1.dp, s.primary.copy(alpha = 0.3f), CircleShape).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { Text("\u2039", fontSize = 28.sp, color = s.onSurface) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = s.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(title, style = MaterialTheme.typography.titleLarge, color = s.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun PillTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val s = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().clip(CircleShape).background(s.surface).border(1.dp, s.primary.copy(alpha = 0.3f), CircleShape).padding(4.dp)) {
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
fun Chip(text: String, tinted: Boolean = false) {
    val s = MaterialTheme.colorScheme
    Box(Modifier.clip(CircleShape).background(if (tinted) s.primary.copy(alpha = 0.18f) else s.onSurface.copy(alpha = 0.07f)).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = if (tinted) s.primary else s.onSurfaceVariant)
    }
}

@Composable
fun IconTile(label: String, accent: Boolean) {
    val s = MaterialTheme.colorScheme
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(if (accent) s.primary.copy(alpha = 0.16f) else s.onSurface.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (accent) s.primary else s.onSurfaceVariant) }
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
