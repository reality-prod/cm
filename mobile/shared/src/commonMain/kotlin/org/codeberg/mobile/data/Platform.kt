// SPDX-License-Identifier: AGPL-3.0-or-later
package org.codeberg.mobile.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/*
 * THE BRIDGE. Everything the shared Kotlin code needs from (or offers to) a platform lives in this file:
 *   - `expect` declarations: implemented in Platform.android.kt and Platform.ios.kt (one file per platform).
 *   - top-level functions at the bottom: called BY the hosts. From Swift they appear as `PlatformKt.<name>(...)`.
 * Keep platform-specific code out of everything else.
 */

// ---- Navigation destinations ----
sealed interface Screen {
    data class RepoHome(val full: String) : Screen
    data class DirView(val full: String, val path: String) : Screen
    data class FileView(val full: String, val path: String) : Screen
    data class IssueView(val full: String, val number: Int) : Screen
    data object Settings : Screen
}

// ---- Secure storage: Android Keystore AES-GCM / iOS Keychain ----
interface SecureStore {
    fun put(key: String, value: String)
    fun get(key: String): String?
    fun remove(key: String)
}

expect fun createSecureStore(): SecureStore

// ---- Device authentication: BiometricPrompt / LocalAuthentication ----
/**
 * Unlocks with the device's own authentication: fingerprint/face, or the device PIN/pattern/password.
 * Passkeys are not used: Android passkeys need Credential Manager (Google Play Services, banned here) and Codeberg
 * does not let apps use passkey/security-key accounts with passwords. This is local verification only.
 */
interface DeviceAuth {
    fun available(): Boolean
    /** [onResult] runs on the main thread, true only on success. */
    fun authenticate(title: String, onResult: (Boolean) -> Unit)
}

expect fun createDeviceAuth(): DeviceAuth

/** Android: FLAG_SECURE. iOS: no-op (the SwiftUI host covers the app-switcher snapshot). */
expect fun setSecureWindow(secure: Boolean)

/** Android: system back. iOS: no-op (on-screen back button). */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)

// ---- Small native touches ----
expect fun hapticTap()

/** Opens the system share sheet with [text]. */
expect fun shareText(text: String)

// ---- Called BY the hosts ----
object AppLifecycle {
    var onBackground: () -> Unit = {}
    var onForeground: () -> Unit = {}
}

/** Android: onStop. iOS: scenePhase == .background. */
fun appDidBackground() = AppLifecycle.onBackground()

/** Android: onStart. iOS: scenePhase == .active. */
fun appDidForeground() = AppLifecycle.onForeground()

object Bridge {
    /** Set by [handleDeepLink]; the UI consumes it once the user is past sign-in and the lock. */
    var pendingDeepLink by mutableStateOf<Screen?>(null)
    /** True only when the user enables "Block screenshots" in Settings (default false). */
    var blockScreenshots by mutableStateOf(false)
}

/** Android: intent data. iOS: `.onOpenURL`. Returns false if the link isn't one we understand (nothing happens). */
fun handleDeepLink(raw: String): Boolean {
    val screen = parseDeepLink(raw) ?: return false
    Bridge.pendingDeepLink = screen
    return true
}

/**
 * Accepts only `https://codeberg.org/owner/repo[/issues|pulls/N]` and the same path under
 * `codebergmobile://codeberg.org/...`. The host must be exactly codeberg.org (no userinfo, no port). Everything is
 * validated like any other server data; anything else returns null.
 */
fun parseDeepLink(raw: String): Screen? {
    val u = raw.trim()
    if (u.isEmpty() || u.length > 2048 || u.any { it.isISOControl() || it.isWhitespace() || it == '\\' }) return null
    val rest = when {
        u.startsWith("https://", ignoreCase = true) -> u.drop(8)
        u.startsWith("codebergmobile://", ignoreCase = true) -> u.drop(17)
        u.startsWith("codeberg://", ignoreCase = true) -> u.drop(11)
        else -> return null
    }
    val parts = rest.substringBefore('#').substringBefore('?').split('/').filter { it.isNotEmpty() }
    val host = parts.firstOrNull()?.lowercase()
    if (host != "codeberg.org" && host != "localhost" && host != "127.0.0.1") return null
    val path = parts.drop(1)
    return try {
        when {
            path.size == 2 -> {
                val full = path.joinToString("/")
                requireSafeRepo(full)
                Screen.RepoHome(full)
            }
            path.size >= 3 && path[2] == "blob" -> {
                val full = path[0] + "/" + path[1]
                requireSafeRepo(full)
                val filePath = path.drop(3).joinToString("/")
                Screen.FileView(full, filePath)
            }
            path.size >= 3 && path[2] == "tree" -> {
                val full = path[0] + "/" + path[1]
                requireSafeRepo(full)
                val dirPath = path.drop(3).joinToString("/")
                Screen.DirView(full, dirPath)
            }
            path.size == 4 && path[2] in setOf("issues", "pulls") && path[3].length in 1..9 && path[3].all { it.isDigit() } -> {
                val full = path[0] + "/" + path[1]
                requireSafeRepo(full)
                Screen.IssueView(full, path[3].toInt())
            }
            else -> null
        }
    } catch (e: ApiException) {
        null
    }
}
