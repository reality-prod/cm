// SPDX-License-Identifier: AGPL-3.0-or-later
//! Small, allocation-free routines shared by the mobile and desktop clients.
//!
//! Keep the public surface C-compatible so Kotlin/Native can link it directly while
//! the JVM targets use a tiny JNI adapter. Heavy work stays in Rust; the adapters
//! only translate platform strings into UTF-8 bytes.
//!
//! Everything here is pure computation over borrowed bytes: no allocation, no I/O,
//! no panics across the FFI boundary (malformed input produces zeroed/empty output).

use core::{ffi::c_char, slice};

// Secret Service storage: Linux phones only (Android keeps its Keystore store in Kotlin,
// iOS its Keychain store; both would gain nothing from this dependency).
#[cfg(all(target_os = "linux", not(target_os = "android")))]
mod secret;

/// Returns the number of display lines in UTF-8 text (empty text has zero lines).
/// A final newline represents a final empty line, matching Kotlin's split behavior.
#[no_mangle]
pub unsafe extern "C" fn codeberg_line_count(data: *const c_char, len: usize) -> usize {
    if len == 0 || data.is_null() {
        return 0;
    }
    let bytes = slice::from_raw_parts(data.cast::<u8>(), len);
    bytes.iter().filter(|&&byte| byte == b'\n').count() + 1
}

/// Statistics about a file preview. Field order and types must stay in lockstep with
/// `include/codeberg_core.h` and the Kotlin readers of the stats int array.
#[repr(C)]
#[derive(Debug, Default, Clone, Copy, PartialEq, Eq)]
pub struct CbTextStats {
    pub total_lines: u32,
    pub blank_lines: u32,
    /// Words separated by ASCII whitespace (code files never rely on CJK spacing rules).
    pub words: u32,
    /// Number of Unicode code points (what the UI calls "characters").
    pub chars: u32,
    pub bytes: u32,
    /// Non-zero when a NUL byte appears in the first CB_BINARY_SCAN_LEN bytes: the
    /// previewer treats this as a binary file. (The header exposes it as uint8_t.)
    pub binary: u8,
}

/// How many leading bytes are inspected before declaring a file binary.
pub const CB_BINARY_SCAN_LEN: usize = 8000;

/// Fills `out` with statistics over the first `len` UTF-8 bytes in `data`.
/// A null `data`, null `out`, or `len` above u32::MAX writes all-zero stats.
#[no_mangle]
pub unsafe extern "C" fn codeberg_text_stats(
    data: *const c_char,
    len: usize,
    out: *mut CbTextStats,
) {
    let mut stats = CbTextStats::default();
    if !data.is_null() && !out.is_null() && len <= u32::MAX as usize {
        let bytes = slice::from_raw_parts(data.cast::<u8>(), len);
        // A CR alone (old Mac lines) is a line terminator too, so "\r\n" is not double-counted.
        stats.total_lines = if len == 0 {
            0
        } else {
            let newlines = bytes.iter().filter(|&&b| b == b'\n' || b == b'\r').count();
            let crlf = bytes
                .windows(2)
                .filter(|w| w[0] == b'\r' && w[1] == b'\n')
                .count();
            (newlines - crlf + 1).min(u32::MAX as usize) as u32
        };
        stats.blank_lines = if len == 0 {
            0
        } else {
            bytes
                .split(|&b| b == b'\n' || b == b'\r')
                .filter(|line| line.iter().all(|&b| (b as char).is_ascii_whitespace()))
                .count()
                .min(u32::MAX as usize) as u32
        };
        let mut words = 0u64;
        let mut in_word = false;
        for &b in bytes {
            let ws = (b as char).is_ascii_whitespace();
            if ws {
                in_word = false;
            } else if !in_word {
                in_word = true;
                words += 1;
            }
        }
        stats.words = words.min(u32::MAX as u64) as u32;
        stats.chars = bytes
            .iter()
            .filter(|&&b| b & 0xC0 != 0x80) // Continuation bytes never start a code point.
            .count()
            .min(u32::MAX as usize) as u32;
        stats.bytes = len as u32;
        let scan = len.min(CB_BINARY_SCAN_LEN);
        stats.binary = u8::from(bytes[..scan].contains(&0));
    }
    if !out.is_null() {
        unsafe { out.write(stats) };
    }
}

