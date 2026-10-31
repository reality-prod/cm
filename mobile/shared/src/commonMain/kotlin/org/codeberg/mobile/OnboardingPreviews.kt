// SPDX-License-Identifier: AGPL-3.0-or-later
package org.codeberg.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.codeberg.mobile.data.AppState
import org.codeberg.mobile.data.Repo
import org.codeberg.mobile.ui.Avatar
import org.codeberg.mobile.ui.BrandColors
import org.codeberg.mobile.ui.BrandRadius
import org.codeberg.mobile.ui.Chip
import org.codeberg.mobile.ui.ChipTone
import org.codeberg.mobile.ui.CodebergLogo
import org.codeberg.mobile.ui.CodebergMark
import org.codeberg.mobile.ui.IconTile
import org.codeberg.mobile.ui.LocalMonoFamily
import org.codeberg.mobile.ui.LogoKit
import org.codeberg.mobile.ui.Wordmark
import org.codeberg.mobile.ui.displayFamily

/** The screens of the first-run setup flow, in order. Guests skip SignIn/Hello but still personalize Interests, Inbox and Security. */
// ---- Live App Screens Rendered Inside the Tilted iPhone Mockup ------------------------------------------------------

@Composable
internal fun PhoneExploreScreen(selectedTopics: List<String>, interactive: Boolean = true) {
    var previewTab by remember { mutableIntStateOf(0) }
    val s = MaterialTheme.colorScheme
    val matchingRepos = remember(selectedTopics) {
        if (selectedTopics.isEmpty()) SAMPLE_REPOS.take(3)
        else {
            val matched = SAMPLE_REPOS.filter { it.language in selectedTopics }
            (matched + SAMPLE_REPOS.filterNot { it in matched }).take(3)
        }
    }

    Box(Modifier.fillMaxSize()) {
        when (previewTab) {
            0 -> Column(
                Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Wordmark(width = 72.dp)
                    Chip("Explore", tone = ChipTone.Green)
                }

                // Topic pills inside the phone's Explore header
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val pills = selectedTopics.ifEmpty { listOf("Go", "Kotlin", "Rust") }
                    pills.take(4).forEachIndexed { idx, topic ->
                        val active = idx == 0
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .background(if (active) BrandColors.brandBlue else s.surfaceVariant)
                                .border(0.75.dp, if (active) BrandColors.brightBlue else s.outlineVariant, CircleShape)
                                .padding(horizontal = 7.dp, vertical = 2.5.dp),
                        ) {
                            Text(
                                topic,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (active) Color.White else s.onSurface,
                            )
                        }
                    }
                }

                // Real repository cards scaled for the phone viewport
                matchingRepos.take(2).forEach { repo ->
                    MiniRepoCard(repo)
                }
            }
            1 -> PhoneRepoCodeScreen()
            2 -> PhoneInboxScreen(showBottomNav = false)
            else -> PhoneProfileScreen(
                displayName = "Codeberg",
                handle = "codeberg",
                bio = "Free and open-source software hosting powered by Forgejo.",
                topics = selectedTopics,
                showBottomNav = false,
            )
        }

        // Real floating navigation pill at the bottom of the phone screen
        MiniFloatingNav(
            selected = previewTab,
            onSelect = { if (interactive) previewTab = it },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp),
        )
    }
}

