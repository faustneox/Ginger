package com.ginger.android.util

import android.content.Context
import android.content.SharedPreferences
import timber.log.Timber
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object AuthPrefs {

    const val PREFS_NAME = "auth_prefs"
    const val KEY_USER_ID = "current_user_id"
    const val KEY_SAVED_PHONE = "saved_phone"
    const val KEY_REMEMBER_ME = "remember_me"

    // Google Sign-In ключи
    const val KEY_GOOGLE_ID = "google_id"
    const val KEY_GOOGLE_EMAIL = "google_email"
    const val KEY_GOOGLE_NAME = "google_name"
    const val KEY_GOOGLE_TOKEN = "google_token"
    const val KEY_LAST_LOGIN_METHOD = "last_login_method" // "phone" или "google"

    @Volatile
    private var cachedPrefs: SharedPreferences? = null

    /**
     * Получить защищённое хранилище предпочтений.
     * Используется кэширование, чтобы не пересоздавать MasterKey и EncryptedSharedPreferences при каждом вызове.
     * Если создание зашифрованных prefs не удаётся, возвращается обычный SharedPreferences как запасной вариант.
     */
    @JvmStatic
    fun getSecurePrefs(context: Context): SharedPreferences {
        return cachedPrefs ?: synchronized(this) {
            cachedPrefs ?: try {
                val masterKey = MasterKey.Builder(context.applicationContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

                EncryptedSharedPreferences.create(
                    context.applicationContext,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                ).also {
                    cachedPrefs = it
                }
            } catch (e: Exception) {
                Timber.w(e, "EncryptedSharedPreferences unavailable, falling back to plain SharedPreferences")
                context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).also {
                    cachedPrefs = it
                }
            }
        }
    }

    /**
     * Очистка кэша (полезно при logout или в тестах).
     */
    @JvmStatic
    fun clearCache() {
        cachedPrefs = null
    }
}
