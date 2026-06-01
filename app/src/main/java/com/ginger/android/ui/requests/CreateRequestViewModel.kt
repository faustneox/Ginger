package com.ginger.android.ui.requests

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ginger.android.R
import com.ginger.android.util.PhoneUtils
import com.ginger.android.data.local.RequestEntity
import com.ginger.android.data.repository.AuthRepository
import com.ginger.android.data.repository.RequestRepository
import com.ginger.android.data.session.SessionManager
import com.ginger.android.util.ActionGuard
import com.ginger.android.util.AppCoroutines
import com.ginger.android.util.launchSafe
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel для создания новой заявки (доступно только администраторам).
 */
@HiltViewModel
class CreateRequestViewModel @Inject constructor(
    application: Application,
    private val requestRepository: RequestRepository,
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager
) : AndroidViewModel(application) {

    private val appContext = getApplication<Application>()

    private val _uiState = MutableStateFlow<CreateRequestUiState>(CreateRequestUiState.Idle)
    val uiState: StateFlow<CreateRequestUiState> = _uiState.asStateFlow()

    private val actionGuard = ActionGuard()

    fun validateFields(title: String, description: String, ownerPhoneRaw: String): ValidationError? {
        if (title.isBlank()) return ValidationError.TitleEmpty
        if (description.trim().length < 5) return ValidationError.DescriptionTooShort
        if (ownerPhoneRaw.isBlank()) return ValidationError.ContactMissing
        val normalizedPhone = PhoneUtils.normalize(ownerPhoneRaw)
        if (!PhoneUtils.isValid(normalizedPhone)) return ValidationError.ContactInvalid
        return null
    }

    fun submitRequest(
        title: String,
        description: String,
        category: String,
        ownerPhoneRaw: String
    ) {
        val validation = validateFields(title, description, ownerPhoneRaw)
        if (validation != null) {
            _uiState.value = CreateRequestUiState.Error(appContext.getString(validation.toMessageRes()))
            return
        }
        val currentUser = sessionManager.currentUser.value
        if (currentUser == null || !currentUser.isAdmin) {
            _uiState.value = CreateRequestUiState.Error(appContext.getString(R.string.message_admin_only_create))
            return
        }

        if (title.isBlank() || description.isBlank()) {
            _uiState.value = CreateRequestUiState.Error("Заполните тему и описание")
            return
        }

        val normalizedPhone = PhoneUtils.normalize(ownerPhoneRaw)

        if (!PhoneUtils.isValid(normalizedPhone)) {
            _uiState.value = CreateRequestUiState.Error(appContext.getString(R.string.error_owner_phone_invalid))
            return
        }

        if (!actionGuard.startGlobal()) return
        _uiState.value = CreateRequestUiState.Loading

        launchSafe {
            try {
                val userResult = authRepository.getUserByPhone(normalizedPhone)

                userResult.onSuccess { user ->
                    if (user == null) {
                        _uiState.value = CreateRequestUiState.Error(appContext.getString(R.string.error_subscriber_not_found))
                        return@onSuccess
                    }

                    if (user.isAdmin) {
                        _uiState.value = CreateRequestUiState.Error(appContext.getString(R.string.error_subscriber_cannot_be_admin))
                        return@onSuccess
                    }

                    val request = RequestEntity(
                        title = title.trim(),
                        description = description.trim(),
                        category = category.trim().ifEmpty { "Общее" },
                        contact = user.phone ?: normalizedPhone,
                        status = RequestEntity.STATUS_NEW,
                        ownerUserId = user.id,
                        ownerPhone = normalizedPhone,
                        createdAt = System.currentTimeMillis()
                    )

                    val insertResult = requestRepository.insert(request)

                    insertResult.onSuccess {
                        _uiState.value = CreateRequestUiState.Success
                    }.onFailure {
                        _uiState.value = CreateRequestUiState.Error(appContext.getString(R.string.error_create_request_failed))
                    }
                }.onFailure {
                    _uiState.value = CreateRequestUiState.Error(appContext.getString(R.string.error_search_owner))
                }
            } finally {
                actionGuard.finishGlobal()
            }
        }
    }

    fun resetState() {
        _uiState.value = CreateRequestUiState.Idle
    }

    sealed class CreateRequestUiState {
        object Idle : CreateRequestUiState()
        object Loading : CreateRequestUiState()
        object Success : CreateRequestUiState()
        data class Error(val message: String) : CreateRequestUiState()
    }
}
