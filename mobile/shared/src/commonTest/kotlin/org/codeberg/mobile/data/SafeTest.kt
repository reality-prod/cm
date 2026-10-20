// SPDX-License-Identifier: AGPL-3.0-or-later
package org.codeberg.mobile.data

import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope

private class MemoryStore : SecureStore {
    private val map = mutableMapOf<String, String>()
    override fun put(key: String, value: String) { map[key] = value }
    override fun get(key: String): String? = map[key]
    override fun remove(key: String) { map.remove(key) }
}

private class FakeAuth(private val ok: Boolean = true) : DeviceAuth {
    override fun available(): Boolean = true
    override fun authenticate(title: String, onResult: (Boolean) -> Unit) = onResult(ok)
}

class SafeTest {
    @Test fun acceptsPlainWebLinks() {
        assertNotNull(safeHttpUrl("https://codeberg.org/user/repo"))
        assertNotNull(safeHttpUrl("  http://example.org/a?b=c#d  "))
    }

    @Test fun rejectsDangerousOrSpoofedLinks() {
        listOf(
            "javascript:alert(1)", "intent://x#Intent;end", "file:///etc/passwd", "content://x", "ftp://x.org",
            "/relative", "", "https://codeberg.org@evil.example/", "https://user:pw@example.org/",
            "https://evil.example\\@codeberg.org/", "https://exa mple.org", "https://:80/",
            "https://" + "a".repeat(3000) + ".org",
        ).forEach { assertNull(safeHttpUrl(it), "should reject: ${it.take(40)}") }
    }

    @Test fun repoNamesCannotSteerTokenRequests() {
        assertEquals(listOf("owner", "repo"), requireSafeRepo("owner/repo"))
        listOf("../../user/tokens", "a/b/c", "a/..", "../x", "a b/c", "a/b?x=1", "", "owner").forEach {
            assertFailsWith<ApiException>(it) { requireSafeRepo(it) }
        }
    }

    @Test fun deepLinksParseOnlyKnownShapes() {
        assertEquals(Screen.RepoHome("owner/repo"), parseDeepLink("https://codeberg.org/owner/repo"))
        assertEquals(Screen.RepoHome("owner/repo"), parseDeepLink("codebergmobile://codeberg.org/owner/repo?x=1#y"))
        assertEquals(Screen.IssueView("owner/repo", 42), parseDeepLink("https://codeberg.org/owner/repo/issues/42"))
        assertEquals(Screen.IssueView("owner/repo", 7), parseDeepLink("https://codeberg.org/owner/repo/pulls/7/"))
    }

    @Test fun deepLinksRejectSpoofedOrMalformed() {
        listOf(
            "https://evil.example/owner/repo", "https://codeberg.org@evil.example/owner/repo",
            "https://codeberg.org:8443/owner/repo", "https://codeberg.org.evil.example/owner/repo",
            "http://codeberg.org/owner/repo", "javascript:alert(1)", "codebergmobile://evil.example/a/b",
            "https://codeberg.org/../../user/tokens", "https://codeberg.org/owner/repo/issues/x",
            "https://codeberg.org/owner/repo/issues/12345678901", "https://codeberg.org/owner", "",
            "https://codeberg.org/owner/repo\\..", "https://codeberg.org/owner/repo/issues/1/extra",
        ).forEach { assertNull(parseDeepLink(it), "should reject: $it") }
    }

    @Test fun pathsRejectTraversal() {
        assertEquals(emptyList(), safePathSegments(""))
        assertEquals(listOf("src", "Main.kt"), safePathSegments("src/Main.kt"))
        listOf("../x", "a/../b", "./a", "a//b", "a\\b", "a\u0000b").forEach {
            assertFailsWith<ApiException>(it) { safePathSegments(it) }
        }
    }

    @Test fun onboardingPersistsInterestsAndGuestState() {
        val store = MemoryStore()
        val scope = CoroutineScope(EmptyCoroutineContext)
        val state = AppState(scope, store, FakeAuth(true))
        assertFalse(state.onboardingDone)

        state.setInterests(listOf("Go", "Kotlin", "Rust"))
        state.finishOnboarding(asGuest = true)
        assertTrue(state.onboardingDone)
        assertTrue(state.anonymous)
        assertEquals(listOf("Go", "Kotlin", "Rust"), state.interests)

        // Re-initializing from the same store keeps onboardingDone, interests, and guest mode.
        val restored = AppState(scope, store, FakeAuth(true))
        assertTrue(restored.onboardingDone)
        assertTrue(restored.anonymous)
        assertEquals(listOf("Go", "Kotlin", "Rust"), restored.interests)

        restored.setLockEnabled(true)
        assertTrue(restored.lockEnabled)
        assertFalse(restored.blockScreenshots)

        restored.setBlockScreenshots(true)
        assertTrue(restored.blockScreenshots)
        assertTrue(AppState(scope, store, FakeAuth(true)).blockScreenshots)
    }
}
