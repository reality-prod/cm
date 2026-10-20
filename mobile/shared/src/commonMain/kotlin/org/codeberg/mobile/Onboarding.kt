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
import org.codeberg.mobile.ui.ThemeSwitcherPill
import org.codeberg.mobile.ui.Wordmark
import org.codeberg.mobile.ui.displayFamily

/** The screens of the first-run setup flow, in order. Guests skip SignIn/Hello but still personalize Interests, Inbox and Security. */
private enum class Step { Welcome, SignIn, Hello, Interests, Inbox, Security, Ready }

private val SIGNED_IN_STEPS = listOf(Step.Welcome, Step.SignIn, Step.Hello, Step.Interests, Step.Inbox, Step.Security, Step.Ready)
private val GUEST_STEPS = listOf(Step.Welcome, Step.Interests, Step.Inbox, Step.Security, Step.Ready)

internal val ALL_TOPICS = listOf("Go", "Kotlin", "Rust", "Python", "TypeScript", "C", "Haskell", "Docs", "Science", "Games", "Web", "Tools")

private val SAMPLE_REPOS = listOf(
    Repo("forgejo/forgejo", "Beyond coding. We Forge. Self-hosted lightweight software forge.", "Go", 4820, "2026-10-18T14:20:00Z"),
    Repo("codeberg/android-client", "Native Kotlin Multiplatform client for Codeberg.", "Kotlin", 940, "2026-10-19T08:15:00Z"),
    Repo("helix-editor/helix", "A post-modern modal text editor written in Rust.", "Rust", 2810, "2026-10-17T19:00:00Z"),
    Repo("woodpecker-ci/woodpecker", "Community-forked container-native CI/CD engine.", "Go", 3610, "2026-10-18T11:05:00Z"),
    Repo("codeberg/pages-server", "Static pages server for Codeberg Pages.", "Go", 615, "2026-10-16T09:30:00Z"),
    Repo("organicmaps/organicmaps", "Offline hike, bike, trails and navigation maps.", "C", 1980, "2026-10-15T20:10:00Z"),
    Repo("gajim/gajim", "Full-featured XMPP chat client written in Python.", "Python", 820, "2026-10-14T16:40:00Z"),
    Repo("penpot/penpot", "Open-source design and prototyping platform for teams.", "TypeScript", 1540, "2026-10-13T12:00:00Z"),
    Repo("pandoc/pandoc-docs", "Universal markup converter documentation and guides.", "Haskell", 730, "2026-10-12T10:15:00Z"),
    Repo("codeberg/documentation", "Community documentation and handbooks for Codeberg.org.", "Docs", 540, "2026-10-18T07:00:00Z"),
    Repo("sciml/ode-benchmarks", "Differential equation solver benchmarks for scientific computing.", "Science", 410, "2026-10-11T15:20:00Z"),
    Repo("veloren/veloren", "Open-world multiplayer voxel RPG written in Rust.", "Games", 2150, "2026-10-17T22:10:00Z"),
    Repo("f-droid/fdroidclient", "Installable catalogue of FOSS applications for Android.", "Tools", 1870, "2026-10-18T18:45:00Z"),
    Repo("activitypub/fediverse-web", "Lightweight ActivityPub web viewer and publisher.", "Web", 660, "2026-10-15T13:30:00Z"),
)

