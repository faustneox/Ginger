package com.ginger.android.di

import android.content.Context
import com.ginger.android.ApiClient
import com.ginger.android.ApiService
import com.ginger.android.data.local.AppDatabase
import com.ginger.android.data.repository.AuthRepository
import com.ginger.android.data.repository.RequestRepository
import com.ginger.android.data.session.SessionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton

/**
 * DatabaseModule — модуль для инъекции Room БД.
 * Предоставляет singleton экземпляр AppDatabase для всего приложения.
 *
 * Внедряется в SingletonComponent, так что существует на всю жизнь приложения.
 * Используется Repository классами для доступа к локальным данным.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    /**
     * Создаёт или возвращает существующий singleton AppDatabase.
     * @param context Application context для инициализации Room
     * @return AppDatabase singleton
     */
    @Singleton
    @Provides
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        AppDatabase.getInstance(context)
}

/**
 * NetworkModule — модуль для инъекции Retrofit API клиента.
 * Предоставляет singleton экземпляр ApiService для сетевых запросов.
 *
 * Внедряется в SingletonComponent для единственного экземпляра на всё приложение.
 * Используется Repository классами для удалённых API вызовов (если требуется).
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    /**
     * Создаёт Retrofit сервис для работы с API.
     * @return ApiService экземпляр с базовой конфигурацией (OkHttp, JSON парсер)
     */
    @Singleton
    @Provides
    fun provideApiService(): ApiService = ApiClient.create(ApiService::class.java)
}

/**
 * SessionModule — модуль для инъекции SessionManager.
 * Управляет текущей сессией пользователя (ID, роль, статус логина).
 *
 * SessionManager хранит состояние аутентификации и доступен везде через Hilt.
 */
@Module
@InstallIn(SingletonComponent::class)
object SessionModule {
    /**
     * Создаёт singleton SessionManager для управления текущей сессией.
     * @param context Application context
     * @return SessionManager singleton
     */
    @Singleton
    @Provides
    fun provideSessionManager(@ApplicationContext context: Context): SessionManager =
        SessionManager.getInstance(context)
}

/**
 * RepositoryModule — модуль для инъекции Repository классов.
 * Содержит фасады для доступа к данным (локальным и удалённым).
 *
 * Предоставляет:
 * - AuthRepository: логирование, регистрация, управление пользователем
 * - RequestRepository: управление заявками (CRUD операции)
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    /**
     * Создаёт singleton AuthRepository для работы с пользователями.
     * @param context Application context
     * @return AuthRepository singleton
     */
    @Singleton
    @Provides
    fun provideAuthRepository(@ApplicationContext context: Context): AuthRepository =
        AuthRepository(context)

    /**
     * Создаёт singleton RequestRepository для работы с заявками.
     * @param context Application context
     * @return RequestRepository singleton
     */
    @Singleton
    @Provides
    fun provideRequestRepository(@ApplicationContext context: Context): RequestRepository =
        RequestRepository(context)
}
