package com.ginger.android

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

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

interface ApiService {
    @POST("auth/login")
    suspend fun login(@Body body: Map<String, String>): Response<LoginResponse>

    @GET("requests")
    suspend fun getAllRequests(): Response<List<RequestDto>>
}
