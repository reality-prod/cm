// SPDX-License-Identifier: AGPL-3.0-or-later
//! Small, allocation-free routines shared by the mobile and desktop clients.
//!
//! Keep the public surface C-compatible so Kotlin/Native can link it directly while
//! the JVM targets use a tiny JNI adapter. Heavy work stays in Rust; the adapters
//! only translate platform strings into UTF-8 bytes.

use core::{ffi::c_char, slice};

/// Returns the number of display lines in UTF-8 text (empty text has zero lines).
/// A final newline represents a final empty line, matching Kotlin's split behavior.
#[no_mangle]
pub unsafe extern "C" fn codeberg_line_count(data: *const c_char, len: usize) -> usize {
    if len == 0 {
        return 0;
    }
    if data.is_null() {
        return 0;
    }
    let bytes = slice::from_raw_parts(data.cast::<u8>(), len);
    bytes.iter().filter(|&&byte| byte == b'\n').count() + 1
}
