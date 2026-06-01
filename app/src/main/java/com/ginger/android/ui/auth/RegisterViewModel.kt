package com.ginger.android.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ginger.android.R
import com.ginger.android.util.ActionGuard
import com.ginger.android.util.PasswordUtils
import com.ginger.android.util.PhoneUtils
import com.ginger.android.data.local.UserEntity
import com.ginger.android.data.repository.AuthRepository
import com.ginger.android.util.launchSafe
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class RegisterViewModel @Inject constructor(
    application: Application,
    private val authRepository: AuthRepository
) : AndroidViewModel(application) {

    private val appCtx = getApplication<Application>()

    private val actionGuard = ActionGuard()

    private val _uiState = MutableStateFlow<RegisterUiState>(RegisterUiState.Idle)
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    private val _navigationEvents = MutableSharedFlow<RegisterNavigationEvent>()
    val navigationEvents: SharedFlow<RegisterNavigationEvent> = _navigationEvents.asSharedFlow()

    fun register(fullName: String, phoneRaw: String, password: String) {
        if (fullName.isBlank() || phoneRaw.isBlank() || password.isBlank()) {
            _uiState.value = RegisterUiState.Error(appCtx.getString(R.string.error_fill_registration_fields))
            return
        }

        val normalizedPhone = PhoneUtils.normalize(phoneRaw)

        if (!PhoneUtils.isValid(normalizedPhone)) {
            _uiState.value = RegisterUiState.Error(appCtx.getString(R.string.error_invalid_phone))
            return
        }

        if (password.length < 6) {
            _uiState.value = RegisterUiState.Error(appCtx.getString(R.string.error_password_length))
            return
        }

        if (!actionGuard.startGlobal()) return
        _uiState.value = RegisterUiState.Loading

        launchSafe {
            try {
                val existsResult = authRepository.phoneExists(normalizedPhone)

                existsResult.onSuccess { exists ->
                    if (exists) {
                        _uiState.value = RegisterUiState.Error(appCtx.getString(R.string.error_phone_exists))
                        return@onSuccess
                    }

                    val hashedPassword = PasswordUtils.hashPassword(password)

                    val newUser = UserEntity(
                        fullName = fullName.trim(),
                        phone = normalizedPhone,
                        password = hashedPassword,
                        isAdmin = false
                    )

                    val insertResult = authRepository.insertUser(newUser)

                    insertResult.onSuccess { userId ->
                        val createdUserResult = authRepository.getUserById(userId)
                        createdUserResult.onSuccess { createdUser ->
                            if (createdUser != null) {
                                authRepository.saveCurrentUserSession(createdUser, "phone")
                                _uiState.value = RegisterUiState.Success
                                viewModelScope.launch {
                                    _navigationEvents.emit(RegisterNavigationEvent.NavigateToMain)
                                }
                            } else {
                                _uiState.value = RegisterUiState.Error(appCtx.getString(R.string.error_load_created_user))
                            }
                        }.onFailure {
                            _uiState.value = RegisterUiState.Error(appCtx.getString(R.string.error_load_user_after_creation))
                        }
                    }.onFailure {
                        _uiState.value = RegisterUiState.Error(appCtx.getString(R.string.error_register_complete))
                    }
                }.onFailure {
                    _uiState.value = RegisterUiState.Error(appCtx.getString(R.string.error_register_generic))
                }
            } finally {
                actionGuard.finishGlobal()
            }
        }
    }

    fun resetState() {
        _uiState.value = RegisterUiState.Idle
    }

    sealed class RegisterNavigationEvent {
        object NavigateToMain : RegisterNavigationEvent()
    }

    sealed class RegisterUiState {
        object Idle : RegisterUiState()
        object Loading : RegisterUiState()
        object Success : RegisterUiState()
        data class Error(val message: String) : RegisterUiState()
    }
}
