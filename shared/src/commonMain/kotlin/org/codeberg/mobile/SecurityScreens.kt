// SPDX-License-Identifier: AGPL-3.0-or-later
package org.codeberg.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.codeberg.mobile.data.AppState
import org.codeberg.mobile.ui.Chip
import org.codeberg.mobile.ui.TopBar

@Composable
internal fun LockScreen(state: AppState) {
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { state.unlock() }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CodebergLogo()
        Spacer(Modifier.height(20.dp))
        Text("CodeBerg mobile is locked", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("Use your fingerprint, face or device PIN to continue.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        state.lockMessage?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = { state.unlock() }, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("Unlock") }
        TextButton(onClick = { confirm = true }) { Text("Sign out and reset") }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            shape = RoundedCornerShape(28.dp),
            title = { Text("Sign out and turn off app lock?") },
            text = { Text("This removes your session and the lock from this device. Tokens stay valid on Codeberg until you remove them in your Codeberg settings.") },
            confirmButton = { TextButton(onClick = { confirm = false; state.resetFromLock() }) { Text("Sign out") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
internal fun SettingsScreen(state: AppState) {
    var revoke by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        TopBar("Settings", null, state::pop)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Group("Security") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("App lock", style = MaterialTheme.typography.titleMedium)
                        Text("Require fingerprint, face or device PIN to open the app.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = state.lockEnabled, onCheckedChange = { state.setLockEnabled(it) })
                }
                if (state.lockEnabled) {
                    Text("Lock after leaving the app", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0 to "Immediately", 60 to "1 min", 300 to "5 min").forEach { (sec, label) ->
                            Surface(onClick = { state.setLockTimeout(sec) }, color = androidx.compose.ui.graphics.Color.Transparent) { Chip(label, tinted = state.lockTimeoutSec == sec) }
                        }
                    }
                }
                state.lockMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Text(
                    "Passkeys and security keys: Codeberg doesn't let apps use passkey or security-key accounts with a password, " +
                        "and Android passkeys need Google Play Services, which this app avoids. App lock uses the same device " +
                        "authentication locally. Biometric data never reaches the app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Group("Account") {
                val user = state.user
                if (user != null) {
                    Text("Signed in as @$user", style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = { state.signOut() }, shape = RoundedCornerShape(16.dp)) { Text("Sign out") }
                    Text("Signing out removes the token from this device but it stays valid on Codeberg.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { revoke = true }, shape = RoundedCornerShape(16.dp)) { Text("Sign out and revoke tokens") }
                } else {
                    Text("Not signed in", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Group("Privacy") {
                Text(
                    "Talks only to codeberg.org, over HTTPS, trusting only Let's Encrypt's certificate authorities with pinned " +
                        "keys. No analytics, trackers, ads or third-party servers. Images in content are never loaded.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Group("About") {
                Text("CodeBerg mobile 0.2.0", style = MaterialTheme.typography.titleMedium)
                Text("Open source, AGPL-3.0-or-later.", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Unofficial app. Not affiliated with or endorsed by Codeberg e.V. \u201CCodeberg\u201D and the Codeberg logo are " +
                        "trademarks of Codeberg e.V.; the logo is licensed CC0 1.0.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (revoke) RevokeDialog(state) { revoke = false }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() } }
    }
}

@Composable
private fun RevokeDialog(state: AppState, onClose: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!state.revoking) onClose() },
        shape = RoundedCornerShape(28.dp),
        title = { Text("Revoke tokens?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Codeberg needs your password to delete this app's tokens. It isn't stored.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = password, onValueChange = { password = it }, singleLine = true, label = { Text("Password") },
                    shape = RoundedCornerShape(16.dp), visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                )
                OutlinedTextField(
                    value = code, onValueChange = { code = it.filter(Char::isDigit).take(6) }, singleLine = true,
                    label = { Text("2FA code (if enabled)") }, shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                state.revokeError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !state.revoking && password.isNotEmpty(),
                onClick = {
                    val pw = password
                    password = ""
                    state.signOutAndRevoke(pw, code)
                },
            ) { Text(if (state.revoking) "Working\u2026" else "Revoke and sign out") }
        },
        dismissButton = { TextButton(enabled = !state.revoking, onClick = onClose) { Text("Cancel") } },
    )
}
