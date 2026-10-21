
# SPDX-License-Identifier: AGPL-3.0-or-later

-dontobfuscate

# Keep useful metadata for Kotlin, annotations, and reflection.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,MethodParameters
-keepattributes SourceFile,LineNumberTable

# Remove Android logging calls from release builds.
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}

# Keep native method names for JNI lookups.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Keep enum methods that may be accessed reflectively.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
