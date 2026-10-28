-keepattributes Signature
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-dontwarn sun.misc.**

# Keep Firebase messaging service entry points.
-keep class com.mobilemail.notifications.MobileMailFirebaseMessagingService { *; }

# Keep Room generated implementations and entities metadata.
-keep class * extends androidx.room.RoomDatabase

# JMAP DTOs: constructed manually from JSONObject but class/field names must survive R8
# because external code (logging, future serialization) may reference them by name.
-keep class com.mobilemail.data.model.** { *; }

# OkHttp / Okio: suppress warnings from optional TLS provider integrations.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
