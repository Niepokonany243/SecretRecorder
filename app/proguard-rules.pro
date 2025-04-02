# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# GPLv3 License Warning Header
# This application is provided for educational and research purposes only.
# The app demonstrates potential security vulnerabilities in Android
# and should be used ONLY for legitimate security testing and research.
#
# Any malicious use is strictly prohibited and the developers
# take no responsibility for misuse. This app is licensed under GPLv3.

# Keep class names for serialization/deserialization
-keepattributes Signature

# Keep basic Android components
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver

# Keep native methods
-keepclasseswithmembers class * {
    native <methods>;
}

# Keep the entry points of the application
-keep public class com.example.systemservice.MainActivity

# Obfuscate all other classes
-keep class androidx.** { *; }
-keep class android.** { *; }
-keep class com.google.** { *; }

# Aggressive obfuscation of our package
-keep class com.example.systemservice.** { *; }
-keepclassmembers class com.example.systemservice.** { *; }
-keeppackagenames com.example.systemservice
-repackageclasses com.example.systemservice

# Handle encryption classes carefully
-keep class javax.crypto.** { *; }
-keep class java.security.** { *; }

# Remove logging in release builds
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** i(...);
    public static *** d(...);
    public static *** w(...);
    public static *** e(...);
}

# Keep MediaProjection classes
-keep class android.media.projection.MediaProjection { *; }
-keep class android.media.projection.MediaProjectionManager { *; }

# Keep WorkManager classes
-keep class androidx.work.** { *; }

# Keep Coroutines
-keep class kotlinx.coroutines.** { *; }

# Keep encryption utilities
-keep class com.example.systemservice.FileEncryptionUtil { *; }

# Special handling for FFmpeg
-keep class com.arthenica.ffmpegkit.** { *; }
-keep class com.arthenica.smartexception.** { *; }

# Optimizations - more aggressive for better performance
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*,!class/unboxing/enum
-optimizationpasses 8
-allowaccessmodification
-repackageclasses ''

# Enable more aggressive optimizations
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose

# Remove unused code
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int d(...);
    public static int w(...);
    public static int e(...);
}

# Performance optimizations
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature
-keepattributes Exceptions

# Reduce APK size
-dontwarn android.support.**
-dontwarn androidx.**
-dontwarn com.google.android.material.**
-dontwarn org.jetbrains.annotations.**
-dontwarn kotlin.**
-dontwarn kotlinx.**

# Optimize RecyclerView for better scrolling performance
-keepclassmembers class androidx.recyclerview.widget.RecyclerView {
    public void suppressLayout(boolean);
    public boolean isLayoutSuppressed();
}

# Optimize Bitmap handling
-keepclassmembers class android.graphics.Bitmap {
    public void recycle();
    public boolean isRecycled();
}

# Optimize Camera2 API
-keepclassmembers class android.hardware.camera2.** { *; }
-keepclassmembers class android.hardware.camera2.params.** { *; }

# Keep missing classes that cause R8 errors
-dontwarn javax.lang.model.element.Modifier
-dontwarn com.google.errorprone.annotations.**
-keep class javax.lang.model.element.** { *; }
-keep class com.google.errorprone.annotations.** { *; }