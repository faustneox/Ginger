package com.ginger.android

import android.app.Application
import com.ginger.android.util.CrashlyticsLoggerProvider
import com.ginger.android.util.CrashlyticsTree
import timber.log.Timber
import dagger.hilt.android.HiltAndroidApp

/**
 * Главный Application класс Ginger.
 *
 * Отвечает за:
 * 1. Инициализацию Hilt DI контейнера (@HiltAndroidApp)
 * 2. Настройку Timber логирования:
 *    - Debug сборки: Timber.DebugTree() для полных логов в Logcat
 *    - Release сборки: CrashlyticsTree() для отправки логов в Firebase Crashlytics
 * 3. Установку глобального обработчика необработанных исключений (крэшей)
 *
 * Здесь также происходит инициализация Firebase и других SDK.
 *
 * Lifecycle: создаётся один раз при запуске приложения, существует столько же, сколько процесс приложения.
 */
@HiltAndroidApp
class GingerApp : Application() {



    private var defaultUncaughtExceptionHandler: Thread.UncaughtExceptionHandler? = null

    override fun onCreate() {
        super.onCreate()

        // Инициализируем Timber для логирования:
        // - В debug builds логируем всё в Logcat через DebugTree
        // - В release builds отправляем WARN+ и исключения в Crashlytics
        val isDebuggable = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (isDebuggable) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(CrashlyticsTree())
        }
        setupGlobalErrorHandling()
    }

    /**
     * Устанавливает глобальный обработчик необработанных исключений (крэшей).
     * При крэше логируем в Timber, который отправит в Crashlytics.
     */
    private fun setupGlobalErrorHandling() {
        // Сохраняем стандартный обработчик
        defaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()

        // Устанавливаем глобальный обработчик необработанных исключений (крэши)
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Timber.e(throwable, "Uncaught exception on thread: %s", thread.name)

            CrashlyticsLoggerProvider.instance.recordException(throwable)

            // Передаем дальше стандартному обработчику (чтобы система показала диалог крэша)
            defaultUncaughtExceptionHandler?.uncaughtException(thread, throwable)
        }

        // Примечание:
        // Обработка ошибок в корутинах уже централизована через AppCoroutines.errorHandler
        // и используется во всех ViewModel'ах через launchSafe { ... }
    }
}