/** First run: tilted iPhone Dynamic Island mockup with live app UI + full personal setup flow. */
@Composable
fun OnboardingFlow(state: AppState) {
    var step by remember { mutableStateOf(if (state.user != null) Step.Hello else Step.Welcome) }
    var guest by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var topics by remember { mutableStateOf(state.interests.ifEmpty { listOf("Go", "Kotlin", "Rust") }) }

    // Signing in moves the flow forward on its own, and loads the account's name for the greeting.
    LaunchedEffect(state.user) {
        if (state.user != null) {
            guest = false
            state.loadProfile()
            if (step == Step.Welcome || step == Step.SignIn) step = Step.Hello
        }
    }

    val activeSteps = if (guest) GUEST_STEPS else SIGNED_IN_STEPS

    fun next() {
        val idx = activeSteps.indexOf(step)
        if (idx >= 0 && idx < activeSteps.lastIndex) {
            step = activeSteps[idx + 1]
        }
    }

    fun back() {
        val idx = activeSteps.indexOf(step)
        if (idx > 0) {
            step = activeSteps[idx - 1]
        }
    }

    val name = state.profile?.displayName ?: state.user ?: "there"
    val progress = activeSteps.indexOf(step).coerceAtLeast(0)
    val canGoBack = progress > 0 && step != Step.Hello

    when (step) {
        Step.Welcome -> OnboardingFrame(
            state = state,
            progress = progress, total = activeSteps.size,
            onBack = null,
            primary = "Get started", onPrimary = { guest = false; step = Step.SignIn },
            secondary = "Set up without an account",
            onSecondary = { guest = true; step = Step.Interests },
        ) {
            WelcomeContent()
        }

        Step.SignIn -> OnboardingFrame(
            state = state,
            progress = progress, total = activeSteps.size,
            onBack = { back() },
            primary = if (state.needOtp) "Verify 2FA code" else "Sign in to Codeberg",
            primaryEnabled = !state.busy && if (state.needOtp) code.length == 6 else username.isNotBlank() && password.isNotEmpty(),
            onPrimary = {
                if (state.needOtp) {
                    state.submitOtp(code)
                } else {
                    state.signIn(username, password)
                    password = ""
                }
            },
            secondary = "Continue without an account",
            onSecondary = { guest = true; step = Step.Interests },
        ) {
            SignInContent(
                state = state, username = username, onUsername = { username = it },
                password = password, onPassword = { password = it }, code = code, onCode = { code = it },
            )
        }

        Step.Hello -> OnboardingFrame(
            state = state,
            progress = progress, total = activeSteps.size,
            onBack = null,
            primary = "Personalise Codeberg", onPrimary = { next() },
        ) {
            PhoneMockupStage(tiltDegrees = 3.8f, phoneHeight = 268.dp) {
                PhoneProfileScreen(
                    displayName = name,
                    handle = state.user ?: "codeberg",
                    bio = state.profile?.description?.ifBlank { null } ?: "Building free and open source software on Codeberg.",
                    topics = topics,
                )
            }
            Spacer(Modifier.height(20.dp))
            Headline("Hi, $name.", "Nice to meet you. Let's make Codeberg fit the way you work.", centred = true)
        }

        Step.Interests -> OnboardingFrame(
            state = state,
            progress = progress, total = activeSteps.size,
            onBack = if (canGoBack) ({ back() }) else null,
            primary = "Continue", onPrimary = { state.setInterests(topics); next() },
        ) {
            PhoneMockupStage(tiltDegrees = -3.8f, phoneHeight = 252.dp) {
                PhoneExploreScreen(selectedTopics = topics, interactive = false)
            }
            Spacer(Modifier.height(16.dp))
            Headline("What are you into?", "Watch the phone update live as you pick topics. We'll tune Explore to your choices.")
            Spacer(Modifier.height(16.dp))
            TopicGrid(selected = topics, onToggle = { topic ->
                topics = if (topic in topics) topics - topic else topics + topic
            })
        }

        Step.Inbox -> OnboardingFrame(
            state = state,
            progress = progress, total = activeSteps.size,
            onBack = if (canGoBack) ({ back() }) else null,
            primary = "Got it", onPrimary = { next() },
        ) {
            PhoneMockupStage(tiltDegrees = 4.2f, phoneHeight = 286.dp) {
                PhoneInboxScreen()
            }
            Spacer(Modifier.height(18.dp))
            Headline(
                "Stay in the loop",
                "Notifications from Codeberg land in one Inbox, unread first. Tap inside the phone above to try triaging.",
                centred = true,
            )
        }

        Step.Security -> OnboardingFrame(
            state = state,
            progress = progress, total = activeSteps.size,
            onBack = if (canGoBack) ({ back() }) else null,
            primary = "Continue", onPrimary = { next() },
            secondary = if (!state.lockEnabled) "Skip app lock for now" else null,
            onSecondary = if (!state.lockEnabled) ({ next() }) else null,
        ) {
            PhoneMockupStage(tiltDegrees = -4.0f, phoneHeight = 254.dp) {
                PhoneSecurityScreen(locked = state.lockEnabled, timeoutSec = state.lockTimeoutSec)
            }
            Spacer(Modifier.height(18.dp))
            Headline("Lock it down", "Open Codeberg with your fingerprint, face or device PIN.")
            Spacer(Modifier.height(16.dp))
            Surface(
                shape = RoundedCornerShape(BrandRadius.lg),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("App lock", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Asked each time you come back to Codeberg.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = state.lockEnabled, onCheckedChange = { state.setLockEnabled(it) })
                    }
                    if (state.lockEnabled) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text("Lock after leaving the app", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0 to "Immediately", 60 to "1 min", 300 to "5 min").forEach { (sec, label) ->
                                Surface(onClick = { state.setLockTimeout(sec) }, color = Color.Transparent) {
                                    Chip(label, tone = if (state.lockTimeoutSec == sec) ChipTone.Green else ChipTone.Neutral)
                                }
                            }
                        }
                    }
                }
            }
            state.lockMessage?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }

        Step.Ready -> OnboardingFrame(
            state = state,
            progress = progress, total = activeSteps.size,
            onBack = if (canGoBack) ({ back() }) else null,
            primary = "Open Codeberg",
            onPrimary = {
                state.setInterests(topics)
                state.finishOnboarding(asGuest = guest || state.user == null)
            },
        ) {
            PhoneMockupStage(tiltDegrees = -3.2f, phoneHeight = 272.dp) {
                PhoneReadyScreen(
                    user = state.user,
                    displayName = if (guest || state.user == null) "Guest" else name,
                    topics = topics,
                    lockEnabled = state.lockEnabled,
                )
            }
            Spacer(Modifier.height(20.dp))
            if (guest || state.user == null) {
                Headline(
                    "You're all set.",
                    "Explore is tuned to ${if (topics.isEmpty()) "open source" else topics.joinToString(", ")}. Sign in any time from You to see your repositories and notifications.",
                    centred = true,
                )
            } else {
                Headline(
                    "You're all ready to go, $name.",
                    "Explore is tuned to ${if (topics.isEmpty()) "your interests" else topics.joinToString(", ")}. You can change anything later in You \u2192 Settings.",
                    centred = true,
                )
            }
        }
    }
}

