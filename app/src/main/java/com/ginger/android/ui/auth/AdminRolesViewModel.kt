package com.ginger.android.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ginger.android.R
import com.ginger.android.data.local.UserEntity
import com.ginger.android.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.ginger.android.data.session.SessionManager
import com.ginger.android.util.ActionGuard
import com.ginger.android.util.AppCoroutines
import com.ginger.android.util.launchSafe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel для экрана управления ролями администраторов.
 */
@HiltViewModel
class AdminRolesViewModel @Inject constructor(
    application: Application,
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager
) : AndroidViewModel(application) {

    private val appContext = getApplication<Application>()

    private val _uiState = MutableStateFlow<AdminRolesUiState>(AdminRolesUiState.Loading)
    val uiState: StateFlow<AdminRolesUiState> = _uiState.asStateFlow()

    private var currentUserId: Long = -1L
    private var adminCount: Int = 0

    private val actionGuard = ActionGuard()

    fun loadUsers() {
        val userId = sessionManager.getCurrentUserId()
        if (userId <= 0) {
            _uiState.value = AdminRolesUiState.AccessDenied
            return
        }

        currentUserId = userId
        _uiState.value = AdminRolesUiState.Loading

        launchSafe {
            val currentUserResult = authRepository.getUserById(userId)
            val allUsersResult = authRepository.getAllUsers()
            val adminCountResult = authRepository.countAdmins()

            currentUserResult.onSuccess { currentUser ->
                allUsersResult.onSuccess { users ->
                    adminCountResult.onSuccess { count ->
                        if (currentUser == null || !currentUser.isAdmin) {
                            _uiState.value = AdminRolesUiState.AccessDenied
                            return@onSuccess
                        }

                        adminCount = count
                        _uiState.value = AdminRolesUiState.Success(
                            users = users,
                            currentUserId = userId,
                            adminCount = count,
                            processingUserId = actionGuard.getCurrentProcessingId() ?: -1L,
                            error = null
                        )
                    }.onFailure {
                        _uiState.value = AdminRolesUiState.Error(appContext.getString(R.string.error_load_data))
                    }
                }.onFailure {
                    _uiState.value = AdminRolesUiState.Error(appContext.getString(R.string.error_load_users))
                }
            }.onFailure {
                _uiState.value = AdminRolesUiState.Error(appContext.getString(R.string.error_load_profile))
            }
        }
    }

    fun toggleAdminRole(targetUserId: Long) {
        val currentState = _uiState.value
        if (currentState !is AdminRolesUiState.Success) return

        // Используем ActionGuard для защиты от race condition и двойных нажатий
        if (!actionGuard.start(targetUserId)) return

        val targetUser = currentState.users.find { it.id == targetUserId }
        if (targetUser == null) {
            actionGuard.finish(targetUserId)
            _uiState.value = currentState.copy(error = "Пользователь не найден")
            return
        }

        val newIsAdmin = !targetUser.isAdmin

        if (!newIsAdmin && targetUser.isAdmin && currentState.adminCount <= 1) {
            actionGuard.finish(targetUserId)
            _uiState.value = currentState.copy(error = "В системе должен оставаться хотя бы один администратор")
            return
        }

        if (!newIsAdmin && targetUser.id == currentUserId && currentState.adminCount <= 1) {
            actionGuard.finish(targetUserId)
            _uiState.value = currentState.copy(error = "Вы не можете снять с себя права последнего администратора")
            return
        }

        launchSafe {
            val result = authRepository.updateAdminStatus(targetUserId, newIsAdmin)

            result.onSuccess {
                actionGuard.finish(targetUserId)
                loadUsers()
            }.onFailure {
                actionGuard.finish(targetUserId)
                // Всегда отражаем актуальное состояние гварда
                _uiState.value = currentState.copy(
                    error = "Не удалось изменить роль",
                    processingUserId = actionGuard.getCurrentProcessingId() ?: -1L
                )
            }
        }
    }

    fun clearError() {
        val currentState = _uiState.value
        if (currentState is AdminRolesUiState.Success) {
            _uiState.value = currentState.copy(error = null)
        }
    }

    sealed class AdminRolesUiState {
        object Loading : AdminRolesUiState()
        object AccessDenied : AdminRolesUiState()
        data class Success(
            val users: List<UserEntity>,
            val currentUserId: Long,
            val adminCount: Int,
            val error: String? = null,
            val processingUserId: Long = -1L   // Для защиты UI от повторных нажатий
        ) : AdminRolesUiState()

        data class Error(val message: String) : AdminRolesUiState()
    }
}
