package org.codeberg.mobile.data

import androidx.compose.runtime.Composable
import java.io.File
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

// 1. Stub secure storage using a standard file config directory
actual fun createSecureStore(): SecureStore = JvmSecureStore()

private class JvmSecureStore : SecureStore {
    private val configDir = File(System.getProperty("user.home"), ".config/codeberg-mobile").apply { mkdirs() }
    private val storageFile = File(configDir, "secrets.dat")
    
    // For an elegant Linux implementation, you can later connect this to 'Secret Service API' (libsecret/KWallet).
    // For now, we use a static fallback key so your app can run.
    private val secretKey = SecretKeySpec(ByteArray(32) { 0 }, "AES") 

    override fun put(key: String, value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secretKey) }
        val blob = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        // Write or append your key/value dictionary to storageFile here
    }

    override fun get(key: String): String? {
        // Implement simple key retrieval from your file here
        return null
    }

    override fun remove(key: String) {
        // Delete key from file
    }
}

// 2. Lock screen auth (Biometrics)
actual fun createDeviceAuth(): DeviceAuth = object : DeviceAuth {
    // Linux desktops/mobiles don't have a single unified biometric framework out-of-the-box like Android.
    // Return false to let your App gracefully skip lockscreen prompts.
    override fun available(): Boolean = false
    override fun authenticate(title: String, onResult: (Boolean) -> Unit) = onResult(false)
}

// 3. Prevent screenshots
actual fun setSecureWindow(secure: Boolean) {
    // No-op on standard Linux Wayland/X11 desktops
}

// 4. Handle the back button (usually doesn't exist on desktop windows)
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // No-op or bind to a hardware escape key press
}

// 5. Haptic engine
actual fun hapticTap() {
    // No-op on desktops. On postmarketOS devices, this can later tap into `/sys/class/leds/vibrator/`
}

// 6. Native Share Sheet
actual fun shareText(text: String) {
    // Use Linux's standard `xdg-open` or copy straight to system clipboard
    val runtime = Runtime.getRuntime()
    runCatching {
        // Fallback: print to console or write to xclip
        println("Shared Text: \$text")
    }
}