/// Writes a human-readable size into `out` ("128 B", "512 KB", "1 MB", "2 GB"),
/// truncating integer division the same way as the old Kotlin formatter.
/// Returns the number of bytes written (without a NUL), or 0 when `out` is null,
/// `cap` is 0, or the text does not fit (then `out` stays untouched).
#[no_mangle]
pub unsafe extern "C" fn codeberg_format_size(bytes: u64, out: *mut c_char, cap: usize) -> usize {
    if out.is_null() || cap == 0 {
        return 0;
    }
    let text = match bytes {
        0..=1023 => format!("{bytes} B"),
        1024..=1_048_575 => format!("{} KB", bytes / 1024),
        1_048_576..=1_073_741_823 => format!("{} MB", bytes / (1024 * 1024)),
        _ => format!("{} GB", bytes / (1024 * 1024 * 1024)),
    };
    let src = text.as_bytes();
    if src.len() >= cap {
        return 0;
    }
    unsafe {
        core::ptr::copy_nonoverlapping(src.as_ptr(), out.cast::<u8>(), src.len());
        *out.cast::<u8>().add(src.len()) = 0;
    }
    src.len()
}

/// Writes a compact count into `out` ("999", "1.5k", "12k", "3m") matching what the UI showed
/// before. Returns the written length (excluding NUL), or 0 on null/overflow.
#[no_mangle]
pub unsafe extern "C" fn codeberg_compact_count(count: u32, out: *mut c_char, cap: usize) -> usize {
    if out.is_null() || cap == 0 {
        return 0;
    }
    let text = match count {
        0..=999 => format!("{count}"),
        1_000..=10_099 => {
            // Same as what the UI showed before: "1.5k", not "1.50k".
            format!("{}.{}k", count / 1000, (count % 1000) / 100)
        }
        10_100..=999_999 => format!("{}k", count / 1000),
        _ => format!("{}m", (count as u64) / 1_000_000),
    };
    write_nul_terminated(text.as_bytes(), out, cap)
}

/// Writes a relative time into `out` ("just now", "5m ago", "3h ago", "2d ago", "3w ago",
/// or "15 mar" older than that) for the RFC 3339 / ISO 8601 timestamp `iso[0..len]`
/// compared against [now_epoch_secs]. Malformed timestamps return 0 and leave `out` empty.
#[no_mangle]
pub unsafe extern "C" fn codeberg_relative_time(
    iso: *const c_char,
    len: usize,
    now_epoch_secs: i64,
    out: *mut c_char,
    cap: usize,
) -> usize {
    if out.is_null() || cap == 0 || iso.is_null() || len == 0 {
        return 0;
    }
    let bytes = slice::from_raw_parts(iso.cast::<u8>(), len);
    let Some(t) = parse_iso_epoch(bytes) else { return 0 };
    let delta = (now_epoch_secs - t).max(0); // Timestamps in the future read as "just now".
    let text = match delta {
        0..=59 => "just now".to_string(),
        60..=3599 => format!("{}m ago", delta / 60),
        3600..=86399 => format!("{}h ago", delta / 3600),
        86400..=518399 => format!("{}d ago", delta / 86400),
        518400..=2_419_199 => format!("{}w ago", delta / 604800),
        _ => {
            let (y, m, d) = civil_from_days(t / 86400);
            const MONTHS: [&str; 12] = [
                "jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec",
            ];
            if y < 1970 || y > 9999 {
                return 0;
            }
            format!("{} {}", d, MONTHS[(m - 1) as usize])
        }
    };
    write_nul_terminated(text.as_bytes(), out, cap)
}

/// Copies `src` into `out` with a trailing NUL. Returns the byte count written (excluding
/// the NUL), or 0 without touching `out` when `src` does not fit in `cap` bytes.
fn write_nul_terminated(src: &[u8], out: *mut c_char, cap: usize) -> usize {
    if src.len() >= cap {
        return 0;
    }
    unsafe {
        core::ptr::copy_nonoverlapping(src.as_ptr(), out.cast::<u8>(), src.len());
        *out.cast::<u8>().add(src.len()) = 0;
    }
    src.len()
}

