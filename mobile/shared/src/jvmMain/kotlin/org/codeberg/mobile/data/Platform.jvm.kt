package org.codeberg.mobile.data

import androidx.compose.runtime.Composable
import java.io.File
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

// Secret storage, Linux phones. Primary: Secret Service (KWallet / GNOME Keyring / KeePassXC /
// the Flatpak secret portal) reached through the Rust client compiled into libcm_native — nothing
// to install separately. Fallback for keyring-less systems: AES-GCM file under the XDG config
// directory with 0600 permissions.
actual fun createSecureStore(): SecureStore = SecretServiceStore(FileSecretStore())

private class SecretServiceStore(private val fallback: FileSecretStore) : SecureStore {
    override fun put(key: String, value: String) {
        val saved = runCatching { NativeRust.secretPut(key, value) }.getOrDefault(-2)
        if (saved != 0) fallback.put(key, value)
    }

    override fun get(key: String): String? {
        // Native returns null when the entry is missing or the backend failed; an entry holding
        // an empty string comes back as "" and must not fall through to the file store.
        val fromService = runCatching { NativeRust.secretGet(key) }.getOrNull()
        return fromService ?: fallback.get(key)
    }

    override fun remove(key: String) {
        runCatching { NativeRust.secretDelete(key) }
        // Clear any stale copy that was written to the fallback while the daemon was down.
        fallback.remove(key)
    }
}

/**
 * Last-resort store when no Secret Service daemon is running. The wrapping AES-256-GCM key is
 * random and persists only in a 0600 file in ~/.config/codeberg-mobile; entries are stored
 * individually as 12-byte IV + ciphertext so a bad write cannot corrupt the whole store.
 */
private class FileSecretStore : SecureStore {
    private val dir = File(System.getProperty("user.home"), ".config/codeberg-mobile")
    private val masterKey: ByteArray by lazy { loadOrCreateKey() }

    override fun put(key: String, value: String) {
        dir.mkdirs()
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(masterKey, "AES"), GCMParameterSpec(128, iv))
        val blob = iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        writePrivate(entryFile(key), blob)
    }

    override fun get(key: String): String? {
        val blob = runCatching { entryFile(key).takeIf { it.isFile }?.readBytes() }.getOrNull() ?: return null
        if (blob.size < 12 + 16) return null
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(masterKey, "AES"), GCMParameterSpec(128, blob, 0, 12))
            String(cipher.doFinal(blob, 12, blob.size - 12), Charsets.UTF_8)
        }.getOrNull()
    }

    override fun remove(key: String) {
        entryFile(key).delete()
    }

    private fun entryFile(key: String): File =
        File(dir, Base64.getUrlEncoder().withoutPadding().encodeToString(key.toByteArray(Charsets.UTF_8)) + ".gcm")

    private fun loadOrCreateKey(): ByteArray {
        val keyFile = File(dir, "key.bin")
        keyFile.takeIf { it.length() == 32L }?.readBytes()?.let { return it }
        val fresh = ByteArray(32).also { SecureRandom().nextBytes(it) }
        dir.mkdirs()
        writePrivate(keyFile, fresh)
        return fresh
    }

    private fun writePrivate(file: File, bytes: ByteArray) {
        val path = file.toPath()
        runCatching {
            java.nio.file.Files.deleteIfExists(path)
            java.nio.file.Files.createFile(
                path,
                java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
                    java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"),
                ),
            )
            java.nio.file.Files.write(path, bytes)
        }.recoverCatching {
            // Filesystem without POSIX permission bits (e.g. some sdcard mounts): plain write.
            java.nio.file.Files.write(path, bytes)
        }.getOrThrow()
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

    // Secret Service (Linux only; non-Linux JNI stubs return -2/-null/-2 so the file store takes over).
    /** 0 stored, -2 backend unavailable, -3 bad input. */
    @JvmStatic external fun secretPut(key: String, value: String): Int
    /** Null when the entry is missing or the backend failed; "" is a stored empty string. */
    @JvmStatic external fun secretGet(key: String): String?
    /** 0 deleted or absent, -2 backend unavailable, -3 bad input. */
    @JvmStatic external fun secretDelete(key: String): Int

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

