package com.ginger.android.ui.requests

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ginger.android.data.repository.ChatRepository
import com.ginger.android.data.session.SessionManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = ChatRepository(application)
    private val sessionManager = SessionManager.getInstance(application)
    
    data class UploadError(val requestId: Long, val fileUri: Uri, val text: String?, val message: String)

    private val _uploadErrors = MutableSharedFlow<UploadError>()
    val uploadErrors: SharedFlow<UploadError> = _uploadErrors.asSharedFlow()

    fun observeMessages(requestId: Long) = repo.observeMessages(requestId)

    fun sendMessage(requestId: Long, text: String?, attachmentUrl: String? = null) {
        val userId = sessionManager.getCurrentUserId()
        viewModelScope.launch {
            val res = repo.sendMessage(requestId, userId, text, attachmentUrl)
            res.onFailure { ex ->
                Log.e("ChatViewModel", "sendMessage failed", ex)
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Ошибка отправки сообщения: ${ex.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun uploadAttachmentAndSend(requestId: Long, fileUri: Uri, text: String? = null) {
        val userId = sessionManager.getCurrentUserId()
        viewModelScope.launch {
            // Check session
            if (!sessionManager.hasActiveSession()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Пожалуйста, войдите в аккаунт", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            // Check network availability
            if (!isNetworkAvailable()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Нет интернет-соединения", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            val uploadResult = repo.uploadAttachment(requestId, fileUri)
            uploadResult.onSuccess { url ->
                val sendRes = repo.sendMessage(requestId, userId, text, url)
                sendRes.onFailure { ex ->
                    Log.e("ChatViewModel", "sendMessage after upload failed", ex)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(getApplication(), "Ошибка отправки сообщения после загрузки: ${ex.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            uploadResult.onFailure { ex ->
                Log.e("ChatViewModel", "uploadAttachment failed", ex)
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Ошибка загрузки файла: ${ex.message}", Toast.LENGTH_SHORT).show()
                }
                // emit ephemeral event so UI can show retry action
                try {
                    _uploadErrors.emit(UploadError(requestId, fileUri, text, ex.message ?: ""))
                } catch (e: Exception) {
                    Log.w("ChatViewModel", "Failed to emit upload error event", e)
                }
            }
        }
    }

    fun retryUpload(requestId: Long, fileUri: Uri, text: String? = null) {
        uploadAttachmentAndSend(requestId, fileUri, text)
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
