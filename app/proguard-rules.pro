# Compose / Kotlin
-dontwarn org.jetbrains.annotations.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.rednote.lite.data.model.**{ *; }

# Coil
-dontwarn coil3.**

# WebView JS bridge
-keepclassmembers class com.rednote.lite.data.sign.** {
    @android.webkit.JavascriptInterface <methods>;
}
