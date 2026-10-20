// SPDX-License-Identifier: AGPL-3.0-or-later
package org.codeberg.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.codeberg.mobile.data.AppState
import org.codeberg.mobile.data.Bridge
import org.codeberg.mobile.data.PlatformBackHandler
import org.codeberg.mobile.data.Repo
import org.codeberg.mobile.data.Screen
import org.codeberg.mobile.data.ThemeMode
import org.codeberg.mobile.data.createDeviceAuth
import org.codeberg.mobile.data.createSecureStore
import org.codeberg.mobile.data.hapticTap
import org.codeberg.mobile.data.setSecureWindow
import org.codeberg.mobile.ui.Avatar
import org.codeberg.mobile.ui.BrandColors
import org.codeberg.mobile.ui.Chip
import org.codeberg.mobile.ui.ChipTone
import org.codeberg.mobile.ui.CodebergTheme
import org.codeberg.mobile.ui.DetailScreen
import org.codeberg.mobile.ui.LinkDialog
import org.codeberg.mobile.ui.LocalMonoFamily
import org.codeberg.mobile.ui.ThemeSwitcherPill
import org.codeberg.mobile.ui.Wordmark

private enum class Tab(val label: String) { Explore("Explore"), Repos("Repos"), Inbox("Inbox"), Profile("You") }

private val FieldShape = RoundedCornerShape(18.dp)

@Composable
fun App() {
    val scope = rememberCoroutineScope()
    val state = remember { AppState(scope, createSecureStore(), createDeviceAuth()) }
    val showSignIn = state.onboardingDone && state.user == null && !state.anonymous
    // Screenshots are allowed by default; FLAG_SECURE is only applied if the user enables "Block screenshots" in Settings.
    LaunchedEffect(state.blockScreenshots) {
        setSecureWindow(state.blockScreenshots)
    }
    val isDark = when (state.themeMode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.System -> isSystemInDarkTheme()
    }
    CodebergTheme(dark = isDark) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when {
                state.lockEnabled && state.locked -> LockScreen(state) // Content is not even composed while locked.
                !state.onboardingDone -> OnboardingFlow(state)
                showSignIn -> SignInPage(state)
                else -> Shell(state)
            }
        }
    }
}

@Composable
private fun Shell(state: AppState) {
    var tab by remember { mutableStateOf(Tab.Explore) }
    // Deep links (Android intent / iOS onOpenURL) wait here until sign-in and the app lock are out of the way.
    LaunchedEffect(Bridge.pendingDeepLink) {
        Bridge.pendingDeepLink?.let {
            state.stack.clear()
            state.push(it)
            Bridge.pendingDeepLink = null
        }
    }
    val top = state.stack.lastOrNull()
    PlatformBackHandler(enabled = top != null) { state.pop() }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            if (top != null) {
                DetailScreen(state, top)
            } else when (tab) {
                Tab.Explore -> ExploreScreen(state)
                Tab.Profile -> ProfileScreen(state)
                Tab.Repos -> if (state.user != null) ReposTab(state) else SignInPrompt(state, "your repositories")
                Tab.Inbox -> if (state.user != null) InboxTab(state) else SignInPrompt(state, "your notifications")
            }
        }
        if (top == null) {
            FloatingNav(tab, { tab = it }, Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 12.dp))
        }
        state.pendingLink?.let { LinkDialog(it, onDismiss = { state.pendingLink = null }) }
    }
}

