# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# ──────────────────────────────────────────────
# Debugging: сохраняем номера строк в стектрейсах
# ──────────────────────────────────────────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ──────────────────────────────────────────────
# Room — сущности и DAO-интерфейсы
# ──────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase {
    abstract *;
}

# ──────────────────────────────────────────────
# Kotlin Coroutines
# ──────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# ──────────────────────────────────────────────
# EncryptedSharedPreferences / Security Crypto
# ──────────────────────────────────────────────
-keep class androidx.security.crypto.** { *; }

# ──────────────────────────────────────────────
# Firebase & Google Play Services
# ──────────────────────────────────────────────
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# ──────────────────────────────────────────────
# Ginger — data-классы (Room entity / state)
# ──────────────────────────────────────────────
-keep class com.ginger.android.data.local.** { *; }