package org.codeberg.mobile.data

import androidx.compose.runtime.Composable
import java.io.File
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

// 1. Secure storage using a standard file config directory (XDG_CONFIG_HOME)
actual fun createSecureStore(): SecureStore = JvmSecureStore()

private class JvmSecureStore : SecureStore {
    private val configDir = File(System.getProperty("user.home"), ".config/codeberg-mobile").apply { mkdirs() }
    private val storageFile = File(configDir, "secrets.dat")
    
    // TODO(parity): connect to the Secret Service API (libsecret/KWallet) so the key is not static.
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
    // Mobile Linux (postmarketOS, Phosh, Plasma Mobile) has no single unified biometric framework out-of-the-box like Android.
    // Return false to let your App gracefully skip lockscreen prompts.
    override fun available(): Boolean = false
    override fun authenticate(title: String, onResult: (Boolean) -> Unit) = onResult(false)
}

// 3. Prevent screenshots
actual fun setSecureWindow(secure: Boolean) {
    // No-op on Linux mobile Wayland/X11 sessions
}

// 4. Handle the back button (on-screen back button on Linux phones)
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // No-op or bind to a hardware escape key press
}

// 5. Haptic engine
actual fun hapticTap() {
    // No-op today. On postmarketOS devices, this can later tap into `/sys/class/leds/vibrator/`
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

private object NativeRust {
    init { loadLibrary("cm_native") }
    @JvmStatic external fun countLines(text: String): Int
    /** Fills stats with {lines, blankLines, words, chars, binary}; returns the byte count. */
    @JvmStatic external fun textStats(text: String, stats: IntArray): Int
    @JvmStatic external fun formatSize(bytes: Long): String
    @JvmStatic external fun compactCount(count: Int): String
    @JvmStatic external fun relativeTime(iso: String, nowEpochSecs: Long): String
    @JvmStatic external fun fileBadge(name: String): String

    private fun loadLibrary(name: String) {
        // Already on the library path (packaged Linux mobile builds)?
        runCatching { System.loadLibrary(name); return }
        // Otherwise extract a bundled native for this OS/arch from the classpath.
        val resourceArch = when (Record.osArch) {
            "aarch64", "arm64" -> if (Record.onLinux) "linux-arm64" else "darwin-arm64"
            else -> if (Record.onLinux) "linux-x64" else "darwin-x64"
        }
        val suffix = if (Record.onLinux) "so" else "dylib"
        val library = NativeRust::class.java.getResourceAsStream("/natives/$resourceArch/lib$name.$suffix")
            ?: return // No engine for this platform; callers degrade to empty results.
        val path = java.nio.file.Files.createTempFile("cm-native-", ".$suffix")
        library.use { input -> java.nio.file.Files.copy(input, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING) }
        path.toFile().deleteOnExit()
        System.load(path.toAbsolutePath().toString())
    }
}

private object Record {
    val osArch: String get() = System.getProperty("os.arch") ?: ""
    val onLinux: Boolean = System.getProperty("os.name")?.lowercase()?.contains("linux") == true
}

actual fun nativeLineCount(text: String): Int = runCatching { NativeRust.countLines(text) }.getOrDefault(0)

actual fun nativeTextStats(text: String): TextStats {
    val out = IntArray(5)
    val bytes = runCatching { NativeRust.textStats(text, out) }.getOrDefault(0)
    return TextStats(out[0], out[1], out[2], out[3], bytes, out[4] != 0)
}

actual fun nativeFormatSize(bytes: Long): String = runCatching { NativeRust.formatSize(bytes) }.getOrDefault("")

private fun nowEpochSecs(): Long = System.currentTimeMillis() / 1000

actual fun nativeCompactCount(count: Int): String = runCatching { NativeRust.compactCount(count) }.getOrDefault("")

actual fun nativeRelativeTime(iso8601: String): String =
    runCatching { NativeRust.relativeTime(iso8601, nowEpochSecs()) }.getOrDefault("")

actual fun nativeFileBadge(name: String): String = runCatching { NativeRust.fileBadge(name) }.getOrDefault("\u2263")

