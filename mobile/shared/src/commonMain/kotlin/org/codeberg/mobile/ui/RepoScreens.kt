// SPDX-License-Identifier: AGPL-3.0-or-later
package org.codeberg.mobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.codeberg.mobile.SettingsScreen
import org.codeberg.mobile.data.ApiException
import org.codeberg.mobile.data.AppState
import org.codeberg.mobile.data.Comment
import org.codeberg.mobile.data.Entry
import org.codeberg.mobile.data.Issue
import org.codeberg.mobile.data.RepoDetail
import org.codeberg.mobile.data.Screen
import org.codeberg.mobile.data.comments
import org.codeberg.mobile.data.errorText
import org.codeberg.mobile.data.issue
import org.codeberg.mobile.data.issues
import org.codeberg.mobile.data.listDir
import org.codeberg.mobile.data.rawFile
import org.codeberg.mobile.data.repoDetail
import org.codeberg.mobile.data.safeHttpUrl
import org.codeberg.mobile.data.shareText

@Composable
fun DetailScreen(state: AppState, screen: Screen) {
    val link = { url: String -> state.pendingLink = safeHttpUrl(url) } // Validated, then confirmed by the user.
    when (screen) {
        is Screen.RepoHome -> RepoHome(state, screen.full, link)
        is Screen.DirView -> Column(Modifier.fillMaxSize()) {
            TopBar(screen.path.substringAfterLast('/'), screen.full, state::pop)
            DirList(state, screen.full, screen.path, false, link)
        }
        is Screen.FileView -> Column(Modifier.fillMaxSize()) {
            TopBar(screen.path.substringAfterLast('/'), screen.path.substringBeforeLast('/', screen.full), state::pop)
            Loadable(key = screen.full + screen.path, load = { state.api.rawFile(screen.full, screen.path) }) { CodeView(it) }
        }
        is Screen.IssueView -> IssueScreen(state, screen.full, screen.number, link)
        Screen.Settings -> SettingsScreen(state)
    }
}

@Composable
private fun RepoHome(state: AppState, full: String, link: (String) -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    val s = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize()) {
        RepoBanner(state, full)
        Column(Modifier.fillMaxSize()) {
            TopBar(full.substringAfter('/'), full.substringBefore('/'), state::pop, onShare = { shareText("https://codeberg.org/$full") })
            RepoHeader(state, full)
            PillTabs(listOf("Code", "Issues", "Pull requests"), tab, { tab = it }, Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
            when (tab) {
                0 -> DirList(state, full, "", true, link)
                1 -> IssueList(state, full, "issues")
                else -> IssueList(state, full, "pulls")
            }
        }
    }
}

/**
 * The repository's banner: the first picture in its README, faded into the page. Without one, the brand glow and the
 * mark stand in, so the header always has a finished look.
 */
@Composable
private fun RepoBanner(state: AppState, full: String) {
    val banner by produceState<String?>(null, full) {
        value = try { findBannerUrl(state.api, full) } catch (e: ApiException) { null }
    }
    val s = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().height(220.dp)) {
        val url = banner
        if (url != null) {
            RemoteImage(url = url, contentDescription = "Repository banner", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, s.background))))
        } else {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(s.primary.copy(alpha = 0.22f), Color.Transparent))))
            CodebergMark(180.dp, Modifier.align(Alignment.TopEnd).alpha(0.10f))
        }
    }
}

@Composable
private fun RepoHeader(state: AppState, full: String) {
    val detail by produceState<RepoDetail?>(null, full) {
        value = try { state.api.repoDetail(full) } catch (e: ApiException) { null } // Quiet: tabs still work.
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Avatar(
                name = detail?.owner?.login?.ifBlank { null } ?: full.substringBefore('/'),
                size = 52.dp,
                imageUrl = detail?.owner?.avatarUrl,
            )
            val d = detail
            Text(
                d?.description?.ifBlank { null } ?: "No description",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        detail?.let { d ->
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("\u2605 ${compact(d.stars)}", tone = ChipTone.Yellow)
                Chip("${compact(d.forks)} forks", tone = ChipTone.Blue)
                Chip("${compact(d.openIssues)} open issues", tone = ChipTone.Green)
                if (d.defaultBranch.isNotBlank()) Chip(d.defaultBranch, tone = ChipTone.Neutral)
                if (d.language.isNotBlank()) Chip(d.language, tone = ChipTone.Green)
                if (d.archived) Chip("archived", tone = ChipTone.Orange)
            }
        }
    }
}

