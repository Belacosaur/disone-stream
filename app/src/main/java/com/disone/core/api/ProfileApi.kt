package com.disone.core.api

import com.disone.core.models.UserResponse
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

interface ProfileApi {

    @GET("auth/user")
    suspend fun getUser(
        @Header("Authorization") auth: String
    ): Response<UserResponse>

    @PUT("profile/username")
    suspend fun updateUsername(
        @Header("Authorization") auth: String,
        @Body body: UpdateUsernameRequest
    ): Response<Unit>

    @Multipart
    @POST("profile/avatar")
    suspend fun uploadAvatar(
        @Header("Authorization") auth: String,
        @Part avatar: MultipartBody.Part
    ): Response<UserResponse>

    @DELETE("profile/avatar")
    suspend fun deleteAvatar(
        @Header("Authorization") auth: String
    ): Response<Unit>
}

data class UpdateUsernameRequest(
    val username: String
)
