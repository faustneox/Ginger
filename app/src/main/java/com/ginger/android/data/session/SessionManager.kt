package com.ginger.android.data.session

import android.content.Context
import com.ginger.android.util.AppCoroutines
import com.ginger.android.util.AuthPrefs
import com.ginger.android.data.local.AppDatabase
import com.ginger.android.data.local.UserEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Централизованный менеджер сессии пользователя.
 * Предоставляет реактивное состояние текущего залогиненного пользователя через StateFlow.
 *
 * Улучшенная версия:
 * - Использует глобальный обработчик ошибок [AppCoroutines.errorHandler]
 * - Корректно отменяет фоновые операции при логауте
 * - Имеет метод [clear] для полного сброса (полезно в тестах)
 */
class SessionManager private constructor(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val userDao = db.userDao()

    // Родительский Job, который позволяет отменять все текущие операции при логауте/очистке
    private val parentJob = SupervisorJob()
    private val scope = CoroutineScope(parentJob + Dispatchers.IO + AppCoroutines.errorHandler)

    // Job для текущей загрузки пользователя (чтобы можно было отменить при новом вызове или логауте)
    private var loadJob: Job? = null

    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    init {
        // При создании менеджера пытаемся загрузить текущего пользователя
        loadCurrentUser()
    }

    /**
     * Загрузить текущего пользователя из SharedPreferences + БД.
     * Если предыдущая загрузка ещё выполняется — она будет отменена.
     */
    fun loadCurrentUser() {
        // Отменяем предыдущую загрузку, если она ещё идёт
        loadJob?.cancel()
        loadJob = scope.launch {
            val userId = getCurrentUserId()
            if (userId > 0) {
                val user = withContext(Dispatchers.IO) {
                    userDao.getById(userId)
                }
                _currentUser.value = user
                _isLoggedIn.value = user != null
            } else {
                _currentUser.value = null
                _isLoggedIn.value = false
            }
        }
    }

    /**
     * Установить текущего пользователя (вызывается после успешного логина/регистрации).
     */
    fun setCurrentUser(user: UserEntity) {
        // Сохраняем ID в защищённые prefs
        AuthPrefs.getSecurePrefs(context)
            .edit()
            .putLong(AuthPrefs.KEY_USER_ID, user.id)
            .apply()

        _currentUser.value = user
        _isLoggedIn.value = true
    }

    /**
     * Выйти из аккаунта.
     * Отменяет все текущие операции загрузки пользователя.
     */
    fun logout() {
        // cancelChildren() отменяет дочерние корутины, но не сам scope.
        // Это важно: после logout можно снова вызвать loadCurrentUser(),
        // потому что scope остаётся живым. cancel() сделал бы scope непригодным навсегда.
        parentJob.cancelChildren()

        AuthPrefs.getSecurePrefs(context)
            .edit()
            .remove(AuthPrefs.KEY_USER_ID)
            .remove(AuthPrefs.KEY_SAVED_PHONE)
            .remove(AuthPrefs.KEY_REMEMBER_ME)
            .remove(AuthPrefs.KEY_GOOGLE_ID)
            .remove(AuthPrefs.KEY_GOOGLE_EMAIL)
            .remove(AuthPrefs.KEY_GOOGLE_NAME)
            .remove(AuthPrefs.KEY_GOOGLE_TOKEN)
            .remove(AuthPrefs.KEY_LAST_LOGIN_METHOD)
            .apply()

        _currentUser.value = null
        _isLoggedIn.value = false

        // Сбрасываем кэш зашифрованных prefs, чтобы следующий вызов
        // getSecurePrefs() получил чистый экземпляр
        AuthPrefs.clearCache()

        // Сбрасываем ссылку на job, чтобы при следующем loadCurrentUser() не было проблем
        loadJob = null
    }

    /**
     * Получить ID текущего пользователя (синхронно, из SharedPreferences).
     */
    fun getCurrentUserId(): Long {
        return AuthPrefs.getSecurePrefs(context)
            .getLong(AuthPrefs.KEY_USER_ID, -1L)
    }

    /**
     * Проверить, авторизован ли пользователь.
     */
    fun hasActiveSession(): Boolean {
        return getCurrentUserId() > 0
    }

    /**
     * Полная очистка менеджера сессии.
     * Отменяет все текущие операции и сбрасывает состояние.
     * Полезно для тестов или полного сброса приложения.
     */
    fun clear() {
        parentJob.cancelChildren()
        _currentUser.value = null
        _isLoggedIn.value = false
        loadJob = null
    }

    companion object {
        @Volatile
        private var INSTANCE: SessionManager? = null

        fun getInstance(context: Context): SessionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SessionManager(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
}
