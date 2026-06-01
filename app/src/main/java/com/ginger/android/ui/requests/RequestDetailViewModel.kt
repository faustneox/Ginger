package com.ginger.android.ui.requests

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ginger.android.R
import com.ginger.android.data.local.RequestEntity
import com.ginger.android.data.repository.AuthRepository
import com.ginger.android.data.session.SessionManager
import com.ginger.android.data.repository.RequestRepository
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
 * ViewModel для экрана детального просмотра/редактирования заявки.
 * Имеет два режима: просмотр (для обычного пользователя) и редактирование (для админа).
 */
@HiltViewModel
class RequestDetailViewModel @Inject constructor(
    application: Application,
    private val requestRepository: RequestRepository,
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager
) : AndroidViewModel(application) {

    private val appContext = getApplication<Application>()

    private val _uiState = MutableStateFlow<RequestDetailUiState>(RequestDetailUiState.Loading)
    val uiState: StateFlow<RequestDetailUiState> = _uiState.asStateFlow()

    private val actionGuard = ActionGuard()
    private var currentRequestId: Long = -1L

    fun loadRequest(requestId: Long) {
        if (requestId <= 0) {
            _uiState.value = RequestDetailUiState.Error(appContext.getString(R.string.error_invalid_request_id))
            return
        }

        currentRequestId = requestId
        _uiState.value = RequestDetailUiState.Loading

        launchSafe {
            val currentUserId = sessionManager.getCurrentUserId()
            val requestResult = requestRepository.getById(requestId)
            val userResult = authRepository.getUserById(currentUserId)

            requestResult.onSuccess { request ->
                userResult.onSuccess { currentUser ->
                    if (request == null || currentUser == null) {
                        _uiState.value = RequestDetailUiState.Error(appContext.getString(R.string.error_request_not_found))
                        return@onSuccess
                    }

                    val isAdmin = currentUser.isAdmin
                    val canView = isAdmin || request.ownerPhone == currentUser.phone

                    if (!canView) {
                        _uiState.value = RequestDetailUiState.AccessDenied
                        return@onSuccess
                    }

                    _uiState.value = if (isAdmin) {
                        RequestDetailUiState.AdminMode(request)
                    } else {
                        RequestDetailUiState.UserMode(request)
                    }
                }.onFailure {
                    _uiState.value = RequestDetailUiState.Error(appContext.getString(R.string.error_load_data))
                }
            }.onFailure {
                _uiState.value = RequestDetailUiState.Error(appContext.getString(R.string.error_load_requests))
            }
        }
    }

    fun saveChanges(
        title: String,
        description: String,
        category: String,
        contact: String,
        status: String
    ) {
        val currentState = _uiState.value
        if (currentState !is RequestDetailUiState.AdminMode) return

        if (title.isBlank() || description.isBlank()) {
            _uiState.value = currentState.copy(error = appContext.getString(R.string.error_fill_all_fields))
            return
        }

        if (!actionGuard.startGlobal()) return
        _uiState.value = RequestDetailUiState.Loading

        launchSafe {
            try {
                val result = requestRepository.updateFields(
                    id = currentRequestId,
                    title = title.trim(),
                    description = description.trim(),
                    category = category.trim().ifEmpty { "Общее" },
                    contact = contact.trim(),
                    status = status
                )

                result.onSuccess { updated ->
                    if (updated) {
                        loadRequest(currentRequestId)
                    } else {
                        _uiState.value = RequestDetailUiState.Error(appContext.getString(R.string.error_save_changes))
                    }
                }.onFailure {
                    _uiState.value = RequestDetailUiState.Error(appContext.getString(R.string.error_save_generic))
                }
            } finally {
                actionGuard.finishGlobal()
            }
        }
    }

    fun deleteRequest() {
        if (!actionGuard.startGlobal()) return
        _uiState.value = RequestDetailUiState.Loading

        launchSafe {
            try {
                val result = requestRepository.deleteById(currentRequestId)

                result.onSuccess {
                    _uiState.value = RequestDetailUiState.Deleted
                }.onFailure {
                    _uiState.value = RequestDetailUiState.Error(appContext.getString(R.string.error_delete_request))
                }
            } finally {
                actionGuard.finishGlobal()
            }
        }
    }

    sealed class RequestDetailUiState {
        object Loading : RequestDetailUiState()
        object AccessDenied : RequestDetailUiState()
        object Deleted : RequestDetailUiState()
        data class UserMode(val request: RequestEntity) : RequestDetailUiState()
        data class AdminMode(
            val request: RequestEntity,
            val error: String? = null
        ) : RequestDetailUiState()

        data class Error(val message: String) : RequestDetailUiState()
    }
}
