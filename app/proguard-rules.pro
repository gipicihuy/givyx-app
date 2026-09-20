# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Jsoup
-keeppackagenames org.jsoup.nodes

# Coil
-keep class coil.** { *; }
-keepnames class coil.** { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.** { *; }

# Compose
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }
