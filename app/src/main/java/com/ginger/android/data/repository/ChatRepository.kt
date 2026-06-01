package com.ginger.android.data.repository

import android.content.Context
import com.ginger.android.data.local.AppDatabase
import com.ginger.android.data.local.ChatMessage
import com.ginger.android.data.local.ChatMessageDao
import android.net.Uri
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.FirebaseApp
import android.util.Log
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.ginger.android.util.compressImageToJpegBytes

/**
 * Репозиторий для чат-сообщений, использующий Firestore для real-time и Room для локального кеша.
 */
class ChatRepository(context: Context) {
    private val appContext = context.applicationContext
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
    private val db = AppDatabase.getInstance(context)
    private val dao: ChatMessageDao = db.chatMessageDao()

    /**
     * Слушает сообщения в коллекции `requests/{requestId}/messages` и синхронизирует их в Room.
     */
    fun observeMessages(requestId: Long): Flow<List<ChatMessage>> = callbackFlow {
        val colRef = firestore.collection("requests").document(requestId.toString()).collection("messages")
        val registration = colRef.orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot: QuerySnapshot?, error: FirebaseFirestoreException? ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val messages = snapshot?.documents?.mapNotNull { doc ->
                    val firestoreId = doc.id
                    val userId = doc.getLong("userId") ?: 0L
                    val text = doc.getString("text")
                    val attachmentUrl = doc.getString("attachmentUrl")
                    val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                    ChatMessage(
                        firestoreId = firestoreId,
                        requestId = requestId,
                        userId = userId,
                        text = text,
                        attachmentUrl = attachmentUrl,
                        createdAt = createdAt
                    )
                } ?: emptyList()

                // persist to local DB asynchronously
                launch {
                    try {
                        if (messages.isNotEmpty()) dao.insertAll(messages)
                    } catch (_: Exception) {
                        // ignore local persistence errors
                    }
                }

                trySend(messages).isSuccess
            }

        awaitClose { registration.remove() }
    }.conflate()

    /**
     * Отправить сообщение через Firestore и сохранить его локально.
     */
    suspend fun sendMessage(requestId: Long, userId: Long, text: String?, attachmentUrl: String?): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val colRef = firestore.collection("requests").document(requestId.toString()).collection("messages")
            val payload = hashMapOf<String, Any?>(
                "userId" to userId,
                "text" to text,
                "attachmentUrl" to attachmentUrl,
                "createdAt" to System.currentTimeMillis()
            )

            val docRef = colRef.add(payload).awaitTask()

            // Persist locally
            val chatMessage = ChatMessage(
                firestoreId = docRef.id,
                requestId = requestId,
                userId = userId,
                text = text,
                attachmentUrl = attachmentUrl,
                createdAt = (payload["createdAt"] as? Long) ?: System.currentTimeMillis()
            )

            dao.insert(chatMessage)
            Unit
        }
    }

    /**
     * Upload file to Firebase Storage under `requests/{requestId}/attachments/` and return public download URL.
     */
    suspend fun uploadAttachment(requestId: Long, fileUri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val filename = (fileUri.lastPathSegment ?: "file")
                .replace(Regex("[^A-Za-z0-9_.-]"), "_")
            val path = "requests/$requestId/attachments/${System.currentTimeMillis()}_$filename"
            val ref = storage.reference.child(path)

            val bucket = try { FirebaseApp.getInstance().options.storageBucket } catch (e: Exception) { null } ?: "unknown"

            // If this is an image, compress first to reduce size
            val mimeType = try { appContext.contentResolver.getType(fileUri) } catch (e: Exception) { null }

            Log.d("ChatRepository", "uploadAttachment: bucket=$bucket path=$path mime=$mimeType uri=$fileUri")

            try {
                if (mimeType != null && mimeType.startsWith("image/")) {
                    // compress to bytes
                    val compressed = compressImageToJpegBytes(appContext, fileUri, maxWidth = 1280, maxHeight = 1280, quality = 80)
                    ref.putBytes(compressed).awaitTask()
                } else {
                    // non-image: upload directly
                    ref.putFile(fileUri).awaitTask()
                }
            } catch (e: Exception) {
                Log.e("ChatRepository", "Upload failed for path=$path bucket=$bucket", e)
                throw Exception("Upload failed for bucket=$bucket path=$path: ${e.message}", e)
            }

            // Get download url
            val downloadUri = ref.downloadUrl.awaitTask()
            Log.d("ChatRepository", "uploadAttachment success: path=$path downloadUrl=$downloadUri")
            downloadUri.toString()
        }
    }

    // Simple await helper for Tasks
    private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) {
                cont.resume(task.result)
            } else {
                cont.resumeWithException(task.exception ?: Exception("Task failed"))
            }
        }
    }
}
