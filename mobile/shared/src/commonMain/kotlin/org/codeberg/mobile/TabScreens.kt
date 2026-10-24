// SPDX-License-Identifier: AGPL-3.0-or-later
package org.codeberg.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.codeberg.mobile.data.ApiException
import org.codeberg.mobile.data.AppState
import org.codeberg.mobile.data.Notif
import org.codeberg.mobile.data.Page
import org.codeberg.mobile.data.Screen
import org.codeberg.mobile.data.errorText
import org.codeberg.mobile.data.myRepos
import org.codeberg.mobile.data.notifications
import org.codeberg.mobile.ui.Chip
import org.codeberg.mobile.ui.ChipTone
import org.codeberg.mobile.ui.CodebergMark
import org.codeberg.mobile.ui.PillTabs
import org.codeberg.mobile.ui.SkeletonRows

@Composable
internal fun SignInPrompt(state: AppState, what: String) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(32.dp)) {
            CodebergMark(64.dp)
            Text("Sign in to see $what", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { state.promptSignIn() }, shape = RoundedCornerShape(16.dp)) { Text("Sign in") }
        }
    }
}

/** Infinite list: loads page 1 per [key], then following pages as the end is reached. */
@Composable
internal fun <T> PagedList(key: Any, fetch: suspend (Int) -> Page<T>, empty: String, row: @Composable (T) -> Unit) {
    var rows by remember(key) { mutableStateOf<List<T>>(emptyList()) }
    var page by remember(key) { mutableIntStateOf(1) }
    var hasMore by remember(key) { mutableStateOf(false) }
    var loading by remember(key) { mutableStateOf(true) }
    var error by remember(key) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun load(p: Int) {
        loading = true
        error = null
        try {
            val r = fetch(p)
            rows = if (p == 1) r.items else rows + r.items
            hasMore = r.hasMore
            page = p + 1
        } catch (e: ApiException) {
            error = errorText(e.error)
        } finally {
            loading = false
        }
    }
    LaunchedEffect(key) { load(1) }

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(rows) { row(it) }
        item {
            when {
                loading -> SkeletonRows(count = 3)
                error != null -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(error ?: "", color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    OutlinedButton(onClick = { scope.launch { load(page) } }) { Text("Retry") }
                }
                rows.isEmpty() -> Text(empty, color = MaterialTheme.colorScheme.onSurfaceVariant)
                hasMore -> LaunchedEffect(page) { load(page) }
            }
        }
    }
}

@Composable
internal fun ReposTab(state: AppState) {
    Column(Modifier.fillMaxSize()) {
        Text("Your repos", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 8.dp))
        PagedList(key = state.user ?: "", fetch = { state.api.myRepos(it) }, empty = "No repositories yet.") { repo ->
            RepoCard(repo) { state.push(Screen.RepoHome(repo.fullName)) }
        }
    }
}

@Composable
internal fun InboxTab(state: AppState) {
    var all by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var askFor by remember { mutableStateOf<Notif?>(null) } // Waiting for write access.

    fun mark(n: Notif) = state.markRead(n.id) { reload++ }

    Column(Modifier.fillMaxSize()) {
        Text("Inbox", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 12.dp))
        PillTabs(listOf("Unread", "All"), if (all) 1 else 0, { all = it == 1 }, Modifier.padding(horizontal = 20.dp))
        state.actionError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        }
        PagedList(key = Triple(all, reload, state.user), fetch = { state.api.notifications(it, all) }, empty = if (all) "No notifications." else "You're all caught up.") { n ->
            NotifCard(
                n,
                onOpen = {
                    val number = n.subject.url.substringAfterLast('/').toIntOrNull()
                    val repo = n.repository.fullName
                    state.push(if (number != null && n.subject.type in setOf("Issue", "Pull")) Screen.IssueView(repo, number) else Screen.RepoHome(repo))
                },
                onMarkRead = { if (state.writeToken != null) mark(n) else askFor = n },
            )
        }
    }

    askFor?.let { n ->
        ElevateDialog(state, onCancel = { askFor = null }) { askFor = null; mark(n) }
    }
}

@Composable
private fun NotifCard(n: Notif, onOpen: () -> Unit, onMarkRead: () -> Unit) {
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Chip(
                    n.subject.type.ifBlank { "Update" },
                    tone = when {
                        n.unread && n.subject.type == "Release" -> ChipTone.Yellow
                        n.unread -> ChipTone.Orange
                        else -> ChipTone.Green
                    },
                )
                Text(n.repository.fullName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(n.subject.title, style = MaterialTheme.typography.titleMedium, fontWeight = if (n.unread) FontWeight.Bold else FontWeight.Normal, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(n.updatedAt.substringBefore('T'), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (n.unread) TextButton(onClick = onMarkRead) { Text("Mark read") }
            }
        }
    }
}

/**
 * PHASE 2 gate. Writing needs a token with write scope. We ask for the password again, mint that token, keep it in
 * memory only, and never store the password.
 */
@Composable
private fun ElevateDialog(state: AppState, onCancel: () -> Unit, onDone: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!state.elevating) onCancel() },
        shape = RoundedCornerShape(28.dp),
        title = { Text("Allow changes?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Marking as read needs write access. Enter your password to allow it for this session. " +
                        "It isn't stored, and the write token stays in memory only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = password, onValueChange = { password = it }, singleLine = true, label = { Text("Password") },
                    shape = RoundedCornerShape(16.dp), visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                )
                OutlinedTextField(
                    value = code, onValueChange = { code = it.filter(Char::isDigit).take(6) }, singleLine = true,
                    label = { Text("2FA code (if enabled)") }, shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                state.elevateError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !state.elevating && password.isNotEmpty(),
                onClick = {
                    val pw = password
                    password = "" // Don't keep it in UI state.
                    state.elevate(pw, code, onDone)
                },
            ) { Text(if (state.elevating) "Working\u2026" else "Allow") }
        },
        dismissButton = { TextButton(enabled = !state.elevating, onClick = onCancel) { Text("Cancel") } },
    )
}