/** Standalone sign-in page, shown after a sign-out. Same form as onboarding, with the option to browse as a guest. */
@Composable
fun SignInPage(state: AppState) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    OnboardingFrame(
        state = state,
        progress = null, total = 0,
        onBack = null,
        primary = if (state.needOtp) "Verify 2FA code" else "Sign in to Codeberg",
        primaryEnabled = !state.busy && if (state.needOtp) code.length == 6 else username.isNotBlank() && password.isNotEmpty(),
        onPrimary = {
            if (state.needOtp) {
                state.submitOtp(code)
            } else {
                state.signIn(username, password)
                password = ""
            }
        },
        secondary = "Browse without an account",
        onSecondary = { state.browseAsGuest() },
    ) {
        SignInContent(
            state = state, username = username, onUsername = { username = it },
            password = password, onPassword = { password = it }, code = code, onCode = { code = it },
        )
    }
}

@Composable
internal fun TopicGrid(selected: List<String>, onToggle: (String) -> Unit) {
    ALL_TOPICS.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { topic ->
                TopicChip(
                    label = topic,
                    selected = topic in selected,
                    modifier = Modifier.weight(1f),
                    onClick = { onToggle(topic) },
                )
            }
        }
    }
}

// ---- Tilted iPhone Mockup with Dynamic Island, Blue Circle & Codeberg Logo ------------------------------------------

/**
 * Marketing-style hero composition:
 * - A bold Codeberg Blue circle backdrop + radial glow behind the device
 * - The official Codeberg mountain mark watermarked inside the blue circle + a floating Codeberg logo medallion
 * - A tilted iPhone frame (`rotationZ = tiltDegrees`) with titanium bezel, side hardware buttons, Dynamic Island
 *   cutout (`9:41` status bar + camera dot), and Home Indicator bar, rendering live Compose UI of the real app inside.
 */
