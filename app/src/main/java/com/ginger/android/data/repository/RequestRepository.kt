package com.ginger.android.data.repository

import android.content.Context
import timber.log.Timber
import com.ginger.android.data.local.AppDatabase
import com.ginger.android.data.local.RequestEntity
import com.ginger.android.data.local.RequestDao
import com.ginger.android.data.local.RequestAttachment
import com.ginger.android.data.local.RequestAttachmentDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.net.Uri
import com.ginger.android.AttachmentDto
import com.ginger.android.util.createMultipartFromUri
import com.ginger.android.ApiClient
import com.ginger.android.ApiService
import com.ginger.android.RequestDto
import com.ginger.android.util.retryWithBackoff
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow

/**
 * Репозиторий для работы с заявками.
 * Отвечает за доступ к данным и скрыщает детали реализации (Room).
 */
class RequestRepository {

    private val requestDao: RequestDao
    private val requestAttachmentDao: RequestAttachmentDao?

    constructor(context: Context) {
        val db = AppDatabase.getInstance(context)
        requestDao = db.requestDao()
        requestAttachmentDao = db.requestAttachmentDao()
    }

    // Test / DI-friendly constructor
    constructor(requestDao: RequestDao) {
        this.requestDao = requestDao
        this.requestAttachmentDao = null
    }

    // Test / DI-friendly constructor with both DAOs
    constructor(requestDao: RequestDao, requestAttachmentDao: RequestAttachmentDao?) {
        this.requestDao = requestDao
        this.requestAttachmentDao = requestAttachmentDao
    }

    /**
     * Получить все заявки (для администраторов).
     */
    suspend fun getAll(): Result<List<RequestEntity>> = withContext(Dispatchers.IO) {
        runCatching { requestDao.getAll() }
            .onFailure { Timber.e(it, "Failed to get all requests") }
    }

    /**
     * Получить заявки по статусу.
     */
    suspend fun getByStatus(status: String): Result<List<RequestEntity>> = withContext(Dispatchers.IO) {
        runCatching { requestDao.getByStatus(status) }
            .onFailure { Timber.e(it, "Failed to get requests by status=%s", status) }
    }

    /**
     * Получить заявки пользователя по телефону.
     */
    suspend fun getByOwnerPhone(ownerPhone: String): Result<List<RequestEntity>> = withContext(Dispatchers.IO) {
        runCatching { requestDao.getByOwnerPhone(ownerPhone) }
    }

    /**
     * Получить заявки пользователя по телефону и статусу.
     */
    suspend fun getByOwnerPhoneAndStatus(ownerPhone: String, status: String): Result<List<RequestEntity>> =
        withContext(Dispatchers.IO) {
            runCatching { requestDao.getByOwnerPhoneAndStatus(ownerPhone, status) }
        }

    /**
     * Получить заявку по ID.
     */
    suspend fun getById(id: Long): Result<RequestEntity?> = withContext(Dispatchers.IO) {
        runCatching { requestDao.getById(id) }
    }

    /**
     * Создать новую заявку. Возвращает ID созданной записи.
     */
    suspend fun insert(request: RequestEntity, attachmentUris: List<String>? = null): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val id = requestDao.insert(request)

            if (!attachmentUris.isNullOrEmpty()) {
                val dao = requestAttachmentDao
                    ?: throw IllegalStateException("RequestAttachmentDao is not available")

                val attachments = attachmentUris.map { uri -> RequestAttachment(requestId = id, uri = uri) }
                // insertAll is suspend in DAO
                dao.insertAll(attachments)
            }

            Result.success(id)
        } catch (e: Exception) {
            Timber.e(e, "Failed to insert request with attachments")
            Result.failure(e)
        }
    }

        /**
         * Загрузить файл прикрепления на сервер и сохранить запись в локальной БД.
         */
        suspend fun uploadAttachmentToServer(context: Context, requestId: Long, fileUri: Uri): Result<AttachmentDto> = withContext(Dispatchers.IO) {
            try {
                val part = createMultipartFromUri(context, fileUri)
                val service = ApiClient.create(com.ginger.android.ApiService::class.java)
                val resp = retryWithBackoff { service.uploadAttachment(requestId, part) }

                if (resp.isSuccessful) {
                    val dto = resp.body()!!
                    try {
                        val dao = requestAttachmentDao
                        if (dao != null) {
                            dao.insert(RequestAttachment(requestId = requestId, uri = dto.url, mimeType = dto.mimeType))
                        } else {
                            Timber.w("RequestAttachmentDao not available; uploaded but not persisted locally")
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to persist uploaded attachment locally")
                    }
                    Result.success(dto)
                } else {
                    val msg = resp.errorBody()?.string()
                    throw Exception("Upload failed: "+ (msg ?: resp.code().toString()))
                }
            } catch (e: Exception) {
                Timber.e(e, "Attachment upload failed")
                Result.failure(e)
            }
        }

    /**
     * Пример получения заявок с сервера (заглушка).
     */
    suspend fun fetchRemoteAll(): Result<List<RequestDto>?> = withContext(Dispatchers.IO) {
        runCatching {
            val service = ApiClient.create(ApiService::class.java)
            val resp = retryWithBackoff { service.getAllRequests() }
            if (resp.isSuccessful) resp.body() else throw Exception("Network fetch failed: ${resp.code()}")
        }.onFailure { Timber.e(it, "Remote fetch failed") }
    }

    // Paging support using Room PagingSource
    fun getPagedRequests(status: String? = null): Flow<PagingData<RequestEntity>> {
        val pagingSourceFactory = {
            if (status.isNullOrEmpty()) {
                requestDao.getAllPaging()
            } else {
                requestDao.getByStatusPaging(status)
            }
        }
        return Pager(PagingConfig(pageSize = 20, enablePlaceholders = false), pagingSourceFactory = pagingSourceFactory).flow
    }

    /**
     * Paging support for requests belonging to a specific owner (by phone).
     */
    fun getPagedRequestsForOwner(ownerPhone: String, status: String? = null): Flow<PagingData<RequestEntity>> {
        val pagingSourceFactory = {
            if (status.isNullOrEmpty()) {
                requestDao.getByOwnerPhonePaging(ownerPhone)
            } else {
                requestDao.getByOwnerPhoneAndStatusPaging(ownerPhone, status)
            }
        }
        return Pager(PagingConfig(pageSize = 20, enablePlaceholders = false), pagingSourceFactory = pagingSourceFactory).flow
    }

    // Exposed for unit tests: return the paging source factory without starting the Pager flow
    internal fun pagingSourceFactoryForOwner(ownerPhone: String, status: String? = null): () -> androidx.paging.PagingSource<Int, RequestEntity> {
        return {
            if (status.isNullOrEmpty()) {
                requestDao.getByOwnerPhonePaging(ownerPhone)
            } else {
                requestDao.getByOwnerPhoneAndStatusPaging(ownerPhone, status)
            }
        }
    }

    /**
     * Обновить поля заявки (используется в RequestDetailActivity).
     */
    suspend fun updateFields(
        id: Long,
        title: String,
        description: String,
        category: String,
        contact: String,
        status: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val updatedRows = requestDao.updateFields(id, title, description, category, contact, status)
            updatedRows > 0
        }
    }

    /**
     * Удалить заявку.
     */
    suspend fun deleteById(id: Long): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { requestDao.deleteById(id) }
    }
}