@Composable
private fun MiniRepoCard(repo: Repo) {
    val s = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = s.surfaceVariant,
        border = BorderStroke(1.dp, s.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Avatar(repo.fullName, 20.dp)
                Column(Modifier.weight(1f)) {
                    Text(
                        repo.fullName,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = s.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(BrandColors.cream)
                        .border(0.75.dp, BrandColors.orange, CircleShape)
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                ) {
                    Text(
                        "\u2605 ${repo.stars}",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandColors.brown,
                        fontFamily = LocalMonoFamily.current,
                    )
                }
            }
            Text(
                repo.description,
                fontSize = 8.5.sp,
                lineHeight = 11.sp,
                color = s.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(BrandColors.green))
                Text(repo.language, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, color = s.onSurface)
                Text("\u00B7 updated today", fontSize = 8.sp, color = s.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun PhoneRepoCodeScreen() {
    val s = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Avatar("forgejo/forgejo", 22.dp)
            Column(Modifier.weight(1f)) {
                Text("forgejo", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = s.onSurface)
                Text("forgejo/forgejo \u00B7 main", fontSize = 8.5.sp, color = s.onSurfaceVariant)
            }
            Chip("\u2605 4.8k", tone = ChipTone.Yellow)
        }
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = s.surfaceVariant,
            border = BorderStroke(1.dp, s.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("dir" to "models", "dir" to "routers", "file" to "main.go", "file" to "README.md").forEach { (kind, name) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconTile(if (kind == "dir") "/" else "GO", kind == "dir")
                        Text(name, fontSize = 9.5.sp, color = s.onSurface, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
internal fun PhoneSignInScreen(username: String, needOtp: Boolean, code: String) {
    val handle = username.trim().ifBlank { "you" }
    val s = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Wordmark(width = 76.dp)
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = s.surfaceVariant,
            border = BorderStroke(1.dp, s.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Avatar(handle, 26.dp)
                    Column(Modifier.weight(1f)) {
                        Text("@$handle", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = s.onSurface, maxLines = 1)
                        Text("codeberg.org \u00B7 TLS pinned", fontSize = 8.sp, fontWeight = FontWeight.SemiBold, color = BrandColors.darkGreen)
                    }
                }
                HorizontalDivider(color = s.outlineVariant)
                if (needOtp) {
                    Text("2FA verification", fontSize = 8.5.sp, color = BrandColors.orange, fontWeight = FontWeight.SemiBold)
                    Text(
                        code.padEnd(6, '\u2022').chunked(1).joinToString(" "),
                        fontFamily = LocalMonoFamily.current,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = s.onSurface,
                    )
                } else {
                    Text("Scoped token (read-only)", fontSize = 8.5.sp, color = s.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("repo", "issue", "notif", "user").forEachIndexed { idx, scopeName ->
                            val bg = if (idx % 2 == 0) BrandColors.iceBlue else BrandColors.cream
                            val fg = if (idx % 2 == 0) BrandColors.darkGreen else BrandColors.brown
                            Box(
                                Modifier
                                    .clip(CircleShape)
                                    .background(bg)
                                    .padding(horizontal = 5.dp, vertical = 2.dp),
                            ) {
                                Text(scopeName, fontSize = 7.5.sp, fontWeight = FontWeight.SemiBold, color = fg, fontFamily = LocalMonoFamily.current)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PhoneProfileScreen(
    displayName: String,
    handle: String,
    bio: String,
    topics: List<String>,
    showBottomNav: Boolean = true,
) {
    val s = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = s.surfaceVariant,
                border = BorderStroke(1.dp, s.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Avatar(displayName, 32.dp)
                        Column(Modifier.weight(1f)) {
                            Text(displayName, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = s.onSurface, maxLines = 1)
                            Text("@$handle", fontSize = 9.sp, color = BrandColors.brandBlue, fontFamily = LocalMonoFamily.current)
                        }
                    }
                    Text(bio, fontSize = 8.5.sp, lineHeight = 11.sp, color = s.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        topics.take(3).forEachIndexed { idx, t ->
                            val bg = if (idx % 2 == 0) BrandColors.iceBlue else BrandColors.cream
                            val fg = if (idx % 2 == 0) BrandColors.darkGreen else BrandColors.brown
                            Box(
                                Modifier
                                    .clip(CircleShape)
                                    .background(bg)
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(t, fontSize = 7.5.sp, fontWeight = FontWeight.SemiBold, color = fg)
                            }
                        }
                    }
                }
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = s.surfaceVariant,
                border = BorderStroke(1.dp, s.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Pinned Let's Encrypt CAs", fontSize = 8.5.sp, color = s.onSurface)
                    Box(Modifier.size(7.dp).clip(CircleShape).background(BrandColors.green))
                }
            }
        }
        if (showBottomNav) {
            MiniFloatingNav(selected = 3, onSelect = {}, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp))
        }
    }
}

@Composable
internal fun PhoneInboxScreen(showBottomNav: Boolean = true) {
    var allTab by remember { mutableStateOf(false) }
    var readFirst by remember { mutableStateOf(false) }
    val s = MaterialTheme.colorScheme
    val mono: FontFamily = LocalMonoFamily.current
    val items = listOf(
        Triple("Issue", "Crash on startup after first sign-in", "forgejo/forgejo"),
        Triple("Pull", "Add dark mode for the code viewer", "codeberg/pages-server"),
        Triple("Release", "v1.4.0 is out", "woodpecker-ci/woodpecker"),
    )

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Inbox", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = s.onSurface)
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(s.surfaceVariant)
                        .border(0.75.dp, s.outlineVariant, CircleShape)
                        .padding(2.dp),
                ) {
                    listOf("Unread" to false, "All" to true).forEach { (label, isAll) ->
                        val active = allTab == isAll
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .background(if (active) BrandColors.brandBlue else Color.Transparent)
                                .clickable { allTab = isAll }
                                .padding(horizontal = 8.dp, vertical = 2.5.dp),
                        ) {
                            Text(
                                label,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (active) Color.White else s.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            items.take(2).forEachIndexed { idx, (kind, title, repo) ->
                val unread = if (idx == 0) !readFirst else !allTab
                Surface(
                    onClick = { if (idx == 0) readFirst = !readFirst },
                    shape = RoundedCornerShape(12.dp),
                    color = s.surfaceVariant,
                    border = BorderStroke(1.dp, if (unread) BrandColors.orange else s.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Box(
                                Modifier
                                    .clip(CircleShape)
                                    .background(if (unread) BrandColors.cream else BrandColors.iceBlue)
                                    .padding(horizontal = 5.dp, vertical = 1.dp),
                            ) {
                                Text(
                                    kind.uppercase(),
                                    fontSize = 7.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (unread) BrandColors.brown else BrandColors.darkGreen,
                                )
                            }
                            if (unread) Box(Modifier.size(5.dp).clip(CircleShape).background(BrandColors.orange))
                            Spacer(Modifier.weight(1f))
                            Text(if (unread) "Mark read" else "Read", fontSize = 7.5.sp, fontWeight = FontWeight.SemiBold, color = BrandColors.brandBlue)
                        }
                        Text(
                            title,
                            fontSize = 9.5.sp,
                            fontWeight = if (unread) FontWeight.Bold else FontWeight.Medium,
                            color = s.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(repo, fontSize = 8.sp, fontFamily = mono, color = s.onSurfaceVariant)
                    }
                }
            }
        }
        if (showBottomNav) {
            MiniFloatingNav(selected = 2, onSelect = {}, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp))
        }
    }
}

@Composable
internal fun PhoneSecurityScreen(locked: Boolean, timeoutSec: Int) {
    val s = MaterialTheme.colorScheme
    val timeoutLabel = when (timeoutSec) {
        0 -> "Immediately"
        60 -> "After 1 min"
        else -> "After 5 min"
    }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CodebergMark(44.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            if (locked) "Codeberg is locked" else "Biometric shield ready",
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            color = s.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (locked) "$timeoutLabel \u00B7 Biometric or PIN" else "Toggle App lock below to require biometric or PIN",
            fontSize = 8.5.sp,
            color = s.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(if (locked) BrandColors.darkGreen else BrandColors.brandBlue)
                .padding(vertical = 7.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (locked) "Unlock with Biometrics" else "Unlocked",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}

@Composable
internal fun PhoneReadyScreen(
    user: String?,
    displayName: String,
    topics: List<String>,
    lockEnabled: Boolean,
) {
    val s = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Wordmark(width = 72.dp)
                Avatar(displayName, 22.dp)
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = s.surfaceVariant,
                border = BorderStroke(1.dp, BrandColors.green),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        if (user != null) "Signed in as @$user" else "Guest workspace ready",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = s.onSurface,
                    )
                    Text(
                        buildString {
                            append(if (lockEnabled) "App lock active" else "Pinned HTTPS")
                            append(" \u00B7 ")
                            append("${topics.size} topics tuned")
                        },
                        fontSize = 8.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandColors.darkGreen,
                    )
                }
            }
            val firstRepo = SAMPLE_REPOS.firstOrNull { it.language in topics } ?: SAMPLE_REPOS.first()
            MiniRepoCard(firstRepo)
        }
        MiniFloatingNav(selected = 0, onSelect = {}, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp))
    }
}

@Composable
private fun MiniFloatingNav(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val s = MaterialTheme.colorScheme
    val labels = listOf("Explore", "Repos", "Inbox", "You")
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = s.surfaceVariant,
        border = BorderStroke(1.dp, s.outlineVariant),
        shadowElevation = 6.dp,
    ) {
        Row(Modifier.padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            labels.forEachIndexed { idx, label ->
                val active = idx == selected
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(if (active) BrandColors.brandBlue else Color.Transparent)
                        .clickable { onSelect(idx) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        label,
                        fontSize = 8.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        color = if (active) Color.White else s.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