@Composable
internal fun PhoneMockupStage(
    tiltDegrees: Float = -5.0f,
    phoneHeight: Dp = 284.dp,
    phoneWidth: Dp = 216.dp,
    screenContent: @Composable () -> Unit,
) {
    val mono = LocalMonoFamily.current
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier.fillMaxWidth().height(phoneHeight + 34.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Outer ambient radial glow blending Codeberg Blue, Green, and Yellow
        Box(
            Modifier
                .size(phoneHeight + 30.dp)
                .background(
                    Brush.radialGradient(
                        listOf(
                            BrandColors.lightGreen.copy(alpha = 0.35f),
                            BrandColors.paleBlue.copy(alpha = 0.22f),
                            Color.Transparent,
                        ),
                    ),
                    CircleShape,
                ),
        )

        // The bold Codeberg Blue & Green circle behind the tilted phone
        Box(
            Modifier
                .size((phoneWidth.value * 1.08f).dp)
                .offset(x = if (tiltDegrees < 0f) (-12).dp else 12.dp, y = (-6).dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            BrandColors.brandBlue,
                            BrandColors.brightBlue,
                            BrandColors.green,
                        ),
                    ),
                )
                .border(2.dp, BrandColors.yellow.copy(alpha = 0.75f), CircleShape),
        ) {
            // Official white Codeberg mark inside the blue/green circle (white on dark/vibrant per Design/README.md)
            CodebergLogo(
                art = LogoKit.IconWhite,
                width = 128.dp,
                modifier = Modifier
                    .align(if (tiltDegrees < 0f) Alignment.TopStart else Alignment.TopEnd)
                    .offset(x = if (tiltDegrees < 0f) (-14).dp else 14.dp, y = (-10).dp)
                    .alpha(0.32f),
            )
        }

        // Tilted iPhone chassis with hardware buttons, silver/titanium border, Dynamic Island, and real app UI
        Box(
            modifier = Modifier
                .graphicsLayer {
                    rotationZ = tiltDegrees
                },
            contentAlignment = Alignment.Center,
        ) {
            // Left hardware buttons (Action button + Volume Up + Volume Down)
            Column(
                Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (-3).dp, y = 44.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.width(3.dp).height(14.dp).clip(RoundedCornerShape(2.dp)).background(BrandColors.orange))
                Spacer(Modifier.height(2.dp))
                Box(Modifier.width(3.dp).height(24.dp).clip(RoundedCornerShape(2.dp)).background(BrandColors.brandBlue))
                Box(Modifier.width(3.dp).height(24.dp).clip(RoundedCornerShape(2.dp)).background(BrandColors.brandBlue))
            }

            // Right hardware button (Power / Side button)
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 3.dp, y = 74.dp)
                    .width(3.dp)
                    .height(34.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(BrandColors.brandBlue),
            )

            // Outer Titanium Frame + Inner Bezel
            Box(
                Modifier
                    .width(phoneWidth)
                    .height(phoneHeight)
                    .shadow(20.dp, RoundedCornerShape(36.dp), clip = false)
                    .clip(RoundedCornerShape(36.dp))
                    .background(BrandColors.navy)
                    .border(
                        width = 2.5.dp,
                        brush = Brush.linearGradient(
                            listOf(
                                BrandColors.lightGreen,
                                BrandColors.brightBlue,
                                BrandColors.yellow,
                            ),
                        ),
                        shape = RoundedCornerShape(36.dp),
                    )
                    .padding(5.dp),
            ) {
                // Live App Screen Viewport (adapts to current Light or Dark theme!)
                Column(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(31.dp))
                        .background(scheme.background)
                        .border(1.dp, scheme.outlineVariant, RoundedCornerShape(31.dp)),
                ) {
                    // Status Bar with iPhone Dynamic Island
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "9:41",
                            fontFamily = mono,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onBackground,
                        )

                        // Dynamic Island pill cutout
                        Row(
                            Modifier
                                .width(68.dp)
                                .height(18.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color.Black)
                                .border(0.75.dp, BrandColors.darkOutline, RoundedCornerShape(50))
                                .padding(horizontal = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            // Live activity indicator dot on the left of Dynamic Island
                            Box(
                                Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(BrandColors.yellow),
                            )
                            // Front camera lens ring on the right of Dynamic Island
                            Box(
                                Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(BrandColors.navy)
                                    .border(1.dp, BrandColors.green, CircleShape),
                            )
                        }

                        // Signal + Battery status icons
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(1.5.dp)) {
                                Box(Modifier.width(2.dp).height(4.dp).clip(RoundedCornerShape(1.dp)).background(scheme.onBackground))
                                Box(Modifier.width(2.dp).height(6.dp).clip(RoundedCornerShape(1.dp)).background(scheme.onBackground))
                                Box(Modifier.width(2.dp).height(8.dp).clip(RoundedCornerShape(1.dp)).background(scheme.onBackground))
                            }
                            Box(
                                Modifier
                                    .width(14.dp)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .border(1.dp, scheme.onBackground.copy(alpha = 0.75f), RoundedCornerShape(2.dp))
                                    .padding(1.5.dp),
                            ) {
                                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(1.dp)).background(BrandColors.green))
                            }
                        }
                    }

                    // Real App Screen Content
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        screenContent()
                    }

                    // Bottom iOS Home Indicator
                    Box(
                        Modifier.fillMaxWidth().padding(top = 3.dp, bottom = 5.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .width(68.dp)
                                .height(3.5.dp)
                                .clip(CircleShape)
                                .background(scheme.onBackground.copy(alpha = 0.35f)),
                        )
                    }
                }
            }
        }

        // Floating official Codeberg logo badge overlapping the corner of the phone and circle
        Surface(
            shape = CircleShape,
            color = Color.White,
            shadowElevation = 12.dp,
            border = BorderStroke(2.dp, BrandColors.yellow),
            modifier = Modifier
                .align(if (tiltDegrees < 0f) Alignment.BottomEnd else Alignment.BottomStart)
                .offset(x = if (tiltDegrees < 0f) (-28).dp else 28.dp, y = (-6).dp)
                .size(52.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                CodebergLogo(LogoKit.IconBlue, 36.dp)
            }
        }
    }
}

