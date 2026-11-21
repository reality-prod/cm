// SPDX-License-Identifier: AGPL-3.0-or-later
@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package org.codeberg.mobile.data

import androidx.compose.runtime.Composable
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.convert
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.NSData
import platform.Foundation.create
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthentication
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleWhenUnlockedThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.memcpy
import org.codeberg.mobile.nativecore.CbTextStats
import org.codeberg.mobile.nativecore.codeberg_compact_count
import org.codeberg.mobile.nativecore.codeberg_file_badge
import org.codeberg.mobile.nativecore.codeberg_format_size
import org.codeberg.mobile.nativecore.codeberg_line_count
import org.codeberg.mobile.nativecore.codeberg_relative_time
import org.codeberg.mobile.nativecore.codeberg_text_stats

/*
 * UNVERIFIED: this whole file was written without a Mac. Compile and test on macOS before any release;
 * Kotlin/Native interop for Keychain and UIKit is easy to get subtly wrong.
 */

// ---- Secure storage: Keychain, "when unlocked, this device only" (not backed up, not synced, not readable while locked) ----
private const val SERVICE = "org.codeberg.mobile.secrets"

private fun newQuery(key: String): CFMutableDictionaryRef? {
    val q = CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
    CFDictionaryAddValue(q, kSecClass, kSecClassGenericPassword)
    addRetained(q, kSecAttrService, SERVICE)
    addRetained(q, kSecAttrAccount, key)
    return q
}

/** The dictionary retains what it is given, so release our own +1 from CFBridgingRetain to avoid a leak. */
private fun addRetained(dict: CFMutableDictionaryRef?, k: CFStringRef?, v: Any?) {
    val ref = CFBridgingRetain(v)
    CFDictionaryAddValue(dict, k, ref)
    CFRelease(ref)
}

private fun ByteArray.toNSData(): NSData = memScoped {
    NSData.create(bytes = allocArrayOf(this@toNSData), length = size.toULong())
}

private fun NSData.toByteArray(): ByteArray {
    val out = ByteArray(length.toInt())
    if (out.isNotEmpty()) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return out
}

actual fun createSecureStore(): SecureStore = object : SecureStore {
    override fun put(key: String, value: String) {
        remove(key)
        val q = newQuery(key)
        addRetained(q, kSecValueData, value.encodeToByteArray().toNSData())
        CFDictionaryAddValue(q, kSecAttrAccessible, kSecAttrAccessibleWhenUnlockedThisDeviceOnly)
        val status = SecItemAdd(q, null)
        CFRelease(q)
        check(status == errSecSuccess) { "Keychain add failed ($status)" }
    }

    override fun get(key: String): String? = memScoped {
        val q = newQuery(key)
        CFDictionaryAddValue(q, kSecReturnData, kCFBooleanTrue)
        CFDictionaryAddValue(q, kSecMatchLimit, kSecMatchLimitOne)
        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(q, result.ptr)
        CFRelease(q)
        if (status != errSecSuccess) return@memScoped null
        (CFBridgingRelease(result.value) as? NSData)?.toByteArray()?.decodeToString()
    }

    override fun remove(key: String) {
        val q = newQuery(key)
        SecItemDelete(q)
        CFRelease(q)
    }
}

// ---- Device authentication: Face ID / Touch ID with the device passcode as fallback ----
actual fun createDeviceAuth(): DeviceAuth = object : DeviceAuth {
    override fun available(): Boolean = LAContext().canEvaluatePolicy(LAPolicyDeviceOwnerAuthentication, null)

    override fun authenticate(title: String, onResult: (Boolean) -> Unit) {
        LAContext().evaluatePolicy(LAPolicyDeviceOwnerAuthentication, localizedReason = title) { ok, _ ->
            dispatch_async(dispatch_get_main_queue()) { onResult(ok) }
        }
    }
}

/** The SwiftUI host covers the app-switcher snapshot (see iOSApp.swift), so nothing to do here. */
actual fun setSecureWindow(secure: Boolean) = Unit

/** iOS has the swipe-back gesture and an on-screen back button. */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

// ---- Small native touches ----
actual fun hapticTap() {
    UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleLight).impactOccurred()
}

actual fun shareText(text: String) {
    val root = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return
    root.presentViewController(UIActivityViewController(activityItems = listOf(text), applicationActivities = null), animated = true, completion = null)
}

actual fun nativeLineCount(text: String): Int {
    val bytes = text.encodeToByteArray()
    if (bytes.isEmpty()) return 0
    val count = bytes.usePinned { pinned -> codeberg_line_count(pinned.addressOf(0), bytes.size.convert()) }
    return count.toInt()
}

actual fun nativeTextStats(text: String): TextStats = memScoped {
    val bytes = text.encodeToByteArray()
    if (bytes.isEmpty()) return@memScoped TextStats(0, 0, 0, 0, 0, binary = false)
    val stats = alloc<CbTextStats>()
    bytes.usePinned { pinned -> codeberg_text_stats(pinned.addressOf(0), bytes.size.convert(), stats.ptr) }
    TextStats(
        totalLines = stats.total_lines.toInt(),
        blankLines = stats.blank_lines.toInt(),
        words = stats.words.toInt(),
        chars = stats.chars.toInt(),
        bytes = stats.bytes.toInt(),
        binary = stats.binary != 0.toUByte(),
    )
}

actual fun nativeFormatSize(bytes: Long): String = memScoped {
    val buf = ByteArray(32)
    buf.usePinned { pinned ->
        val written = codeberg_format_size(bytes.convert(), pinned.addressOf(0), buf.size.convert())
        if (written == 0uL) "" else buf.decodeToString(0, written.toInt())
    }
}

actual fun nativeCompactCount(count: Int): String = memScoped {
    val buf = ByteArray(24)
    buf.usePinned { pinned ->
        val written = codeberg_compact_count(count.convert(), pinned.addressOf(0), buf.size.convert())
        if (written == 0uL) "" else buf.decodeToString(0, written.toInt())
    }
}

actual fun nativeRelativeTime(iso8601: String): String = memScoped {
    val bytes = iso8601.encodeToByteArray()
    if (bytes.isEmpty()) return@memScoped ""
    val buf = ByteArray(32)
    val now = platform.posix.time(null).toLong()
    val written = buf.usePinned { outPinned ->
        bytes.usePinned { inPinned ->
            codeberg_relative_time(
                inPinned.addressOf(0),
                bytes.size.convert(),
                now,
                outPinned.addressOf(0),
                buf.size.convert(),
            )
        }
    }
    if (written == 0uL) "" else buf.decodeToString(0, written.toInt())
}

actual fun nativeFileBadge(name: String): String = memScoped {
    val bytes = name.encodeToByteArray()
    if (bytes.isEmpty()) return@memScoped "\u2263"
    val buf = ByteArray(8)
    val written = buf.usePinned { outPinned ->
        bytes.usePinned { inPinned ->
            codeberg_file_badge(inPinned.addressOf(0), bytes.size.convert(), outPinned.addressOf(0), buf.size.convert())
        }
    }
    if (written == 0uL) "\u2263" else buf.decodeToString(0, written.toInt())
}
