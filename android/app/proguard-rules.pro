# Android & Compose
-keepclassmembers class * extends androidx.activity.ComponentActivity {
   public <init>();
}

# Kotlin Serialization
-keepattributes *Annotation*,InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.vibe.core.model.** { *; }
-keep class com.vibe.core.network.model.** { *; }

# Koin
-keep class * extends org.koin.core.module.Module { *; }
-keep class org.koin.** { *; }

# Retrofit & OkHttp
-dontwarn okio.**
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# Media3 & ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Coil
-keep class coil.** { *; }
-dontwarn coil.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler { *; }
-dontwarn kotlinx.coroutines.**

# Spotify App Remote SDK & Protocol types
-keep class com.spotify.android.appremote.** { *; }
-keep interface com.spotify.android.appremote.** { *; }
-keep class com.spotify.protocol.** { *; }
-keep interface com.spotify.protocol.** { *; }
-keepclassmembers class com.spotify.protocol.types.** { *; }
-dontwarn com.spotify.android.appremote.**
-dontwarn com.spotify.protocol.**
-dontwarn com.spotify.base.annotations.**
-dontwarn com.fasterxml.jackson.**

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-dontwarn com.google.gson.**

