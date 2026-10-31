// SPDX-License-Identifier: AGPL-3.0-or-later
#include <jni.h>
#include "codeberg_core.h"

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