/// Writes a short uppercase badge for a file name into `out` ("KT" for "Main.kt", "TS" for
/// "app.ts", "≡" for extension-less names or extensions without ASCII letters, like ".gitignore"
/// or " Makefile"). Returns the written length (excluding NUL), or 0 on null inputs.
#[no_mangle]
pub unsafe extern "C" fn codeberg_file_badge(name: *const c_char, len: usize, out: *mut c_char, cap: usize) -> usize {
    if out.is_null() || cap < 4 || name.is_null() || len == 0 {
        return 0;
    }
    let bytes = slice::from_raw_parts(name.cast::<u8>(), len);
    // "app.min.js" -> "js"; a dot-only name or a leading dot (".gitignore") yields no extension.
    let Some(dot) = bytes[1..].iter().rposition(|&b| b == b'.').map(|i| i + 1) else {
        return 0;
    };
    let mut badge = [b'\0'; 3];
    let mut n = 0;
    for &b in &bytes[dot..] {
        if b.is_ascii_alphanumeric() {
            badge[n] = b.to_ascii_uppercase();
            n += 1;
            if n == 3 {
                break;
            }
        }
    }
    if n == 0 {
        return 0;
    }
    write_nul_terminated(&badge[..n], out, cap)
}

/// Parses the RFC 3339 shape the Forgejo API emits ("YYYY-MM-DDTHH:MM:SSZ" or with a
/// "+HH:MM" offset; "T" may be a space). Fractional seconds and trailing junk are ignored.
/// Non-UTC offsets are applied. Returns epoch seconds, or None when the shape does not match.
fn parse_iso_epoch(b: &[u8]) -> Option<i64> {
    // Shape: Y Y Y Y - M M - D D T/t/space H H : M M : S S ...
    if b.len() < 19 || b[4] != b'-' || b[7] != b'-' || (b[10] != b'T' && b[10] != b't' && b[10] != b' ')
        || b[13] != b':' || b[16] != b':'
    {
        return None;
    }
    let digits = |range: core::ops::Range<usize>| -> Option<i64> {
        b[range].iter().try_fold(0i64, |acc, &d| {
            d.is_ascii_digit().then(|| acc * 10 + i64::from(d - b'0'))
        })
    };
    let (year, month, day) = (digits(0..4)?, digits(5..7)?, digits(8..10)?);
    let (hour, min, sec) = (digits(11..13)?, digits(14..16)?, digits(17..19)?);
    if !(1..=12).contains(&month) || !(1..=31).contains(&day) || hour > 23 || min > 59 || sec > 60 {
        return None;
    }
    let mut epoch = days_from_civil(year, month, day) * 86400 + hour * 3600 + min * 60 + sec;
    // Optional offset: "Z"/"z", or ±HH:MM / ±HHMM after anything (e.g. fractional seconds).
    let rest = &b[19..];
    if let Some(sign_pos) = rest.iter().position(|&c| c == b'+' || c == b'-') {
        let sign = if rest[sign_pos] == b'+' { -1 } else { 1 };
        let o = &rest[sign_pos + 1..];
        let f = |i: usize| -> Option<i64> { o.get(i).and_then(|&d| d.is_ascii_digit().then(|| i64::from(d - b'0'))) };
        let (oh, om) = match (f(0), f(1), o.get(2)) {
            (Some(h1), Some(h2), Some(b':')) => (h1 * 10 + h2, f(3)? * 10 + f(4)?),
            (Some(h1), Some(h2), _) => (h1 * 10 + h2, f(2)? * 10 + f(3)?),
            _ => return None,
        };
        if om > 59 {
            return None;
        }
        epoch += sign * (oh * 3600 + om * 60);
    }
    Some(epoch)
}

/// Howard Hinnant's `days_from_civil`: days since 1970-01-01 for a proleptic Gregorian date.
fn days_from_civil(y: i64, m: i64, d: i64) -> i64 {
    let y = if m <= 2 { y - 1 } else { y };
    let era = if y >= 0 { y } else { y - 399 } / 400;
    let yoe = y - era * 400; // [0, 399]
    let mp = (m + 9) % 12; // March = 0
    let doy = (153 * mp + 2) / 5 + d - 1; // [0, 365]
    let doe = yoe * 365 + yoe / 4 - yoe / 100 + doy; // [0, 146096]
    era * 146_097 + doe - 719_468
}