// ---- Live App Screens Rendered Inside the Tilted iPhone Mockup ------------------------------------------------------

@Composable
private fun PhoneExploreScreen(selectedTopics: List<String>, interactive: Boolean = true) {
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
private fun PhoneRepoCodeScreen() {
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
private fun PhoneSignInScreen(username: String, needOtp: Boolean, code: String) {
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
private fun PhoneProfileScreen(
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
private fun PhoneInboxScreen(showBottomNav: Boolean = true) {
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
private fun PhoneSecurityScreen(locked: Boolean, timeoutSec: Int) {
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
private fun PhoneReadyScreen(
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

// ---- Frame ---------------------------------------------------------------------------------------------------------

/** Every onboarding screen shares this layout: progress dots + theme switcher + optional back button, the step's content, one primary button, an optional text button. */
@Composable
private fun OnboardingFrame(
    state: AppState,
    progress: Int?,
    total: Int,
    onBack: (() -> Unit)? = null,
    primary: String,
    onPrimary: () -> Unit,
    primaryEnabled: Boolean = true,
    secondary: String? = null,
    onSecondary: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // A soft Codeberg Blue + Green + Yellow glow behind the top of every step.
        Box(
            Modifier
                .fillMaxWidth()
                .height(340.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            BrandColors.lightGreen.copy(alpha = 0.25f),
                            BrandColors.paleBlue.copy(alpha = 0.15f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 14.dp)) {
            Row(
                Modifier.fillMaxWidth().height(36.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (onBack != null) {
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                .clickable(onClick = onBack),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("\u2039", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    if (progress != null && total > 0) {
                        ProgressDots(index = progress, total = total)
                    }
                }
                ThemeSwitcherPill(mode = state.themeMode, onSelect = state::setThemeMode)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 12.dp)) {
                content()
            }
            Spacer(Modifier.height(12.dp))
            BrandButton(text = primary, enabled = primaryEnabled, onClick = onPrimary)
            if (secondary != null && onSecondary != null) {
                TextButton(onClick = onSecondary, modifier = Modifier.fillMaxWidth()) {
                    Text(secondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** The blue Codeberg button. Used for every primary action in onboarding and sign-in. */
@Composable
private fun BrandButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(BrandRadius.lg),
        colors = ButtonDefaults.buttonColors(
            containerColor = BrandColors.brandBlue,
            contentColor = Color.White,
            disabledContainerColor = BrandColors.brandBlue.copy(alpha = 0.35f),
            disabledContentColor = Color.White.copy(alpha = 0.7f),
        ),
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ProgressDots(index: Int, total: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { i ->
            val color = when {
                i == index -> BrandColors.brandBlue
                i < index -> BrandColors.green
                else -> MaterialTheme.colorScheme.outlineVariant
            }
            Box(
                Modifier
                    .height(6.dp)
                    .width(if (i == index) 24.dp else 6.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

/** A large Space Grotesk display title with a supporting Inter line underneath. */
@Composable
private fun Headline(title: String, body: String, centred: Boolean = false) {
    val align = if (centred) TextAlign.Center else TextAlign.Start
    val display = displayFamily()
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (centred) Alignment.CenterHorizontally else Alignment.Start) {
        Text(title, style = MaterialTheme.typography.displaySmall.copy(fontFamily = display), textAlign = align)
        Spacer(Modifier.height(10.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = align)
    }
}

// ---- Steps -----------------------------------------------------------------------------------------------------------

@Composable
private fun WelcomeContent() {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        PhoneMockupStage(tiltDegrees = -5.5f, phoneHeight = 290.dp) {
            PhoneExploreScreen(selectedTopics = listOf("Go", "Kotlin", "Rust"), interactive = true)
        }
        Spacer(Modifier.height(20.dp))
        Headline(
            "Your Codeberg,\nin your pocket.",
            "Browse repositories, read code and issues, and triage notifications. Open source, with no trackers.",
            centred = true,
        )
    }
}

@Composable
private fun SignInContent(
    state: AppState,
    username: String,
    onUsername: (String) -> Unit,
    password: String,
    onPassword: (String) -> Unit,
    code: String,
    onCode: (String) -> Unit,
) {
    PhoneMockupStage(tiltDegrees = -3.5f, phoneHeight = 212.dp, phoneWidth = 204.dp) {
        PhoneSignInScreen(username = username, needOtp = state.needOtp, code = code)
    }
    Spacer(Modifier.height(16.dp))
    Headline("Sign in to Codeberg", "Use your username or email. Your password is used once to mint a scoped token and is never stored.")
    Spacer(Modifier.height(20.dp))
    if (state.needOtp) {
        OutlinedTextField(
            value = code,
            onValueChange = { onCode(it.filter(Char::isDigit).take(6)) },
            singleLine = true,
            label = { Text("6-digit code") },
            shape = RoundedCornerShape(BrandRadius.md),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        OutlinedTextField(
            value = username,
            onValueChange = onUsername,
            singleLine = true,
            label = { Text("Username or email") },
            shape = RoundedCornerShape(BrandRadius.md),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = onPassword,
            singleLine = true,
            label = { Text("Password") },
            shape = RoundedCornerShape(BrandRadius.md),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    state.authError?.let {
        Spacer(Modifier.height(14.dp))
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
internal fun TopicChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val bucket = (label.hashCode() and 0x7FFFFFFF) % 3
    val selectedBg = when (bucket) {
        0 -> BrandColors.brandBlue
        1 -> BrandColors.darkGreen
        else -> BrandColors.brown
    }
    val selectedBorder = when (bucket) {
        0 -> BrandColors.brightBlue
        1 -> BrandColors.green
        else -> BrandColors.yellow
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) selectedBg else scheme.surfaceVariant,
        border = BorderStroke(1.5.dp, if (selected) selectedBorder else scheme.outlineVariant),
        modifier = modifier,
    ) {
        Box(Modifier.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) Color.White else scheme.onSurface,
            )
        }
    }
}

