// SPDX-License-Identifier: AGPL-3.0-or-later
package org.codeberg.mobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import org.codeberg.mobile.data.AppContext
import org.codeberg.mobile.data.appDidBackground
import org.codeberg.mobile.data.appDidForeground
import org.codeberg.mobile.data.handleDeepLink

// FragmentActivity (a ComponentActivity subclass) is required by BiometricPrompt. Everything else is in :shared.
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AppContext.app = applicationContext
        AppContext.activity = this
        intent?.dataString?.let { handleDeepLink(it) }
        setContent { App() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.dataString?.let { handleDeepLink(it) }
    }

    override fun onStart() {
        super.onStart()
        appDidForeground()
    }

    override fun onStop() {
        // Rotation also stops/starts the activity; that is not "leaving the app".
        if (!isChangingConfigurations) appDidBackground()
        super.onStop()
    }

    override fun onDestroy() {
        if (AppContext.activity === this) AppContext.activity = null
        super.onDestroy()
    }
}