@Composable
private fun DirList(state: AppState, full: String, path: String, showReadme: Boolean, link: (String) -> Unit) {
    Loadable(key = "$full:$path", load = { state.api.listDir(full, path) }) { entries ->
        val sorted = remember(entries) { entries.sortedWith(compareBy({ it.type != "dir" }, { it.name.lowercase() })) }
        val readme = if (showReadme) sorted.firstOrNull { it.type == "file" && it.name.startsWith("readme", ignoreCase = true) } else null
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { EntryCard(sorted) { e -> state.push(if (e.type == "dir") Screen.DirView(full, e.path) else Screen.FileView(full, e.path)) } }
            if (readme != null) {
                item {
                    Card {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(readme.name, style = MaterialTheme.typography.titleMedium)
                            Loadable(
                                key = "readme:$full:${readme.path}",
                                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                // The default branch is needed so relative pictures in the README resolve.
                                load = {
                                    val branch = state.api.repoDetail(full).defaultBranch.ifBlank { "main" }
                                    branch to state.api.rawFile(full, readme.path)
                                },
                            ) { (branch, text) -> Markdown(text, link, imageBase = rawBase(full, branch)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
        content = content,
    )
}

@Composable
private fun EntryCard(entries: List<Entry>, onClick: (Entry) -> Unit) {
    Card {
        if (entries.isEmpty()) {
            Text("This folder is empty.", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column {
            entries.forEachIndexed { i, e ->
                val dir = e.type == "dir"
                Surface(onClick = { onClick(e) }, color = Color.Transparent) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconTile(if (dir) "/" else e.name.substringAfterLast('.', "").take(3).uppercase().ifEmpty { "\u2263" }, dir)
                        Spacer(Modifier.width(14.dp))
                        Text(e.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (!dir && e.size > 0) Text(size(e.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (dir) Text("\u203A", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 20.sp)
                    }
                }
                if (i < entries.lastIndex) HorizontalDivider(Modifier.padding(start = 70.dp), color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

private fun size(bytes: Long) = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${bytes / (1024 * 1024)} MB"
}

@Composable
private fun CodeView(text: String) {
    val s = MaterialTheme.colorScheme
    when {
        text.take(8000).contains('\u0000') -> Notice("Binary file. Preview isn't available.")
        text.length > 2_000_000 -> Notice("File is too large to preview.")
        else -> {
            val lines = remember(text) { text.split('\n') }
            val mono = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            Box(
                Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                    .background(s.surfaceVariant, RoundedCornerShape(20.dp)).horizontalScroll(rememberScrollState()),
            ) {
                LazyColumn(contentPadding = PaddingValues(vertical = 12.dp, horizontal = 12.dp)) {
                    items(lines.size) { i ->
                        Row {
                            Text((i + 1).toString().padStart(4), style = mono, color = s.onSurfaceVariant.copy(alpha = 0.6f))
                            Spacer(Modifier.width(12.dp))
                            Text(lines[i], style = mono, softWrap = false)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Notice(text: String) {
    Box(Modifier.fillMaxSize(), Alignment.Center) { Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun IssueList(state: AppState, full: String, type: String) {
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
                append("  \u00B7  ${issue.createdAt.substringBefore('T')}")
                if (issue.comments > 0) append("  \u00B7  ${issue.comments} comments")
            }
            Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun IssueScreen(state: AppState, full: String, number: Int, link: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopBar("#$number", full, state::pop, onShare = { shareText("https://codeberg.org/$full/issues/$number") })
        Loadable(key = "$full#$number", load = { state.api.issue(full, number) to state.api.comments(full, number) }) { (issue, comments) ->
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Card {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Chip(if (issue.state == "open") "Open" else "Closed", tone = if (issue.state == "open") ChipTone.Green else ChipTone.Orange)
                            Text(issue.title, style = MaterialTheme.typography.headlineMedium)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Avatar(issue.user.login, 28.dp)
                                Text("${issue.user.login}  \u00B7  ${issue.createdAt.substringBefore('T')}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    Card {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Avatar(c.user.login, 28.dp)
                Text(c.user.login, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text(c.createdAt.substringBefore('T'), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Markdown(c.body, link)
        }
    }
}
