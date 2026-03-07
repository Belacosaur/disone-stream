package com.disone.core.api

import com.disone.core.models.CommentsResponse
import retrofit2.Response
import retrofit2.http.*

interface CommentsApi {

    @POST("comments/submit")
    suspend fun submit(
        @Header("Authorization") auth: String,
        @Body body: SubmitCommentRequest
    ): Response<SubmitCommentResponse>

    @GET("comments/{contentId}/{contentType}")
    suspend fun get(
        @Path("contentId") contentId: String,
        @Path("contentType") contentType: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<CommentsResponse>

    @PUT("comments/{id}")
    suspend fun update(
        @Header("Authorization") auth: String,
        @Path("id") id: String,
        @Body body: UpdateCommentRequest
    ): Response<Unit>

    @DELETE("comments/{id}")
    suspend fun delete(
        @Header("Authorization") auth: String,
        @Path("id") id: String
    ): Response<Unit>
}

data class SubmitCommentRequest(
    val contentId: String,
    val contentType: String,
    val body: String,
    val parentId: String? = null
)

data class UpdateCommentRequest(
    val body: String
)

data class SubmitCommentResponse(
    val success: Boolean = true
)
