// SPDX-License-Identifier: AGPL-3.0-or-later
package org.codeberg.mobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.codeberg.mobile.data.ApiException
import org.codeberg.mobile.data.AppState
import org.codeberg.mobile.data.Comment
import org.codeberg.mobile.data.Issue
import org.codeberg.mobile.data.Screen
import org.codeberg.mobile.data.comments
import org.codeberg.mobile.data.errorText
import org.codeberg.mobile.data.issue
import org.codeberg.mobile.data.issues
import org.codeberg.mobile.data.shareText

@Composable
private fun IssueSurface(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
        content = content,
    )
}

@Composable
internal fun IssueList(state: AppState, full: String, type: String) {
    var open by remember { mutableStateOf(true) }
    var items by remember(open) { mutableStateOf<List<Issue>>(emptyList()) }
    var page by remember(open) { mutableIntStateOf(1) }
    var hasMore by remember(open) { mutableStateOf(false) }
    var loading by remember(open) { mutableStateOf(true) }
    var error by remember(open) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun fetch(p: Int) {
        loading = true
        error = null
        try {
            val r = state.api.issues(full, type, if (open) "open" else "closed", p)
            items = if (p == 1) r.items else items + r.items
            hasMore = r.hasMore
            page = p + 1
        } catch (e: ApiException) {
            error = errorText(e.error)
        } finally {
            loading = false
        }
    }
    LaunchedEffect(open) { fetch(1) }

    Column(Modifier.fillMaxSize()) {
        PillTabs(listOf("Open", "Closed"), if (open) 0 else 1, { open = it == 0 }, Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items) { issue -> IssueCard(issue) { state.push(Screen.IssueView(full, issue.number)) } }
            item {
                when {
                    loading -> SkeletonRows(count = 3)
                    error != null -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(error ?: "", color = MaterialTheme.colorScheme.error)
                        OutlinedButton(onClick = { scope.launch { fetch(page.coerceAtLeast(1)) } }) { Text("Retry") }
                    }
                    items.isEmpty() -> Text("Nothing here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    hasMore -> LaunchedEffect(page) { fetch(page) }
                }
            }
        }
    }
}

@Composable
private fun IssueCard(issue: Issue, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Chip(if (issue.state == "open") "Open" else "Closed", tone = if (issue.state == "open") ChipTone.Green else ChipTone.Orange)
                Text("#${issue.number}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(issue.title, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            val meta = buildString {
                append(issue.user.login)
                append("  \u00B7  ${relativeTime(issue.createdAt)}")
                if (issue.comments > 0) append("  \u00B7  ${issue.comments} comments")
            }
            Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun IssueScreen(state: AppState, full: String, number: Int, link: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopBar("#$number", full, state::pop, onShare = { shareText("https://codeberg.org/$full/issues/$number") })
        Loadable(key = "$full#$number", load = { state.api.issue(full, number) to state.api.comments(full, number) }) { (issue, comments) ->
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    IssueSurface {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Chip(if (issue.state == "open") "Open" else "Closed", tone = if (issue.state == "open") ChipTone.Green else ChipTone.Orange)
                            Text(issue.title, style = MaterialTheme.typography.headlineMedium)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Avatar(issue.user.login, 28.dp, imageUrl = issue.user.avatarUrl.ifBlank { null })
                                Text("${issue.user.login}  \u00B7  ${relativeTime(issue.createdAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (issue.body.isBlank()) Text("No description.", color = MaterialTheme.colorScheme.onSurfaceVariant) else Markdown(issue.body, link)
                        }
                    }
                }
                items(comments) { c -> CommentCard(c, link) }
            }
        }
    }
}

@Composable
private fun CommentCard(c: Comment, link: (String) -> Unit) {
    IssueSurface {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Avatar(c.user.login, 28.dp, imageUrl = c.user.avatarUrl.ifBlank { null })
                Text(c.user.login, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text(relativeTime(c.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Markdown(c.body, link)
        }
    }
}
