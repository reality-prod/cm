// SPDX-License-Identifier: AGPL-3.0-or-later
//! Secret storage on Linux phones through the freedesktop Secret Service API, reached with
//! the pure-Rust `keyring` client (D-Bus via zbus): it statically links into the JNI library,
//! so devices need no libsecret — only a running Secret Service provider (KWallet, GNOME
//! Keyring, KeePassXC; Flatpak's secret portal fills the role inside sandboxes).
//!
//! Status codes shared with the JNI adapter and Kotlin:
//!   0 = ok, -1 = no such entry, -2 = backend failure, -3 = bad input, -5 = output buffer too small.
//! Compiled only for Linux (not Android: Android keeps its Keystore implementation in Kotlin).

use core::ffi::{c_char, c_int};
use keyring::Error as KeyringError;

fn status(err: &KeyringError) -> c_int {
    match err {
        KeyringError::NoEntry => -1,
        _ => -2,
    }
}

/// # Safety
/// `p` must point to `len` readable bytes, or be null.
unsafe fn bytes<'a>(p: *const c_char, len: usize) -> Option<&'a [u8]> {
    if p.is_null() {
        None
    } else {
        Some(core::slice::from_raw_parts(p.cast::<u8>(), len))
    }
}

fn field(raw: Option<&[u8]>) -> Result<&str, c_int> {
    core::str::from_utf8(raw.ok_or(-3)?).map_err(|_| -3)
}

fn copy_out(value: &str, out: *mut c_char, cap: usize) -> c_int {
    let src = value.as_bytes();
    if src.len() >= cap {
        return -5;
    }
    unsafe {
        core::ptr::copy_nonoverlapping(src.as_ptr(), out.cast::<u8>(), src.len());
        *out.cast::<u8>().add(src.len()) = 0;
    }
    src.len() as c_int
}

/// Stores `value` under `key` for `service` in the user's default secret collection.
#[no_mangle]
pub unsafe extern "C" fn codeberg_secret_put(
    service: *const c_char,
    service_len: usize,
    key: *const c_char,
    key_len: usize,
    value: *const c_char,
    value_len: usize,
) -> c_int {
    let (service, key, value) = match (field(bytes(service, service_len)), field(bytes(key, key_len)), field(bytes(value, value_len))) {
        (Ok(s), Ok(k), Ok(v)) => (s, k, v),
        _ => return -3,
    };
    match keyring::Entry::new(service, key) {
        Ok(entry) => match entry.set_password(value) {
            Ok(()) => 0,
            Err(e) => status(&e),
        },
        Err(e) => status(&e),
    }
}

/// Reads the secret stored under `key` for `service` into `out`.
/// Returns the value's byte length, or a negative status code.
#[no_mangle]
pub unsafe extern "C" fn codeberg_secret_get(
    service: *const c_char,
    service_len: usize,
    key: *const c_char,
    key_len: usize,
    out: *mut c_char,
    cap: usize,
) -> c_int {
    let out_ok = !out.is_null() && cap > 0;
    let (service, key) = match (field(bytes(service, service_len)), field(bytes(key, key_len))) {
        (Ok(s), Ok(k)) => (s, k),
        _ => return -3,
    };
    if !out_ok {
        return -3;
    }
    match keyring::Entry::new(service, key) {
        Ok(entry) => match entry.get_password() {
            Ok(pw) => copy_out(&pw, out, cap),
            Err(e) => status(&e),
        },
        Err(e) => status(&e),
    }
}

/// Deletes the secret stored under `key` for `service`. Deleting a missing secret is fine: 0.
#[no_mangle]
pub unsafe extern "C" fn codeberg_secret_delete(
    service: *const c_char,
    service_len: usize,
    key: *const c_char,
    key_len: usize,
) -> c_int {
    let (service, key) = match (field(bytes(service, service_len)), field(bytes(key, key_len))) {
        (Ok(s), Ok(k)) => (s, k),
        _ => return -3,
    };
    match keyring::Entry::new(service, key) {
        Ok(entry) => match entry.delete_credential() {
            Ok(()) => 0,
            Err(e) => status(&e),
        },
        Err(e) => status(&e),
    }
}

// Integration test: needs a live Secret Service provider, so it is #[ignore]d by default;
// run with `cargo test -- --ignored` on a machine with a keyring daemon.
#[cfg(all(test, target_os = "linux"))]
mod tests {
    use super::*;

    #[test]
    #[ignore]
    fn put_get_delete_round_trip() {
        let service = b"org.codeberg.mobile.test\0";
        let key = b"round-trip\0";
        let value = b"token-123\0";
        unsafe {
            assert_eq!(
                codeberg_secret_put(
                    service.as_ptr().cast(), service.len() - 1,
                    key.as_ptr().cast(), key.len() - 1,
                    value.as_ptr().cast(), value.len() - 1,
                ),
                0
            );
            let mut buf = [0i8; 64];
            let n = codeberg_secret_get(
                service.as_ptr().cast(), service.len() - 1,
                key.as_ptr().cast(), key.len() - 1, buf.as_mut_ptr().cast(), buf.len(),
            );
            assert_eq!(n, (value.len() - 1) as i32);
            assert_eq!(
                core::str::from_utf8(core::slice::from_raw_parts(buf.as_ptr().cast::<u8>(), n as usize)).unwrap(),
                "token-123"
            );
            assert_eq!(
                codeberg_secret_delete(service.as_ptr().cast(), service.len() - 1, key.as_ptr().cast(), key.len() - 1),
                0
            );
            assert_eq!(
                codeberg_secret_get(
                    service.as_ptr().cast(), service.len() - 1,
                    key.as_ptr().cast(), key.len() - 1, buf.as_mut_ptr().cast(), buf.len(),
                ),
                -1
            );
        }
    }

    #[test]
    fn rejects_null_and_invalid_utf8() {
        unsafe {
            assert_eq!(codeberg_secret_put(core::ptr::null(), 0, core::ptr::null(), 0, core::ptr::null(), 0), -3);
            // Invalid UTF-8 in the key.
            assert_eq!(
                codeberg_secret_put(b"s\0".as_ptr().cast(), 1, b"\xff\0".as_ptr().cast(), 1, b"v\0".as_ptr().cast(), 1),
                -3
            );
        }
    }
}
