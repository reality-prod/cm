// SPDX-License-Identifier: AGPL-3.0-or-later
package org.codeberg.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.codeberg.mobile.data.ForgejoApi
import org.codeberg.mobile.data.listDir
import org.codeberg.mobile.data.rawFile
import org.codeberg.mobile.data.repoDetail
import org.codeberg.mobile.data.safeHttpUrl

// Images are part of the content, and they are also a privacy question: loading one tells its host your IP address.
// So: images from Codeberg load by default (its avatars, banners and README pictures). Images from any other site
// wait behind a tap, unless the person turns on "Load images from other sites" in Settings.

/** Codeberg's own hosts. Images here load without asking. */
private val CODEBERG_HOSTS = setOf("codeberg.org")

/** The app-wide switch for images from other sites. Set from Settings; off by default. */
object ImagePrefs {
    var allowExternal by mutableStateOf(false)
}

/** The host of an absolute URL, without the port or the path. */
fun imageHost(url: String): String =
    url.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#').substringBefore(':')

/** Whether this image may load without asking the person first. */
fun imageLoadsAutomatically(url: String): Boolean =
    imageHost(url) in CODEBERG_HOSTS || ImagePrefs.allowExternal

/**
 * One remote image. Shows a shimmer while it loads, then the picture. Images from other sites show a tap-to-load
 * card instead of loading straight away. Missing or unsafe addresses show nothing, so no broken icons appear.
 */
@Composable
fun RemoteImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val safe = url?.let { safeHttpUrl(it) } ?: return
    var approved by remember(safe) { mutableStateOf(false) }
    if (approved || imageLoadsAutomatically(safe)) {
        // Shimmer shows while the picture loads; the picture covers it once it arrives.
        Box(modifier.shimmer()) {
            AsyncImage(
                model = safe,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        }
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .clickable { approved = true }
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Image from ${imageHost(safe)}", style = MaterialTheme.typography.labelLarge)
                Text(
                    "Tap to load. The site will see your address.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Turns a link from a README or comment into an absolute URL. Relative paths are resolved against [base]. */
fun resolveImageUrl(dest: String, base: String?): String? {
    val target = dest.trim().substringBefore(' ')
    if (target.startsWith("https://") || target.startsWith("http://")) return target
    if (base == null || target.isEmpty() || target.startsWith("data:")) return null
    return base.trimEnd('/') + "/" + target.trimStart('/')
}

/** The raw-file root of a repository's branch on Codeberg. README pictures are relative to this. */
fun rawBase(full: String, branch: String): String = "https://codeberg.org/$full/raw/branch/$branch/"

/**
 * Finds the banner for a repository: the first picture in its README. Returns null when there is no README or no
 * picture, so the header falls back to the brand glow.
 */
suspend fun findBannerUrl(api: ForgejoApi, full: String): String? {
    val detail = api.repoDetail(full)
    val readme = api.listDir(full, "").firstOrNull { it.type == "file" && it.name.startsWith("readme", ignoreCase = true) }
        ?: return null
    val text = api.rawFile(full, readme.path)
    val dest = Regex("""!\[[^\]]*]\(\s*([^)\s]+)""").find(text)?.groupValues?.get(1) ?: return null
    return resolveImageUrl(dest, rawBase(full, detail.defaultBranch.ifBlank { "main" }))
}