@Composable
private fun ExploreScreen(state: AppState) {
    var q by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { if (state.repos.isEmpty() && !state.loading) state.search("") }
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Wordmark()
                ThemeSwitcherPill(mode = state.themeMode, onSelect = state::setThemeMode)
            }
            Spacer(Modifier.height(20.dp))
            Text("Explore", style = MaterialTheme.typography.headlineLarge)
            Text("Discover open source on Codeberg", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(18.dp))
            // Searches only on the keyboard's Search action: no request per keystroke.
            OutlinedTextField(
                value = q, onValueChange = { q = it }, singleLine = true, shape = FieldShape,
                placeholder = { Text("Search repositories") }, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { state.search(q) }),
            )
            if (state.interests.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    InterestFilterPill(
                        label = "All",
                        selected = state.activeInterest == null,
                        onClick = { state.selectInterest(null) },
                    )
                    state.interests.forEach { topic ->
                        InterestFilterPill(
                            label = topic,
                            selected = state.activeInterest == topic,
                            onClick = { state.selectInterest(if (state.activeInterest == topic) null else topic) },
                        )
                    }
                    Surface(
                        onClick = { state.push(Screen.Settings) },
                        shape = CircleShape,
                        color = Color.Transparent,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Text(
                            "Tune",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                }
            }
        }
        itemsIndexed(state.repos) { i, repo ->
            RepoCard(repo) { state.push(Screen.RepoHome(repo.fullName)) }
            if (i == state.repos.lastIndex && state.hasMore) LaunchedEffect(state.repos.size) { state.loadMore() }
        }
        item {
            when {
                state.loading -> Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) { CircularProgressIndicator() }
                state.listError != null -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(state.listError ?: "", color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    OutlinedButton(onClick = { state.search(q) }) { Text("Retry") }
                }
                state.repos.isEmpty() -> Text("No repositories found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun RepoCard(repo: Repo, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Avatar(repo.fullName, 40.dp)
                Column {
                    Text(repo.fullName.substringBefore('/'), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(repo.fullName.substringAfter('/'), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            if (repo.description.isNotBlank()) {
                Text(repo.description, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Chip("\u2605 ${repo.stars}", tone = ChipTone.Yellow)
                if (repo.language.isNotBlank()) Chip(repo.language, tone = ChipTone.Green)
                if (repo.isPrivate) Chip("private", tone = ChipTone.Orange)
                Spacer(Modifier.weight(1f))
                Text(
                    repo.updatedAt.substringBefore('T'),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InterestFilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) BrandColors.brandBlue else scheme.surfaceVariant,
        border = BorderStroke(1.dp, if (selected) BrandColors.accent else scheme.outlineVariant),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color.White else scheme.onSurface,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun ProfileScreen(state: AppState) {
    val user = state.user
    val profile = state.profile
    val displayName = profile?.displayName ?: user ?: "Guest"
    val mono = LocalMonoFamily.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("You", style = MaterialTheme.typography.headlineLarge)
            OutlinedButton(onClick = { state.push(Screen.Settings) }, shape = RoundedCornerShape(16.dp)) {
                Text("Settings")
            }
        }

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Avatar(displayName, 56.dp)
                    Column(Modifier.weight(1f)) {
                        Text(displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        if (user != null) {
                            Text("@$user \u00B7 codeberg.org", style = MaterialTheme.typography.bodySmall, fontFamily = mono, color = BrandColors.accent)
                        } else {
                            Text("Browsing without an account", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (profile != null && profile.description.isNotBlank()) {
                    Text(profile.description, style = MaterialTheme.typography.bodyMedium)
                }
                if (profile != null && (profile.followers > 0 || profile.following > 0 || profile.location.isNotBlank())) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (profile.followers > 0 || profile.following > 0) {
                            Chip("${profile.followers} followers \u00B7 ${profile.following} following", tinted = true)
                        }
                        if (profile.location.isNotBlank()) {
                            Chip(profile.location)
                        }
                    }
                }
                if (user != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { state.signOut() }, shape = RoundedCornerShape(16.dp)) { Text("Sign out") }
                    }
                } else {
                    Button(onClick = { state.promptSignIn() }, shape = RoundedCornerShape(16.dp)) { Text("Sign in to Codeberg") }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Your interests", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Edit in Settings",
                        style = MaterialTheme.typography.labelSmall,
                        color = BrandColors.accent,
                        modifier = Modifier.clickable { state.push(Screen.Settings) },
                    )
                }
                if (state.interests.isEmpty()) {
                    Text("No topics selected yet. Pick topics in Settings to tune Explore.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.interests.forEachIndexed { idx, topic ->
                            val tone = when (idx % 3) {
                                0 -> ChipTone.Green
                                1 -> ChipTone.Blue
                                else -> ChipTone.Yellow
                            }
                            Chip(topic, tone = tone)
                        }
                    }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.padding(20.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Appearance", style = MaterialTheme.typography.titleMedium)
                    Text("Light, Dark or System theme", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ThemeSwitcherPill(mode = state.themeMode, onSelect = state::setThemeMode)
            }
        }

        Text(
            "Talks only to codeberg.org over Let's Encrypt SPKI-pinned HTTPS. No analytics, trackers or third-party servers.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FloatingNav(selected: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier, shape = RoundedCornerShape(32.dp), color = scheme.surfaceVariant,
        border = BorderStroke(1.dp, scheme.outline), shadowElevation = 12.dp,
    ) {
        Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Tab.entries.forEach { t ->
                val on = t == selected
                Box(
                    Modifier.clip(CircleShape).background(if (on) scheme.primary else Color.Transparent)
                        .clickable { hapticTap(); onSelect(t) }.padding(horizontal = 18.dp, vertical = 11.dp),
                ) {
                    Text(
                        t.label, style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (on) scheme.onPrimary else scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
