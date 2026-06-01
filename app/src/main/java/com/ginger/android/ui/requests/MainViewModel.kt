package com.ginger.android.ui.requests

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ginger.android.R
import com.ginger.android.data.local.RequestEntity
import com.ginger.android.data.local.UserEntity
import com.ginger.android.data.repository.AuthRepository
import com.ginger.android.data.session.SessionManager
import com.ginger.android.data.repository.RequestRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.Flow
import androidx.paging.PagingData
import com.ginger.android.util.ActionGuard
import com.ginger.android.util.launchSafe
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.update

/**
 * MainViewModel — главный ViewModel приложения (экран заявок).
 *
 * Отвечает за:
 * - Загрузку текущего пользователя из SessionManager и БД
 * - Загрузку и фильтрацию списка заявок (4 вкладки):
 *   * Tab 0: Все заявки (для админов) или мои (для пользователей)
 *   * Tab 1: Новые
 *   * Tab 2: В процессе
 *   * Tab 3: Закрытые
 * - Ролевое поведение (админ видит все, пользователь — только свои)
 * - Переключение между экраном заявок и профилем
 * - Выход из аккаунта (логаут)
 *
 * Используется на MainActivity. Injected зависимости:
 * - Application
 * - RequestRepository
 * - AuthRepository
 * - SessionManager
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val requestRepository: RequestRepository,
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager
) : AndroidViewModel(application) {

    private val appContext = getApplication<Application>()

    // UI state: текущий пользователь, заявки, выбранная вкладка, статус
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    // Защита от параллельных загрузок
    private val actionGuard = ActionGuard()

    init {
        loadData()
    }

    /**
     * Загружает данные пользователя и заявки при инициализации или обновлении.
     * Проверяет SessionManager на наличие текущего пользователя.
     * Если пользователя нет — сбрасывает состояние (LoginActivity отреагирует).
     */
    fun loadData() {
        val userId = sessionManager.getCurrentUserId()
        if (userId <= 0) {
            // Не выставляем isLoggedOut здесь — это не явный логаут пользователя.
            // Просто сбрасываем состояние и позволяем LoginActivity отреагировать.
            _uiState.update {
                it.copy(
                    currentUser = null,
                    isLoggedOut = false,
                    isLoading = false,
                    error = null
                )
            }
            return
        }

        // Защита от параллельных загрузок
        if (!actionGuard.startGlobal()) return

        _uiState.update { it.copy(isLoading = true, error = null) }

        launchSafe {
            try {
                val userResult = authRepository.getUserById(userId)

                userResult.onSuccess { user ->
                    if (user == null) {
                        sessionManager.logout()
                        _uiState.update {
                            it.copy(
                                currentUser = null,
                                isLoggedOut = true,
                                isLoading = false,
                                error = null
                            )
                        }
                        return@onSuccess
                    }

                    // Передаём уже загруженного user — второй DB-запрос не нужен
                    val requestsResult = loadRequestsForUser(user)

                    requestsResult.onSuccess { requests ->
                        _uiState.update {
                            it.copy(
                                currentUser = user,
                                isAdmin = user.isAdmin,
                                requests = requests,
                                isLoading = false,
                                isLoggedOut = false
                            )
                        }
                    }.onFailure {
                        _uiState.update { it.copy(isLoading = false, error = appContext.getString(R.string.error_load_requests)) }
                    }
                }.onFailure {
                    _uiState.update { it.copy(isLoading = false, error = appContext.getString(R.string.error_load_profile)) }
                }
            } finally {
                actionGuard.finishGlobal()
            }
        }
    }

    /**
     * Переключение между вкладками (0-3).
     * Перезагружает заявки для выбранной вкладки.
     * @param tabIndex Индекс вкладки (0=все, 1=новые, 2=в процессе, 3=закрытые)
     */
    fun selectTab(tabIndex: Int) {
        if (tabIndex !in 0..3) return

        if (!actionGuard.startGlobal()) return
        _uiState.update { it.copy(selectedTab = tabIndex, isLoading = true) }

        launchSafe {
            try {
                val userId = sessionManager.getCurrentUserId()
                val requestsResult = loadRequestsForCurrentFilter(userId)

                requestsResult.onSuccess { requests ->
                    _uiState.update {
                        it.copy(
                            requests = requests,
                            isLoading = false
                        )
                    }
                }.onFailure {
                    _uiState.update { it.copy(isLoading = false, error = appContext.getString(R.string.error_load_requests_tab)) }
                }
            } finally {
                actionGuard.finishGlobal()
            }
        }
    }

    /**
     * Переключение на экран профиля пользователя.
     */
    fun showProfileScreen() {
        _uiState.update { it.copy(showProfileScreen = true) }
    }

    /**
     * Переключение на экран заявок.
     */
    fun showRequestsScreen() {
        _uiState.update { it.copy(showProfileScreen = false) }
    }

    /**
     * Логаут пользователя.
     * Очищает сессию и выставляет флаг isLoggedOut для перехода на LoginActivity.
     */
    fun logout() {
        actionGuard.clear()
        authRepository.clearSession()
        _uiState.update { it.copy(isLoggedOut = true) }
    }

    /**
     * Обновление данных (pull-to-refresh).
     */
    fun refresh() {
        loadData()
    }

    /**
     * Загружает заявки для пользователя с учётом текущей вкладки.
     * Если пользователь админ — загружает все заявки (по статусу вкладки).
     * Если обычный пользователь — загружает только свои (по номеру телефона).
     */
    private suspend fun loadRequestsForUser(user: UserEntity): Result<List<RequestEntity>> {
        val status = when (_uiState.value.selectedTab) {
            1 -> RequestEntity.STATUS_NEW
            2 -> RequestEntity.STATUS_IN_PROGRESS
            3 -> RequestEntity.STATUS_CLOSED
            else -> null
        }
        return if (user.isAdmin) {
            if (status == null) requestRepository.getAll()
            else requestRepository.getByStatus(status)
        } else {
            val phone = user.phone ?: return Result.success(emptyList())
            if (status == null) requestRepository.getByOwnerPhone(phone)
            else requestRepository.getByOwnerPhoneAndStatus(phone, status)
        }
    }

    /**
     * Загружает заявки для текущего фильтра (вкладки).
     * Используется в selectTab(), где currentUser уже есть в состоянии.
     * При отсутствии — делает дополнительный запрос к БД (fallback).
     */
    private suspend fun loadRequestsForCurrentFilter(userId: Long): Result<List<RequestEntity>> {
        val currentUser = _uiState.value.currentUser
            ?: authRepository.getUserById(userId).getOrNull()

        if (currentUser == null) {
            return Result.failure(IllegalStateException("User not found"))
        }

        return loadRequestsForUser(currentUser)
    }

    /**
     * Provide a PagingData flow for the given tab index.
     * Intended for admin views where full dataset paging is required.
     */
    fun getPagedRequestsForTab(tabIndex: Int): Flow<PagingData<RequestEntity>> {
        val status = when (tabIndex) {
            1 -> RequestEntity.STATUS_NEW
            2 -> RequestEntity.STATUS_IN_PROGRESS
            3 -> RequestEntity.STATUS_CLOSED
            else -> null
        }
        return requestRepository.getPagedRequests(status)
    }

    /**
     * MainUiState — состояние экрана заявок.
     * @param currentUser Текущий залогиненный пользователь
     * @param requests Список заявок для текущей вкладки
     * @param selectedTab Индекс выбранной вкладки (0-3)
     * @param isAdmin Является ли пользователь администратором
     * @param showProfileScreen Показывать ли экран профиля (вместо заявок)
     * @param isLoading Идёт ли загрузка данных
     * @param error Сообщение об ошибке (если есть)
     * @param isLoggedOut Был ли выполнен логаут (для перехода на LoginActivity)
     */
    data class MainUiState(
        @JvmField val currentUser: UserEntity? = null,
        @JvmField val requests: List<RequestEntity> = emptyList(),
        @JvmField val selectedTab: Int = 0,
        @JvmField val isAdmin: Boolean = false,
        @JvmField val showProfileScreen: Boolean = false,
        @JvmField val isLoading: Boolean = false,
        @JvmField val error: String? = null,
        @JvmField val isLoggedOut: Boolean = false
    )
}
