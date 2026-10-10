// SPDX-License-Identifier: AGPL-3.0-or-later
package org.codeberg.mobile.data

import android.content.Context
import android.content.Intent
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Set by MainActivity. [activity] is cleared in onDestroy so a destroyed activity is never leaked. */
object AppContext {
    lateinit var app: Context
    var activity: FragmentActivity? = null
}

// ---- Secure storage: non-exportable Keystore AES-GCM key ----
actual fun createSecureStore(): SecureStore = KeystoreStore(AppContext.app)

private class KeystoreStore(context: Context) : SecureStore {
    private val prefs = context.applicationContext.getSharedPreferences("secrets", Context.MODE_PRIVATE)

    override fun put(key: String, value: String) {
        val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val blob = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(key, Base64.encodeToString(blob, Base64.NO_WRAP)).apply()
    }

    override fun get(key: String): String? {
        val blob = Base64.decode(prefs.getString(key, null) ?: return null, Base64.NO_WRAP)
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, blob, 0, IV_LEN))
            String(cipher.doFinal(blob, IV_LEN, blob.size - IV_LEN), Charsets.UTF_8)
        }.getOrNull()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private companion object {
        const val ALIAS = "codeberg_mobile_token_key"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val IV_LEN = 12
    }
}

// ---- Device authentication ----
// BIOMETRIC_STRONG | DEVICE_CREDENTIAL is unsupported on Android 9-10 with androidx.biometric 1.1.0; WEAK works on all.
private const val ALLOWED = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

actual fun createDeviceAuth(): DeviceAuth = object : DeviceAuth {
    override fun available(): Boolean {
        val a = AppContext.activity ?: return false
        return BiometricManager.from(a).canAuthenticate(ALLOWED) == BiometricManager.BIOMETRIC_SUCCESS
    }

    override fun authenticate(title: String, onResult: (Boolean) -> Unit) {
        val a = AppContext.activity
        if (a == null) {
            onResult(false)
            return
        }
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true)
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(false)
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setAllowedAuthenticators(ALLOWED) // No negative button is allowed together with DEVICE_CREDENTIAL.
            .build()
        BiometricPrompt(a, ContextCompat.getMainExecutor(a), callback).authenticate(info)
    }
}

actual fun setSecureWindow(secure: Boolean) {
    val window = AppContext.activity?.window ?: return
    if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled, onBack)

// ---- Small native touches ----
actual fun hapticTap() {
    AppContext.activity?.window?.decorView?.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
}

actual fun shareText(text: String) {
    val a = AppContext.activity ?: return
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    a.startActivity(Intent.createChooser(send, null))
}
