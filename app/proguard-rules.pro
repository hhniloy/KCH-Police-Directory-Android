# Add project specific ProGuard rules here.
# This helps reduce APK size by removing unused code.

# Keep WebView JavaScript interface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep WebView related classes
-keep class android.webkit.** { *; }
-dontwarn android.webkit.**
