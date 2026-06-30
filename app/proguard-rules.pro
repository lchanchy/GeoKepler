# JTS
-keep class org.locationtech.jts.** { *; }
-dontwarn org.locationtech.jts.**

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *

# Mapbox
-keep class com.mapbox.** { *; }
-dontwarn com.mapbox.**

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
