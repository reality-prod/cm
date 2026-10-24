// SPDX-License-Identifier: AGPL-3.0-or-later
package org.codeberg.mobile.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmName
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.codeberg.mobile.ui.ImagePrefs

private const val TOKEN = "access_token"
private const val USER = "username"
private const val ONBOARDING = "onboarding_done"
private const val ANONYMOUS = "anonymous_mode"
private const val INTERESTS = "interests"
private const val EXTERNAL_IMAGES = "external_images"

// TODO(verify-api): scope names. Only what the current write feature needs, nothing broader.
private val WRITE_SCOPES = listOf("read:notification", "write:notification")

private const val TOKEN_ID = "access_token_id"
private const val LOCK = "app_lock"
private const val LOCK_TIMEOUT = "app_lock_timeout"
private const val BLOCK_SCREENSHOTS = "block_screenshots"
private const val THEME_MODE = "theme_mode"

enum class ThemeMode(val label: String) { Light("Light"), Dark("Dark"), System("System") }

class AppState(
    private val scope: CoroutineScope,
    private val store: SecureStore,
    private val auth: DeviceAuth,
) {
    var user by mutableStateOf<String?>(null); private set
    var anonymous by mutableStateOf(false)
    var needOtp by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var authError by mutableStateOf<String?>(null); private set

    // ---- ONBOARDING AND PERSONALISATION ----
    var onboardingDone by mutableStateOf(false); private set
    var interests by mutableStateOf<List<String>>(emptyList()); private set
    var activeInterest by mutableStateOf<String?>(null); private set
    var themeMode by mutableStateOf(ThemeMode.Light); private set
    var profile by mutableStateOf<Profile?>(null); private set

    @JvmName("updateThemeMode")
    fun setThemeMode(mode: ThemeMode) {
        themeMode = mode
        store.put(THEME_MODE, mode.name)
    }

    fun loadProfile() {
        val login = user ?: return
        scope.launch {
            profile = try {
                api.currentUser()
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                Profile(login = login) // Name unknown: greet with the username instead.
            }
        }
    }

    @JvmName("updateInterests")
    /** Off by default. When on, images from other sites load without a tap, and those sites see your address. */
    var externalImages by mutableStateOf(false); private set

    fun setExternalImages(on: Boolean) {
        externalImages = on
        org.codeberg.mobile.ui.ImagePrefs.allowExternal = on
        store.put(EXTERNAL_IMAGES, if (on) "1" else "0")
    }

    fun setInterests(topics: List<String>) {
        interests = topics
        store.put(INTERESTS, topics.joinToString(","))
        if (activeInterest != null && activeInterest !in topics) {
            activeInterest = null
        }
    }

    fun selectInterest(topic: String?) {
        activeInterest = topic
        search(query)
    }

    fun finishOnboarding(asGuest: Boolean = user == null) {
        if (asGuest && user == null) {
            anonymous = true
            store.put(ANONYMOUS, "1")
        }
        store.put(ONBOARDING, "1")
        onboardingDone = true
    }

    fun browseAsGuest() {
        anonymous = true
        store.put(ANONYMOUS, "1")
    }

    fun promptSignIn() {
        anonymous = false
        store.remove(ANONYMOUS)
    }

    fun restartOnboarding() {
        store.put(ONBOARDING, "0")
        stack.clear()
        onboardingDone = false
    }

    var repos by mutableStateOf<List<Repo>>(emptyList()); private set
    var hasMore by mutableStateOf(false); private set
    var loading by mutableStateOf(false); private set
    var listError by mutableStateOf<String?>(null); private set

    // PHASE 2: write access. Held in memory only (never persisted), created after a password re-entry.
    var writeToken by mutableStateOf<String?>(null); private set
    var elevating by mutableStateOf(false); private set
    var elevateError by mutableStateOf<String?>(null); private set
    var actionError by mutableStateOf<String?>(null)

    /** Re-authenticate and mint a short-lived-in-memory write token. The password is used once and not kept. */
    fun elevate(password: String, otp: String?, then: () -> Unit) {
        val u = user ?: return
        elevating = true
        elevateError = null
        scope.launch {
            try {
                val s = api.signIn(u, password, otp?.takeIf { it.isNotBlank() }, WRITE_SCOPES, "Codeberg (write session)")
                writeToken = s.token
                writeTokenId = s.tokenId
                then()
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                elevateError = errorText(e.error)
            } finally {
                elevating = false
            }
        }
    }

    fun markRead(id: Long, onDone: () -> Unit) {
        val t = writeToken ?: return
        scope.launch {
            try {
                api.markRead(id, t)
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                if (e.error == ApiError.Auth) writeToken = null // Token revoked/expired: ask for the password again.
                actionError = errorText(e.error)
            }
        }
    }

    val stack = mutableStateListOf<Screen>()
    /** An external link waiting for the user's confirmation. Nothing opens without it. */
    var pendingLink by mutableStateOf<String?>(null)

    fun push(screen: Screen) {
        stack.add(screen)
    }

    fun pop() {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
    }

    val api = ForgejoApi(token = { store.get(TOKEN) }, onExpired = { expire() })
    private var query = ""
    private var page = 1
    private var generation = 0

    // Held only between the password step and the 2FA step, cleared as soon as the flow ends.
    private var pendingUser = ""
    private var pendingPassword = ""

    init {
        val saved = store.get(USER)
        if (saved != null && store.get(TOKEN) != null) user = saved
        anonymous = user == null && store.get(ANONYMOUS) == "1"
        // Onboarding is finished for anyone who was already signed in before it existed.
        onboardingDone = store.get(ONBOARDING) == "1" || user != null
        interests = store.get(INTERESTS)?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
        externalImages = store.get(EXTERNAL_IMAGES) == "1"
        org.codeberg.mobile.ui.ImagePrefs.allowExternal = externalImages
        themeMode = store.get(THEME_MODE)?.let { savedMode -> ThemeMode.entries.firstOrNull { it.name == savedMode } } ?: ThemeMode.Light
        if (user != null) loadProfile()
    }

    // ---- APP LOCK (device authentication: biometrics or device PIN/pattern/password) ----
    var lockEnabled by mutableStateOf(false); private set
    var lockTimeoutSec by mutableStateOf(60); private set
    /** Off by default so screenshots work everywhere unless explicitly enabled in Settings. */
    var blockScreenshots by mutableStateOf(false); private set
    /** Starts true: until settings are read the app stays locked (fail closed). */
    var locked by mutableStateOf(true); private set
    var lockMessage by mutableStateOf<String?>(null); private set
    private var authInFlight = false // The system prompt itself backgrounds the app; ignore that.
    private var backgroundedAt: TimeMark? = null

    private fun initLock() {
        lockEnabled = store.get(LOCK) == "1"
        lockTimeoutSec = store.get(LOCK_TIMEOUT)?.toIntOrNull()?.takeIf { it in setOf(0, 60, 300) } ?: 60
        blockScreenshots = store.get(BLOCK_SCREENSHOTS) == "1"
        Bridge.blockScreenshots = blockScreenshots
        locked = lockEnabled
        AppLifecycle.onBackground = { if (!authInFlight) backgroundedAt = TimeSource.Monotonic.markNow() }
        AppLifecycle.onForeground = {
            val t = backgroundedAt
            if (lockEnabled && t != null && t.elapsedNow().inWholeSeconds >= lockTimeoutSec) locked = true
            backgroundedAt = null
        }
    }

    @JvmName("updateBlockScreenshots")
    fun setBlockScreenshots(block: Boolean) {
        blockScreenshots = block
        Bridge.blockScreenshots = block
        store.put(BLOCK_SCREENSHOTS, if (block) "1" else "0")
    }

    fun unlock() {
        if (authInFlight) return
        authInFlight = true
        auth.authenticate("Unlock Codeberg") { ok ->
            authInFlight = false
            if (ok) {
                locked = false
                lockMessage = null
            } else {
                lockMessage = "Couldn't verify it's you."
            }
        }
    }

    /** Turning the lock on or off requires authenticating first, so nobody else can change it. */
    @JvmName("updateLockEnabled")
    fun setLockEnabled(enable: Boolean) {
        if (authInFlight) return
        if (enable && !auth.available()) {
            lockMessage = "Set up a screen lock or biometrics in system settings first."
            return
        }
        authInFlight = true
        auth.authenticate(if (enable) "Turn on app lock" else "Turn off app lock") { ok ->
            authInFlight = false
            if (ok) {
                lockEnabled = enable
                store.put(LOCK, if (enable) "1" else "0")
                locked = false
                lockMessage = null
            } else {
                lockMessage = "Couldn't verify it's you."
            }
        }
    }

    fun setLockTimeout(seconds: Int) {
        lockTimeoutSec = seconds
        store.put(LOCK_TIMEOUT, seconds.toString())
    }

    /** Escape hatch when device authentication is unavailable: wipe the session and drop the lock. */
    fun resetFromLock() {
        signOut()
        lockEnabled = false
        store.put(LOCK, "0")
        locked = false
        lockMessage = null
    }

    // ---- TOKEN REVOCATION ----
    var revoking by mutableStateOf(false); private set
    var revokeError by mutableStateOf<String?>(null); private set
    private var writeTokenId = 0L

    // Must stay below every property it touches: Kotlin runs init blocks in textual order.
    init {
        initLock()
    }

    /** Deletes this app's tokens on Codeberg (needs the password once; it is not kept), then signs out. */
    fun signOutAndRevoke(password: String, otp: String?) {
        val u = user ?: return
        val ids = listOfNotNull(store.get(TOKEN_ID)?.toLongOrNull(), writeTokenId.takeIf { it > 0 })
        revoking = true
        revokeError = null
        scope.launch {
            try {
                api.revokeTokens(u, password, otp?.takeIf { it.isNotBlank() }, ids)
                signOut()
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                revokeError = errorText(e.error)
            } finally {
                revoking = false
            }
        }
    }

    fun signIn(username: String, password: String) {
        pendingUser = username.trim()
        pendingPassword = password
        attempt(otp = null)
    }

    fun submitOtp(code: String) = attempt(otp = code.trim())

    private fun attempt(otp: String?) {
        busy = true
        authError = null
        scope.launch {
            try {
                val s = api.signIn(pendingUser, pendingPassword, otp)
                try {
                    store.put(TOKEN, s.token)
                    store.put(USER, s.login)
                    store.put(TOKEN_ID, s.tokenId.toString())
                } catch (e: Exception) {
                    authError = "Secure storage isn't available on this platform yet."
                    return@launch
                }
                clearPending()
                needOtp = false
                anonymous = false
                user = s.login
                search("")
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                if (e.error == ApiError.TwoFactor && otp == null) {
                    needOtp = true
                } else {
                    authError = errorText(e.error)
                    if (e.error != ApiError.Auth || otp == null) {
                        clearPending()
                        needOtp = false
                    }
                }
            } finally {
                busy = false
            }
        }
    }

    private fun clearPending() {
        pendingUser = ""
        pendingPassword = ""
    }

    fun signOut() {
        store.remove(TOKEN)
        store.remove(USER)
        store.remove(TOKEN_ID)
        store.remove(ANONYMOUS)
        writeTokenId = 0
        user = null
        profile = null
        anonymous = false
        repos = emptyList()
        stack.clear()
        pendingLink = null
        writeToken = null
        actionError = null
    }

    /** A 401 on a token request: wipe the dead session and return to sign-in with a message. */
    private fun expire() {
        signOut()
        authError = "Session expired, sign in again."
    }

    fun search(q: String) {
        query = q.trim()
        page = 1
        repos = emptyList()
        hasMore = false
        load(reset = true)
    }

    fun loadMore() {
        if (!loading && hasMore) load(reset = false)
    }

    private fun load(reset: Boolean) {
        val gen = if (reset) ++generation else generation // A newer search invalidates older in-flight ones.
        loading = true
        listError = null
        val effectiveQuery = listOfNotNull(query.takeIf { it.isNotBlank() }, activeInterest).joinToString(" ")
        scope.launch {
            try {
                val result = api.searchRepos(effectiveQuery, page)
                if (gen != generation) return@launch
                repos = if (reset) result.items else repos + result.items
                hasMore = result.hasMore
                page++
            } catch (e: ApiException) {
                if (gen == generation) listError = errorText(e.error)
            } finally {
                if (gen == generation) loading = false
            }
        }
    }
}
