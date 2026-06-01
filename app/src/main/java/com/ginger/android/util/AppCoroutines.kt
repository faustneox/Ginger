package com.ginger.android.util

import timber.log.Timber
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Глобальные утилиты для работы с корутинами.
 * Здесь находится обработчик необработанных ошибок.
 */
object AppCoroutines {

    /**
     * Глобальный обработчик ошибок для корутин.
     * Все необработанные исключения в корутинах будут логироваться здесь.
     */
    val errorHandler = CoroutineExceptionHandler { _, throwable ->
        Timber.e(throwable, "Uncaught coroutine exception")

        // TODO: В будущем сюда можно подключить Crashlytics / Sentry / Firebase
        // FirebaseCrashlytics.getInstance().recordException(throwable)
    }
}

/**
 * Удобная extension-функция для запуска корутин с автоматической обработкой ошибок.
 *
 * Вместо:
 * viewModelScope.launch(AppCoroutines.errorHandler) { ... }
 *
 * Можно писать:
 * launchSafe { ... }
 */
fun ViewModel.launchSafe(block: suspend CoroutineScope.() -> Unit) {
    viewModelScope.launch(AppCoroutines.errorHandler, block = block)
}
