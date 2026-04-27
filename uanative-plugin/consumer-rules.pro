-keep class com.ua.toolkit.AdJsBridge { *; }
-keepclassmembers class com.ua.toolkit.AdJsBridge {
    @android.webkit.JavascriptInterface <methods>;
}
