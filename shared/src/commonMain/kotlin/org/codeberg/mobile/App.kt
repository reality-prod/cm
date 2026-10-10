// SPDX-License-Identifier: AGPL-3.0-or-later
package org.codeberg.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.codeberg.mobile.data.AppState
import org.codeberg.mobile.data.Repo
import org.codeberg.mobile.data.createDeviceAuth
import org.codeberg.mobile.data.hapticTap
import org.codeberg.mobile.data.createSecureStore
import org.codeberg.mobile.data.setSecureWindow
import org.codeberg.mobile.data.Screen
import org.codeberg.mobile.ui.Avatar
import org.codeberg.mobile.ui.DetailScreen
import org.codeberg.mobile.ui.LinkDialog
import org.codeberg.mobile.data.Bridge
import org.codeberg.mobile.data.PlatformBackHandler
import org.codeberg.mobile.ui.CodebergBlue
import org.codeberg.mobile.ui.CodebergTheme

private enum class Tab(val label: String) { Explore("Explore"), Repos("Repos"), Inbox("Inbox"), Profile("You") }

private val FieldShape = RoundedCornerShape(18.dp)

@Composable
fun App() {
    val scope = rememberCoroutineScope()
    val state = remember { AppState(scope, createSecureStore(), createDeviceAuth()) }
    val showSignIn = state.user == null && !state.anonymous
    // Hide content from screenshots and the recents thumbnail while the lock is on, and always on the sign-in screen.
    LaunchedEffect(state.lockEnabled, showSignIn) { setSecureWindow(state.lockEnabled || showSignIn) }
    CodebergTheme(dark = true) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when {
                state.lockEnabled && state.locked -> LockScreen(state) // Content is not even composed while locked.
                showSignIn -> SignInScreen(state)
                else -> Shell(state)
            }
        }
    }
}

/** The Codeberg logo, drawn from the official CC0 SVG path (trademark of Codeberg e.V.). */
@Composable
internal fun CodebergLogo(size: Int = 64) {
    val path = remember {
        PathParser().parsePathString(
            "M11249.461-1883.696c-12.74 0-23.067 10.327-23.067 23.067 0 4.334 1.22 8.58 3.522 12.251l19.232-24.863" +
                "c.138-.18.486-.18.624 0l19.233 24.864a23.07 23.07 0 0 0 3.523-12.252c0-12.74-10.327-23.067-23.067-23.067",
        ).toPath()
    }
    Canvas(Modifier.size(size.dp)) {
        scale(this.size.width / 4.233f, Offset.Zero) {
            translate(-1030.156f, 172.97f) { scale(0.09176f, Offset.Zero) { drawPath(path, CodebergBlue) } }
        }
    }
}

@Composable
private fun SignInScreen(state: AppState) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CodebergLogo()
        Spacer(Modifier.height(20.dp))
        Text(if (state.needOtp) "Two-factor code" else "Sign in to Codeberg", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))
        if (state.needOtp) {
            OutlinedTextField(
                value = code, onValueChange = { code = it.filter(Char::isDigit).take(6) }, singleLine = true,
                label = { Text("6-digit code") }, shape = FieldShape, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            )
        } else {
            OutlinedTextField(
                value = username, onValueChange = { username = it }, singleLine = true,
                label = { Text("Username or email") }, shape = FieldShape, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password, onValueChange = { password = it }, singleLine = true,
                label = { Text("Password") }, shape = FieldShape, modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            )
        }
        state.authError?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                if (state.needOtp) state.submitOtp(code) else {
                    state.signIn(username, password)
                    password = "" // Don't keep the password in UI state once submitted.
                }
            },
            enabled = !state.busy && (if (state.needOtp) code.length == 6 else username.isNotBlank() && password.isNotEmpty()),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) { Text(if (state.needOtp) "Verify" else "Sign in", fontWeight = FontWeight.SemiBold) }
        TextButton(onClick = { state.anonymous = true }) { Text("Browse without an account") }
        Spacer(Modifier.height(20.dp))
        Text(
            "Unofficial app. Not affiliated with or endorsed by Codeberg e.V. \u201CCodeberg\u201D and its logo are trademarks of Codeberg e.V.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
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
                    Text(repo.fullName.substringAfter('/'), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (repo.description.isNotBlank()) {
                Text(repo.description, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            val meta = buildString {
                append("\u2605 ${repo.stars}")
                if (repo.language.isNotBlank()) append("  \u00B7  ${repo.language}")
                append("  \u00B7  updated ${repo.updatedAt.substringBefore('T')}")
                if (repo.isPrivate) append("  \u00B7  private")
            }
            Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ProfileScreen(state: AppState) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("You", style = MaterialTheme.typography.headlineLarge)
        OutlinedButton(onClick = { state.push(Screen.Settings) }, shape = RoundedCornerShape(16.dp)) { Text("Settings") }
        val user = state.user
        if (user != null) {
            Text("@$user", style = MaterialTheme.typography.titleLarge)
            Text("Signed in to codeberg.org", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { state.signOut() }, shape = RoundedCornerShape(16.dp)) { Text("Sign out") }
        } else {
            Text("Browsing anonymously", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { state.anonymous = false }, shape = RoundedCornerShape(16.dp)) { Text("Sign in") }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Talks only to codeberg.org. No analytics, trackers or third-party servers.",
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