/// Inverse of [days_from_civil]: (year, month 1-12, day 1-31) from days since the epoch.
fn civil_from_days(z: i64) -> (i64, i64, i64) {
    let z = z + 719_468;
    let era = if z >= 0 { z } else { z - 146_096 } / 146_097;
    let doe = z - era * 146_097; // [0, 146096]
    let yoe = (doe - doe / 1460 + doe / 36524 - doe / 146_096) / 365; // [0, 399]
    let y = yoe + era * 400;
    let doy = doe - (365 * yoe + yoe / 4 - yoe / 100); // [0, 365]
    let mp = (5 * doy + 2) / 153; // March = 0
    let d = doy - (153 * mp + 2) / 5 + 1; // [1, 31]
    let m = if mp < 10 { mp + 3 } else { mp - 9 }; // [1, 12]
    (if m <= 2 { y + 1 } else { y }, m, d)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn stats(text: &str) -> CbTextStats {
        let mut out = CbTextStats::default();
        unsafe {
            codeberg_text_stats(text.as_ptr().cast(), text.len(), &mut out);
        }
        out
    }

    #[test]
    fn line_counts_bytes_as_kotlin_split_would() {
        let mut out = CbTextStats::default();
        unsafe { codeberg_text_stats(core::ptr::null(), 0, &mut out) };
        assert_eq!(out.total_lines, 0);
        unsafe {
            assert_eq!(codeberg_line_count(core::ptr::null(), 0), 0);
            assert_eq!(
                codeberg_line_count(b"src/Main.kt\n".as_ptr().cast(), 12),
                2
            );
        }
        assert_eq!(stats("a\nb\n").total_lines, 3);
    }

    #[test]
    fn crlf_counts_as_one_line() {
        let s = stats("a\r\nb\r\n");
        assert_eq!(s.total_lines, 3);
    }

    #[test]
    fn counts_words_chars_and_blanks() {
        let text = "\n  \nhello world\n\u{00e9}\u{4e16}\n";
        let s = stats(text);
        // Lines: "", "  ", "hello world", "é世", "" -> 5; blanks include the trailing empty line.
        assert_eq!(s.total_lines, 5);
        assert_eq!(s.blank_lines, 3);
        assert_eq!(s.words, 3); // "hello", "world", "é世"
        assert_eq!(s.chars, 19);
        assert_eq!(s.bytes, text.len() as u32);
        assert_eq!(s.binary, 0);
    }

    #[test]
    fn detects_nul_in_scan_window() {
        // NUL after the 8000-byte window does not mark the file binary.
        let mut late = vec![b'a'; CB_BINARY_SCAN_LEN + 10];
        late[CB_BINARY_SCAN_LEN + 5] = 0;
        assert_eq!(stats(core::str::from_utf8(&late).unwrap()).binary, 0);
        let mut early = vec![b'a'; 10];
        early[9] = 0;
        assert_eq!(stats(core::str::from_utf8(&early).unwrap()).binary, 1);
    }

    #[test]
    fn format_size_matches_expected_strings() {
        let mut buf = [0i8; 32];
        let mut check = |bytes: u64| {
            unsafe {
                let n = codeberg_format_size(bytes, buf.as_mut_ptr().cast(), buf.len());
                core::str::from_utf8(
                    core::slice::from_raw_parts(buf.as_ptr().cast::<u8>(), n),
                )
                .unwrap()
                .to_string()
            }
        };
        assert_eq!(check(0), "0 B");
        assert_eq!(check(999), "999 B");
        assert_eq!(check(2048), "2 KB");
        assert_eq!(check(3 * 1024 * 1024), "3 MB");
        assert_eq!(check(5 * 1024 * 1024 * 1024), "5 GB");
        assert_eq!(unsafe { codeberg_format_size(1, core::ptr::null_mut(), 32) }, 0);
        assert_eq!(unsafe { codeberg_format_size(5 * 1024, buf.as_mut_ptr().cast(), 3) }, 0);
    }

    #[test]
    fn compact_counts_match_ui_expectations() {
        let mut buf = [0i8; 32];
        let mut check = |n: u32| {
            unsafe {
                let len = codeberg_compact_count(n, buf.as_mut_ptr().cast(), buf.len());
                core::str::from_utf8(core::slice::from_raw_parts(buf.as_ptr().cast::<u8>(), len))
                    .unwrap()
                    .to_string()
            }
        };
        assert_eq!(check(0), "0");
        assert_eq!(check(999), "999");
        assert_eq!(check(1500), "1.5k");
        assert_eq!(check(12000), "12k");
        assert_eq!(check(3_500_000), "3m");
    }

    fn rel_time(iso: &str, now: i64) -> Option<String> {
        let mut buf = [0i8; 32];
        unsafe {
            let len = codeberg_relative_time(iso.as_ptr().cast(), iso.len(), now, buf.as_mut_ptr().cast(), buf.len());
            if len == 0 {
                None
            } else {
                Some(
                    core::str::from_utf8(core::slice::from_raw_parts(buf.as_ptr().cast::<u8>(), len))
                        .unwrap()
                        .to_string(),
                )
            }
        }
    }

    /// 2024-01-31T14:22:05Z in epoch seconds.
    const NOW: i64 = 1_706_710_925;

    #[test]
    fn relative_time_bands() {
        assert_eq!(rel_time("2024-01-31T14:22:05Z", NOW).as_deref(), Some("just now"));
        assert_eq!(rel_time("2024-01-31T14:20:00Z", NOW).as_deref(), Some("2m ago"));
        assert_eq!(rel_time("2024-01-31T11:00:00Z", NOW).as_deref(), Some("3h ago"));
        assert_eq!(rel_time("2024-01-29T14:22:05Z", NOW).as_deref(), Some("2d ago"));
        assert_eq!(rel_time("2024-01-12T14:22:05Z", NOW).as_deref(), Some("2w ago"));
        assert_eq!(rel_time("2023-12-15T14:22:05Z", NOW).as_deref(), Some("15 dec"));
        // Future timestamps count as "just now", not negative times.
        assert_eq!(rel_time("2030-01-01T00:00:00Z", NOW).as_deref(), Some("just now"));
    }

    #[test]
    fn relative_time_parses_offsets_and_fractions() {
        // +02:00 offset of the same instant reads as 2 hours earlier.
        assert_eq!(rel_time("2024-01-31T14:22:05+00:00", NOW).as_deref(), Some("just now"));
        assert_eq!(rel_time("2024-01-31T16:22:05+02:00", NOW).as_deref(), Some("just now"));
        assert_eq!(rel_time("2024-01-31T09:22:05-05:00", NOW).as_deref(), Some("just now"));
        assert_eq!(rel_time("2024-01-31T14:22:05.123456Z", NOW).as_deref(), Some("just now"));
        assert_eq!(rel_time("2024-01-31 14:22:05", NOW).as_deref(), Some("just now"));
    }

    #[test]
    fn relative_time_rejects_malformed() {
        for bad in ["", "garbage", "2024-13-01T14:22:05Z", "2024-01-31", "2024-01-32T14:22:05Z", "not-a-date at all!"] {
            assert!(rel_time(bad, NOW).is_none(), "should reject: {bad}");
        }
    }

    #[test]
    fn file_badges_for_common_extensions() {
        let mut buf = [0i8; 8];
        let mut badge = |name: &str| {
            unsafe {
                let len = codeberg_file_badge(name.as_ptr().cast(), name.len(), buf.as_mut_ptr().cast(), buf.len());
                if len == 0 { None } else { Some(core::str::from_utf8(core::slice::from_raw_parts(buf.as_ptr().cast::<u8>(), len)).unwrap().to_string()) }
            }
        };
        assert_eq!(badge("Main.kt").as_deref(), Some("KT"));
        assert_eq!(badge("app.min.ts").as_deref(), Some("TS"));
        assert_eq!(badge("lib.rs").as_deref(), Some("RS"));
        assert_eq!(badge("no-extension").as_deref(), None);
        assert_eq!(badge(".gitignore").as_deref(), None);
        assert_eq!(badge("trailing.").as_deref(), None);
    }

    #[test]
    fn civil_round_trip_over_centuries() {
        for days in [0i64, 100, 19_723, 86_400, 1_000_000, 30_000_000, 60_000_000] {
            let (y, m, d) = civil_from_days(days);
            assert_eq!(days_from_civil(y, m, d), days, "round trip failed for {days}");
        }
        assert_eq!(civil_from_days(0), (1970, 1, 1));
        assert_eq!(days_from_civil(2024, 1, 31), NOW / 86400);
    }
}
