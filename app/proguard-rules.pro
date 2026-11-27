# WatchTogether ProGuard / R8 Rules for Official Release APK & AAB Builds

# Preserve YouTube IFrame WebView JavaScript Bridge
-keepattributes *Annotation*
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Preserve Firestore data models annotated with @Keep
-keep @androidx.annotation.Keep class * { *; }
-keepclassmembers class com.example.data.** { *; }

# Keep source file and line numbers for clean crash diagnostics
-keepattributes SourceFile,LineNumberTable
