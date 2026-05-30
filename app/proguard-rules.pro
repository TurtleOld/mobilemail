-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**

# Keep Firebase messaging service entry points.
-keep class com.mobilemail.notifications.MobileMailFirebaseMessagingService { *; }

# Keep Room generated implementations and entities metadata.
-keep class * extends androidx.room.RoomDatabase
