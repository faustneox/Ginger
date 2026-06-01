package com.ginger.android.data.repository

import android.content.Context
import timber.log.Timber
import com.ginger.android.util.AuthPrefs
import com.ginger.android.data.local.AppDatabase
import com.ginger.android.data.local.UserEntity
import com.ginger.android.data.session.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.ginger.android.ApiClient
import com.ginger.android.ApiService
import com.ginger.android.LoginResponse
import com.ginger.android.util.retryWithBackoff

/**
 * Репозиторий для работы с пользователями и аутентификацией.
 */
class AuthRepository(private val appContext: Context) {

    private val db = AppDatabase.getInstance(appContext)
    private val userDao = db.userDao()

    // ==================== Чтение ====================

    suspend fun getUserById(userId: Long): Result<UserEntity?> = withContext(Dispatchers.IO) {
        runCatching { userDao.getById(userId) }
            .onFailure { Timber.e(it, "Failed to get user by id=%s", userId) }
    }

    suspend fun getUserByPhone(phone: String): Result<UserEntity?> = withContext(Dispatchers.IO) {
        runCatching { userDao.getByPhone(phone) }
            .onFailure { Timber.e(it, "Failed to get user by phone") }
    }

    suspend fun getUserByGoogleId(googleId: String): Result<UserEntity?> = withContext(Dispatchers.IO) {
        runCatching { userDao.getByGoogleId(googleId) }
            .onFailure { Timber.e(it, "Failed to get user by googleId") }
    }

    suspend fun getAllUsers(): Result<List<UserEntity>> = withContext(Dispatchers.IO) {
        runCatching { userDao.getAllUsers() }
            .onFailure { Timber.e(it, "Failed to get all users") }
    }

    suspend fun countAdmins(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching { userDao.countAdmins() }
            .onFailure { Timber.e(it, "Failed to count admins") }
    }

    // ==================== Запись / Модификация ====================

    suspend fun insertUser(user: UserEntity): Result<Long> = withContext(Dispatchers.IO) {
        runCatching { userDao.insert(user) }
            .onFailure { Timber.e(it, "Failed to insert user") }
    }

    suspend fun updateAdminStatus(userId: Long, isAdmin: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { userDao.updateAdminStatus(userId, isAdmin) }
            .onFailure { Timber.e(it, "Failed to update admin status for userId=%s", userId) }
    }

    suspend fun updateUserPhone(userId: Long, phone: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { userDao.updatePhone(userId, phone) }
            .onFailure { Timber.e(it, "Failed to update phone for userId=%s", userId) }
    }

    // ==================== Вспомогательные проверки ====================

    suspend fun phoneExists(phone: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching { userDao.countByPhone(phone) > 0 }
            .onFailure { Timber.e(it, "Failed to check if phone exists") }
    }

    // ==================== Работа с сессией ====================

    /**
     * Сохранить пользователя как текущего (после успешного логина/регистрации).
     */
    suspend fun saveCurrentUserSession(
        user: UserEntity,
        loginMethod: String = "phone"
    ) = withContext(Dispatchers.IO) {
        val prefs = AuthPrefs.getSecurePrefs(appContext)
        with(prefs.edit()) {
            putLong(AuthPrefs.KEY_USER_ID, user.id)
            putString(AuthPrefs.KEY_LAST_LOGIN_METHOD, loginMethod)

            if (loginMethod == "google") {
                user.googleId?.let { putString(AuthPrefs.KEY_GOOGLE_ID, it) } ?: remove(AuthPrefs.KEY_GOOGLE_ID)
                user.googleEmail?.let { putString(AuthPrefs.KEY_GOOGLE_EMAIL, it) } ?: remove(AuthPrefs.KEY_GOOGLE_EMAIL)
                putString(AuthPrefs.KEY_GOOGLE_NAME, user.fullName)
            } else {
                remove(AuthPrefs.KEY_GOOGLE_ID)
                remove(AuthPrefs.KEY_GOOGLE_EMAIL)
                remove(AuthPrefs.KEY_GOOGLE_NAME)
            }
            apply()
        }

        SessionManager.getInstance(appContext).setCurrentUser(user)
    }

    suspend fun saveRememberMePreference(phone: String, rememberMe: Boolean) = withContext(Dispatchers.IO) {
        val prefs = AuthPrefs.getSecurePrefs(appContext)
        with(prefs.edit()) {
            if (rememberMe) {
                putBoolean(AuthPrefs.KEY_REMEMBER_ME, true)
                putString(AuthPrefs.KEY_SAVED_PHONE, phone)
            } else {
                remove(AuthPrefs.KEY_REMEMBER_ME)
                remove(AuthPrefs.KEY_SAVED_PHONE)
            }
            apply()
        }
    }

    suspend fun getSavedLoginPhone(): String? = withContext(Dispatchers.IO) {
        AuthPrefs.getSecurePrefs(appContext).getString(AuthPrefs.KEY_SAVED_PHONE, null)
    }

    suspend fun isRememberMeEnabled(): Boolean = withContext(Dispatchers.IO) {
        AuthPrefs.getSecurePrefs(appContext).getBoolean(AuthPrefs.KEY_REMEMBER_ME, false)
    }

    /**
     * Пример удалённого входа через Retrofit (заглушка).
     */
    suspend fun loginRemote(phone: String): Result<LoginResponse?> = withContext(Dispatchers.IO) {
        runCatching {
            val service = ApiClient.create(ApiService::class.java)
            val resp = retryWithBackoff { service.login(mapOf("phone" to phone)) }
            if (resp.isSuccessful) resp.body() else throw Exception("Network login failed: ${resp.code()}")
        }.onFailure { Timber.e(it, "Remote login failed") }
    }

    /**
     * Выйти из аккаунта (очистить сессию).
     */
    fun clearSession() {
        SessionManager.getInstance(appContext).logout()
    }
}
