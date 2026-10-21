// SPDX-License-Identifier: AGPL-3.0-or-later
package org.codeberg.mobile

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    // Standard Linux mobile layout dimensions (Phosh/Plasma scale-factor match)
    val windowState = rememberWindowState(width = 360.dp, height = 720.dp)
    
    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "Codeberg Mobile"
    ) {
        // Runs your existing common UI seamlessly
        App() 
    }
}

