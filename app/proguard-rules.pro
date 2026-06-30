# ── JTS Topology Suite ───────────────────────────────────────────────────────
-keep class org.locationtech.jts.** { *; }
-dontwarn org.locationtech.jts.**

# ── Room ─────────────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public static ** INSTANCE;
    public static ** Companion;
}

# ── GeoKepler — datos y dominio (Room + serialización) ───────────────────────
-keep class com.act.geomapper.data.database.** { *; }
-keep class com.act.geomapper.domain.models.** { *; }

# ── Kotlinx Serialization ────────────────────────────────────────────────────
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.** { kotlinx.serialization.KSerializer serializer(...); }
-keep @kotlinx.serialization.Serializable class * { *; }

# ── OSMDroid ─────────────────────────────────────────────────────────────────
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# ── DataStore Preferences ────────────────────────────────────────────────────
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { <fields>; }

# ── Kotlin Coroutines ─────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# ── Reflection (Compose / ViewModel) ─────────────────────────────────────────
-keepattributes Signature
-keepattributes Exceptions
-keepclassmembers class ** {
    @androidx.compose.runtime.Composable <methods>;
}

# ── SLF4J (logging osmdroid) ──────────────────────────────────────────────────
-dontwarn org.slf4j.**
-keep class org.slf4j.** { *; }
