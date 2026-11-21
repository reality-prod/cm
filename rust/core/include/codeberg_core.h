// SPDX-License-Identifier: AGPL-3.0-or-later
#ifndef CODEBERG_CORE_H
#define CODEBERG_CORE_H
#include <stddef.h>
#include <stdint.h>
#ifdef __cplusplus
extern "C" {
#endif

/* Number of leading bytes inspected before a file is considered binary. */
#define CB_BINARY_SCAN_LEN 8000

typedef struct CbTextStats {
    uint32_t total_lines;
    uint32_t blank_lines;
    uint32_t words;       /* ASCII-whitespace separated words */
    uint32_t chars;       /* Unicode code points */
    uint32_t bytes;
    uint8_t  binary;      /* 1 when a NUL byte appears in the first CB_BINARY_SCAN_LEN bytes */
} CbTextStats;

/* Display-line count; a trailing newline still opens a final (empty) line. */
size_t codeberg_line_count(const char *data, size_t len);

/* Fills *out with statistics over data[0..len]. Null data or len > UINT32_MAX zeroes *out. */
void codeberg_text_stats(const char *data, size_t len, CbTextStats *out);

/* Writes "N B" / "N KB" / "N MB" / "N GB" into out, NUL-terminated.
 * Returns the written length (excluding NUL), or 0 if out is NULL/ cap too small. */
size_t codeberg_format_size(uint64_t bytes, char *out, size_t cap);

/* Compact count ("999", "1.5k", "12k", "3m"), NUL-terminated, same return rules. */
size_t codeberg_compact_count(uint32_t count, char *out, size_t cap);

/* Relative time ("just now", "5m ago", "3h ago", "2d ago", "3w ago", "15 mar") for the
 * RFC 3339 timestamp iso[0..len] against now_epoch_secs. Same return rules. */
size_t codeberg_relative_time(const char *iso, size_t len, int64_t now_epoch_secs, char *out, size_t cap);

/* File-type badge for a file name ("KT" for "Main.kt"); 0 when there is none. */
size_t codeberg_file_badge(const char *name, size_t len, char *out, size_t cap);

/* Secret Service storage (freedesktop.org), Linux only: talks to KWallet / GNOME Keyring /
 * KeePassXC via a statically-linked pure-Rust D-Bus client. Status codes:
 *   0 = ok, -1 = no such entry, -2 = backend failure, -3 = bad input, -5 = buffer too small. */
#if defined(__linux__) && !defined(__ANDROID__)
int codeberg_secret_put(const char *service, size_t service_len, const char *key, size_t key_len, const char *value, size_t value_len);
int codeberg_secret_get(const char *service, size_t service_len, const char *key, size_t key_len, char *out, size_t cap);
int codeberg_secret_delete(const char *service, size_t service_len, const char *key, size_t key_len);
#endif

#ifdef __cplusplus
}
#endif
#endif
