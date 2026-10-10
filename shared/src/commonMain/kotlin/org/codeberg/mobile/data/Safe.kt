// SPDX-License-Identifier: AGPL-3.0-or-later
package org.codeberg.mobile.data

/*
 * Everything the server (or a repo's content) tells us is hostile. These checks decide what may be placed in a
 * request that carries our token, and what may be opened outside the app.
 */

private val FULL_NAME = Regex("^[A-Za-z0-9_.-]{1,100}/[A-Za-z0-9_.-]{1,100}$")

private fun invalid() = ApiException(ApiError.Unexpected(0))

/** "owner/repo" -> ["owner","repo"]. Rejects traversal like "../../user/tokens". */
fun requireSafeRepo(full: String): List<String> {
    if (!FULL_NAME.matches(full)) throw invalid()
    val parts = full.split('/')
    if (parts.any { it == "." || it == ".." }) throw invalid()
    return parts
}

/** Splits a repo-relative path into segments. Rejects dot-segments, empty segments, backslashes and NUL. */
fun safePathSegments(path: String): List<String> {
    if (path.isEmpty()) return emptyList()
    if ('\\' in path || '\u0000' in path) throw invalid()
    val segments = path.split('/')
    if (segments.any { it.isEmpty() || it == "." || it == ".." }) throw invalid()
    return segments
}

/**
 * Only plain http(s) links, never ones with embedded credentials ("https://codeberg.org@evil.example" would
 * display a trusted name but go to evil.example). Returns the URL, or null if it must not be opened.
 */
fun safeHttpUrl(raw: String): String? {
    val u = raw.trim()
    if (u.isEmpty() || u.length > 2048) return null
    if (u.any { it.isWhitespace() || it.isISOControl() || it == '\\' }) return null
    val rest = when {
        u.startsWith("https://", ignoreCase = true) -> u.drop(8)
        u.startsWith("http://", ignoreCase = true) -> u.drop(7)
        else -> return null
    }
    val authority = rest.takeWhile { it != '/' && it != '?' && it != '#' }
    if (authority.isEmpty() || '@' in authority || authority.startsWith(":")) return null
    return u
}

fun hostOf(url: String): String = url.substringAfter("://").takeWhile { it != '/' && it != '?' && it != '#' }
