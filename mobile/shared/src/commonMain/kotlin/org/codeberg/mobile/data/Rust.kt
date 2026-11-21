// SPDX-License-Identifier: AGPL-3.0-or-later
package org.codeberg.mobile.data

/*
 * The shared Rust core (rust/core). Everything here is pure computation delegated to Rust and
 * linked per platform: Kotlin/Native links the static library directly, the JVM targets use a
 * tiny JNI adapter (mobile/shared/src/nativeInterop/jni/jni_bridge.c). Kotlin never duplicates
 * these algorithms; when the native engine is unavailable a function degrades to an empty result
 * rather than re-implementing the logic.
 */

/** Statistics for a text file, computed by the shared Rust core (see rust/core). */
data class TextStats(
    val totalLines: Int,
    val blankLines: Int,
    val words: Int,
    val chars: Int,
    val bytes: Int,
    /** True when the content looks binary (NUL byte in the first 8000 bytes). */
    val binary: Boolean,
)

/** Statistics over [text], computed by the shared Rust core. */
expect fun nativeTextStats(text: String): TextStats

/** Human-readable size ("128 B", "512 KB", "1 MB", "2 GB"), computed by the shared Rust core. */
expect fun nativeFormatSize(bytes: Long): String

/** Display-line count computed by the shared Rust core. */
expect fun nativeLineCount(text: String): Int

/** Compact count ("999", "1.5k", "12k", "3m"), computed by the shared Rust core. */
expect fun nativeCompactCount(count: Int): String

/** Relative time ("just now", "5m ago", "2d ago", "15 mar") for an RFC 3339 timestamp. */
expect fun nativeRelativeTime(iso8601: String): String

/** File-type badge for a file name ("KT" for "Main.kt", "≡" when there is no extension). */
expect fun nativeFileBadge(name: String): String
