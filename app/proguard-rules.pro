# Disone ProGuard rules
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes *Annotation*
-keepattributes SourceFile, LineNumberTable

# Retrofit
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,interface retrofit2.Call { *; }
-dontwarn retrofit2.**

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# jlibtorrent
-keep class com.frostwire.jlibtorrent.** { *; }

# libVLC
-keep class org.videolan.** { *; }

# Solana
-keep class com.solanamobile.** { *; }

# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontwarn kotlinx.serialization.**
