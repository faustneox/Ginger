package com.ginger.android.ui.auth

import android.app.Application
import timber.log.Timber
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ginger.android.R
import com.ginger.android.util.ActionGuard
import com.ginger.android.util.PasswordUtils
import com.ginger.android.util.PhoneUtils
import com.ginger.android.util.launchSafe
import com.ginger.android.data.local.UserEntity
import com.ginger.android.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


/**
 * LoginViewModel — управление логикой экрана входа.
 *
 * Отвечает за:
 * - Валидация номера телефона и пароля
 * - Аутентификация по телефону (с поиском в БД)
 * - Аутентификация по Google аккаунту
 * - Сохранение сессии и "запомни меня"
 *
 * Использует StateFlow для UI состояния и SharedFlow для навигационных событий.
 * Injected зависимости: Application, AuthRepository.
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    application: Application,
    private val authRepository: AuthRepository
) : AndroidViewModel(application) {

    private companion object {
        private const val TAG = "LoginViewModel"
    }

    private val appContext = getApplication<Application>()

    // UI state: Idle → Loading → Success/Error/PhoneNumberRequired
    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    // Navigation events: отправляем события навигации (NavigateToMain)
    private val _navigationEvents = MutableSharedFlow<LoginNavigationEvent>()
    val navigationEvents: SharedFlow<LoginNavigationEvent> = _navigationEvents.asSharedFlow()

    // Защита от параллельных запросов (двойной клик, быстрые повторы)
    private val actionGuard = ActionGuard()

    /**
     * Логирование по телефону и пароль.
     * @param phoneRaw Номер телефона (может иметь разные форматы)
     * @param password Пароль в открытом виде (хэшируется на клиенте в PasswordUtils)
     * @param rememberMe Сохранить номер телефона для следующего входа
     */
    fun loginWithPhone(phoneRaw: String, password: String, rememberMe: Boolean) {
        if (phoneRaw.isBlank() || password.isBlank()) {
            _uiState.value = LoginUiState.Error(appContext.getString(R.string.error_login_fill))
            return
        }

        val normalizedPhone = PhoneUtils.normalize(phoneRaw)

        if (!PhoneUtils.isValid(normalizedPhone)) {
            _uiState.value = LoginUiState.Error(appContext.getString(R.string.error_invalid_phone))
            return
        }

        if (!actionGuard.startGlobal()) return
        _uiState.value = LoginUiState.Loading

        launchSafe {
            try {
                val result = authRepository.getUserByPhone(normalizedPhone)

                result.onSuccess { user ->
                    if (user == null) {
                        _uiState.value = LoginUiState.Error(appContext.getString(R.string.error_user_not_found))
                        return@onSuccess
                    }

                    val storedHash = user.password
                    if (storedHash.isNullOrEmpty()) {
                        _uiState.value = LoginUiState.Error(appContext.getString(R.string.error_login_failed))
                        return@onSuccess
                    }

                    val passwordMatches = withContext(Dispatchers.Default) {
                        PasswordUtils.verifyPassword(password, storedHash)
                    }

                    if (passwordMatches) {
                        authRepository.saveCurrentUserSession(user, "phone")
                        authRepository.saveRememberMePreference(normalizedPhone, rememberMe)

                        _uiState.value = LoginUiState.Success(user.id)
                        viewModelScope.launch {
                            _navigationEvents.emit(LoginNavigationEvent.NavigateToMain)
                        }
                    } else {
                        _uiState.value = LoginUiState.Error(appContext.getString(R.string.error_login_failed))
                    }
                }.onFailure {
                    _uiState.value = LoginUiState.Error(appContext.getString(R.string.error_login_generic))
                }
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error during phone login")
                _uiState.value = LoginUiState.Error(appContext.getString(R.string.error_login_generic))
            } finally {
                actionGuard.finishGlobal()
            }
        }
    }

    /**
     * Обработка Google Sign-In результата.
     * Проверяет наличие пользователя с этим googleId.
     * Если найден и есть номер телефона — логирует в приложение.
     * Если не найден или нет номера — требует ввод номера телефона.
     * @param account Google аккаунт (из GoogleSignInClient)
     */
    fun handleGoogleSignIn(account: com.google.android.gms.auth.api.signin.GoogleSignInAccount) {
        val googleId = account.id ?: run {
            _uiState.value = LoginUiState.Error(appContext.getString(R.string.error_google_id_failed))
            return
        }

        val email = account.email
        val displayName = account.displayName ?: email ?: "Пользователь Google"

        if (!actionGuard.startGlobal()) return
        _uiState.value = LoginUiState.Loading

        launchSafe {
            try {
                val existingResult = authRepository.getUserByGoogleId(googleId)

                existingResult.onSuccess { existingUser ->
                    if (existingUser != null) {
                        if (existingUser.phone.isNullOrBlank()) {
                            _uiState.value = LoginUiState.PhoneNumberRequired(
                                fullName = existingUser.fullName,
                                googleId = googleId,
                                googleEmail = email ?: "",
                                existingUserId = existingUser.id
                            )
                        } else {
                            authRepository.saveCurrentUserSession(existingUser, "google")
                            _uiState.value = LoginUiState.Success(existingUser.id)
                            viewModelScope.launch {
                                _navigationEvents.emit(LoginNavigationEvent.NavigateToMain)
                            }
                        }
                    } else {
                        _uiState.value = LoginUiState.PhoneNumberRequired(
                            fullName = displayName,
                            googleId = googleId,
                            googleEmail = email ?: "",
                            existingUserId = null
                        )
                    }
                }.onFailure {
                    _uiState.value = LoginUiState.Error(appContext.getString(R.string.error_database_google))
                }
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error during Google sign-in")
                _uiState.value = LoginUiState.Error(appContext.getString(R.string.error_google_sign_in))
            } finally {
                actionGuard.finishGlobal()
            }
        }
    }

    /**
     * Завершение Google Sign-In путём добавления номера телефона.
     * Если пользователь существует (existingUserId != null) — обновляем номер телефона.
     * Если нет — создаём новый пользователь (делая его админом, если это первый).
     * @param phoneRaw Номер телефона (любой формат)
     * @param phoneRequiredState Состояние с данными Google аккаунта
     */
    fun completeGoogleSignInWithPhone(
        phoneRaw: String,
        phoneRequiredState: LoginUiState.PhoneNumberRequired
    ) {
        val normalizedPhone = PhoneUtils.normalize(phoneRaw)

        if (normalizedPhone.isBlank() || !PhoneUtils.isValid(normalizedPhone)) {
            _uiState.value = LoginUiState.Error(appContext.getString(R.string.error_invalid_phone))
            return
        }

        if (!actionGuard.startGlobal()) return
        _uiState.value = LoginUiState.Loading

        launchSafe {
            try {
                val phoneExists = authRepository.phoneExists(normalizedPhone).getOrDefault(false)

                if (phoneExists) {
                    _uiState.value = LoginUiState.Error(appContext.getString(R.string.error_phone_exists))
                    return@launchSafe
                }

                if (phoneRequiredState.existingUserId != null) {
                    val updateResult = authRepository.updateUserPhone(phoneRequiredState.existingUserId, normalizedPhone)

                    updateResult.onSuccess {
                        val userResult = authRepository.getUserById(phoneRequiredState.existingUserId)
                        userResult.onSuccess { user ->
                            if (user != null) {
                                authRepository.saveCurrentUserSession(user, "google")
                                _uiState.value = LoginUiState.Success(user.id)
                                viewModelScope.launch {
                                    _navigationEvents.emit(LoginNavigationEvent.NavigateToMain)
                                }
                            } else {
                                _uiState.value = LoginUiState.Error(appContext.getString(R.string.error_load_user_after_creation))
                            }
                        }.onFailure {
                            _uiState.value = LoginUiState.Error(appContext.getString(R.string.error_load_user_after_creation))
                        }
                    }.onFailure {
                        _uiState.value = LoginUiState.Error(appContext.getString(R.string.error_update_user_phone))
                    }
                } else {
                    val adminCountResult = authRepository.countAdmins()
                    val makeAdmin = adminCountResult.getOrNull() == 0

                    val newUser: UserEntity = UserEntity(
                        fullName = phoneRequiredState.fullName,
                        phone = normalizedPhone,
                        password = null,
                        isAdmin = makeAdmin,
                        googleId = phoneRequiredState.googleId,
                        googleEmail = phoneRequiredState.googleEmail
                    )

                    val insertResult = authRepository.insertUser(newUser)
                    insertResult.onSuccess { newUserId ->
                        val createdUserResult = authRepository.getUserById(newUserId)
                        createdUserResult.onSuccess { createdUser ->
                            if (createdUser != null) {
                                authRepository.saveCurrentUserSession(createdUser, "google")
                                _uiState.value = LoginUiState.Success(createdUser.id)
                                viewModelScope.launch {
                                    _navigationEvents.emit(LoginNavigationEvent.NavigateToMain)
                                }
                            } else {
                                _uiState.value = LoginUiState.Error(appContext.getString(R.string.error_load_created_user))
                            }
                        }.onFailure {
                            _uiState.value = LoginUiState.Error(appContext.getString(R.string.error_load_user_after_creation))
                        }
                    }.onFailure {
                        _uiState.value = LoginUiState.Error(appContext.getString(R.string.error_create_google_user))
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error while completing Google sign-in with phone")
                _uiState.value = LoginUiState.Error(appContext.getString(R.string.error_google_sign_in))
            } finally {
                actionGuard.finishGlobal()
            }
        }
    }

    suspend fun getSavedLoginPhone(): String? = authRepository.getSavedLoginPhone()

    suspend fun isRememberMeEnabled(): Boolean = authRepository.isRememberMeEnabled()

    fun resetState() {
        _uiState.value = LoginUiState.Idle
    }

    /**
     * Sealed class для UI состояния LoginActivity.
     * - Idle: Начальное состояние
     * - Loading: Идёт проверка учётных данных
     * - Success: Успешный вход (содержит userId)
     * - Error: Ошибка (содержит сообщение)
     * - PhoneNumberRequired: Требуется номер телефона (Google Sign-In без номера)
     */
    sealed class LoginUiState {
        object Idle : LoginUiState()
        object Loading : LoginUiState()
        data class Success(val userId: Long) : LoginUiState()
        data class Error(val message: String) : LoginUiState()
        data class PhoneNumberRequired(
            val fullName: String,
            val googleId: String,
            val googleEmail: String,
            val existingUserId: Long?
        ) : LoginUiState()
    }

    sealed class LoginNavigationEvent {
        object NavigateToMain : LoginNavigationEvent()
    }

}
