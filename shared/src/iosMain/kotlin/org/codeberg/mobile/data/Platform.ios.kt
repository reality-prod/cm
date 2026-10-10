
// SPDX-License-Identifier: AGPL-3.0-or-later
@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package org.codeberg.mobile.data

import androidx.compose.runtime.Composable
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
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

/*
 * UNVERIFIED: this whole file was written without a Mac. Compile and test on macOS before any release;
 * Kotlin/Native interop for Keychain and UIKit is easy to get subtly wrong.
 */

// ---- Secure storage: Keychain, "when unlocked, this device only" (not backed up, not synced, not readable while locked) ----

private const val SERVICE = "org.codeberg.mobile.secrets"

private fun String.toCFString(): CFStringRef? =
    CFStringCreateWithCString(null, this, kCFStringEncodingUTF8)

private fun ByteArray.toCFData(): CFDataRef? {
    if (isEmpty()) {
        return CFDataCreate(null, null, 0)
    }

    return usePinned { pinned ->
        CFDataCreate(
            null,
            pinned.addressOf(0).reinterpret<UByteVar>(),
            this@toCFData.size.toLong()
        )
    }
}

private fun CFDataRef.toByteArray(): ByteArray {
    val byteCount = CFDataGetLength(this).toInt()
    if (byteCount <= 0) return ByteArray(0)

    val source = CFDataGetBytePtr(this) ?: return ByteArray(0)
    val output = ByteArray(byteCount)

    output.usePinned { pinned ->
        memcpy(
            pinned.addressOf(0),
            source,
            byteCount.toULong()
        )
    }

    return output
}

/**
 * CFDictionaryAddValue retains its values. The values passed here were
 * created with CF*Create, so release our own reference after insertion.
 */
private fun addRetained(
    dict: CFMutableDictionaryRef?,
    key: CFStringRef?,
    value: CFTypeRef?
) {
    if (value == null) return

    if (dict == null || key == null) {
        CFRelease(value)
        return
    }

    CFDictionaryAddValue(dict, key, value)
    CFRelease(value)
}

private fun newQuery(key: String): CFMutableDictionaryRef? {
    val query = CFDictionaryCreateMutable(
        null,
        0,
        kCFTypeDictionaryKeyCallBacks.ptr,
        kCFTypeDictionaryValueCallBacks.ptr
    ) ?: return null

    addRetained(query, kSecClass, null)
    CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)

    val serviceRef = SERVICE.toCFString()
    if (serviceRef == null) {
        CFRelease(query)
        return null
    }
    addRetained(query, kSecAttrService, serviceRef)

    val accountRef = key.toCFString()
    if (accountRef == null) {
        CFRelease(query)
        return null
    }
    addRetained(query, kSecAttrAccount, accountRef)

    return query
}

actual fun createSecureStore(): SecureStore = object : SecureStore {

    override fun put(key: String, value: String) {
        remove(key)

        val query = checkNotNull(newQuery(key)) {
            "Could not create Keychain query"
        }

        val status = try {
            val data = checkNotNull(value.encodeToByteArray().toCFData()) {
                "Could not create Keychain data"
            }

            addRetained(query, kSecValueData, data)

            CFDictionaryAddValue(
                query,
                kSecAttrAccessible,
                kSecAttrAccessibleWhenUnlockedThisDeviceOnly
            )

            SecItemAdd(query, null)
        } finally {
            CFRelease(query)
        }

        check(status == errSecSuccess) {
            "Keychain add failed ($status)"
        }
    }

    override fun get(key: String): String? = memScoped {
        val query = newQuery(key) ?: return@memScoped null

        val result = alloc<CFTypeRefVar>()
        result.value = null

        val status = try {
            CFDictionaryAddValue(query, kSecReturnData, kCFBooleanTrue)
            CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitOne)

            SecItemCopyMatching(query, result.ptr)
        } finally {
            CFRelease(query)
        }

        if (status != errSecSuccess) return@memScoped null

        val resultRef = result.value ?: return@memScoped null

        try {
            val data = resultRef as CFDataRef
            data.toByteArray().decodeToString()
        } finally {
            CFRelease(resultRef)
        }
    }

    override fun remove(key: String) {
        val query = newQuery(key) ?: return

        try {
            SecItemDelete(query)
        } finally {
            CFRelease(query)
        }
    }
}

// ---- Device authentication: Face ID / Touch ID with the device passcode as fallback ----

actual fun createDeviceAuth(): DeviceAuth = object : DeviceAuth {

    override fun available(): Boolean =
        LAContext().canEvaluatePolicy(
            LAPolicyDeviceOwnerAuthentication,
            null
        )

    override fun authenticate(title: String, onResult: (Boolean) -> Unit) {
        LAContext().evaluatePolicy(
            LAPolicyDeviceOwnerAuthentication,
            localizedReason = title
        ) { ok, _ ->
            dispatch_async(dispatch_get_main_queue()) {
                onResult(ok)
            }
        }
    }
}

/** The SwiftUI host covers the app-switcher snapshot (see iOSApp.swift), so nothing to do here. */
actual fun setSecureWindow(secure: Boolean) = Unit

/** iOS has the swipe-back gesture and an on-screen back button. */
@Composable
actual fun PlatformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit
) = Unit

// ---- Small native touches ----

actual fun hapticTap() {
    UIImpactFeedbackGenerator(
        UIImpactFeedbackStyle.UIImpactFeedbackStyleLight
    ).impactOccurred()
}

actual fun shareText(text: String) {
    val root = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return

    root.presentViewController(
        UIActivityViewController(
            activityItems = listOf(text),
            applicationActivities = null
        ),
        animated = true,
        completion = null
    )
}

