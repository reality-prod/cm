// SPDX-License-Identifier: AGPL-3.0-or-later
#include <jni.h>
#include "codeberg_core.h"

/* Classic line count. */
JNIEXPORT jint JNICALL
Java_org_codeberg_mobile_data_NativeRust_countLines(JNIEnv *env, jclass klass, jstring text) {
    (void)klass;
    if (text == NULL) return 0;
    const char *utf = (*env)->GetStringUTFChars(env, text, NULL);
    if (utf == NULL) return 0; /* JVM has raised OutOfMemoryError. */
    jsize length = (*env)->GetStringUTFLength(env, text);
    size_t lines = codeberg_line_count(utf, (size_t)length);
    (*env)->ReleaseStringUTFChars(env, text, utf);
    return lines > 2147483647u ? 2147483647 : (jint)lines;
}

/* Fills stats[0..5] with {total_lines, blank_lines, words, chars, binary}; returns byte count. */
JNIEXPORT jint JNICALL
Java_org_codeberg_mobile_data_NativeRust_textStats(JNIEnv *env, jclass klass, jstring text, jintArray stats) {
    (void)klass;
    if (text == NULL || stats == NULL) return 0;
    jsize stats_len = (*env)->GetArrayLength(env, stats);
    jint *out = (*env)->GetIntArrayElements(env, stats, NULL);
    if (out == NULL) return 0; /* JVM has raised OutOfMemoryError. */
    for (jsize i = 0; i < stats_len; i++) out[i] = 0;

    const char *utf = (*env)->GetStringUTFChars(env, text, NULL);
    jint bytes = 0;
    if (utf != NULL) {
        jsize length = (*env)->GetStringUTFLength(env, text);
        CbTextStats s;
        codeberg_text_stats(utf, (size_t)length, &s);
        if (stats_len > 0) out[0] = (jint)s.total_lines;
        if (stats_len > 1) out[1] = (jint)s.blank_lines;
        if (stats_len > 2) out[2] = (jint)s.words;
        if (stats_len > 3) out[3] = (jint)s.chars;
        if (stats_len > 4) out[4] = (jint)s.binary;
        bytes = (jint)s.bytes;
        (*env)->ReleaseStringUTFChars(env, text, utf);
    }
    (*env)->ReleaseIntArrayElements(env, stats, out, 0);
    return bytes;
}

/* "128 B" / "512 KB" / "1 MB" / "2 GB"; empty string when the buffer is too small. */
JNIEXPORT jstring JNICALL
Java_org_codeberg_mobile_data_NativeRust_formatSize(JNIEnv *env, jclass klass, jlong bytes) {
    (void)klass;
    char buf[32];
    size_t n = codeberg_format_size((uint64_t)bytes, buf, sizeof buf);
    if (n == 0) return (*env)->NewStringUTF(env, "");
    return (*env)->NewStringUTF(env, buf);
}

/* "999" / "1.5k" / "12k" / "3m"; empty string on overflow. */
JNIEXPORT jstring JNICALL
Java_org_codeberg_mobile_data_NativeRust_compactCount(JNIEnv *env, jclass klass, jint count) {
    (void)klass;
    char buf[24];
    size_t n = codeberg_compact_count((uint32_t)count, buf, sizeof buf);
    if (n == 0) return (*env)->NewStringUTF(env, "");
    return (*env)->NewStringUTF(env, buf);
}

/* "just now" / "5m ago" / "3h ago" / "2d ago" / "15 mar"; empty string for malformed input.
 * Note: GetStringUTFChars is Java modified UTF-8, which only differs from real UTF-8 for
 * NUL and astral characters — irrelevant for Forgejo timestamps. */
JNIEXPORT jstring JNICALL
Java_org_codeberg_mobile_data_NativeRust_relativeTime(JNIEnv *env, jclass klass, jstring iso, jlong now_epoch_secs) {
    (void)klass;
    if (iso == NULL) return (*env)->NewStringUTF(env, "");
    const char *utf = (*env)->GetStringUTFChars(env, iso, NULL);
    if (utf == NULL) return (*env)->NewStringUTF(env, "");
    jsize length = (*env)->GetStringUTFLength(env, iso);
    char buf[32];
    size_t n = codeberg_relative_time(utf, (size_t)length, (int64_t)now_epoch_secs, buf, sizeof buf);
    (*env)->ReleaseStringUTFChars(env, iso, utf);
    if (n == 0) return (*env)->NewStringUTF(env, "");
    return (*env)->NewStringUTF(env, buf);
}

/* "KT" for "Main.kt"; empty string for extension-less names. */
JNIEXPORT jstring JNICALL
Java_org_codeberg_mobile_data_NativeRust_fileBadge(JNIEnv *env, jclass klass, jstring name) {
    (void)klass;
    if (name == NULL) return (*env)->NewStringUTF(env, "");
    const char *utf = (*env)->GetStringUTFChars(env, name, NULL);
    if (utf == NULL) return (*env)->NewStringUTF(env, "");
    jsize length = (*env)->GetStringUTFLength(env, name);
    char buf[8];
    size_t n = codeberg_file_badge(utf, (size_t)length, buf, sizeof buf);
    (*env)->ReleaseStringUTFChars(env, name, utf);
    if (n == 0) return (*env)->NewStringUTF(env, "\xe2\x89\xa3"); /* ≡ for extension-less */
    return (*env)->NewStringUTF(env, buf);
}
