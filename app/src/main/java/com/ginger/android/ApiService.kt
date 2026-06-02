package com.ginger.android

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

// Minimal DTOs for network layer (adjust fields as backend requires)
data class LoginResponse(val success: Boolean, val userId: Long?, val message: String?)

data class RequestDto(
    val id: Long,
    val title: String,
    val description: String,
    val category: String,
    val contact: String,
    val status: String,
    val ownerUserId: Long,
    val ownerPhone: String,
    val createdAt: Long
)

data class AttachmentDto(
    val id: Long,
    val requestId: Long,
    val url: String,
    val mimeType: String?
)

data class ChatMessageDto(
    val id: Long,
    val requestId: Long,
    val userId: Long,
    val text: String?,
    val attachmentUrl: String?,
    val createdAt: Long
)

interface ApiService {
    @POST("auth/login")
    suspend fun login(@Body body: Map<String, String>): Response<LoginResponse>

    @GET("requests")
    suspend fun getAllRequests(): Response<List<RequestDto>>

    @Multipart
    @POST("requests/{id}/attachments")
    suspend fun uploadAttachment(@Path("id") requestId: Long, @Part file: MultipartBody.Part): Response<AttachmentDto>

    @GET("requests/{id}/messages")
    suspend fun getMessages(@Path("id") requestId: Long): Response<List<ChatMessageDto>>

    @POST("requests/{id}/messages")
    suspend fun sendMessage(@Path("id") requestId: Long, @Body message: ChatMessageDto): Response<ChatMessageDto>
}
