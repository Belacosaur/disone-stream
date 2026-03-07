package com.disone.core.api

import com.disone.core.models.ReviewsResponse
import com.disone.core.models.SubmitReviewResponse
import retrofit2.Response
import retrofit2.http.*

interface ReviewsApi {

    @POST("reviews/submit")
    suspend fun submit(
        @Header("Authorization") auth: String,
        @Body body: SubmitReviewRequest
    ): Response<SubmitReviewResponse>

    @GET("reviews/{contentId}/{contentType}")
    suspend fun get(
        @Path("contentId") contentId: String,
        @Path("contentType") contentType: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 10
    ): Response<ReviewsResponse>

    @PUT("reviews/{id}")
    suspend fun update(
        @Header("Authorization") auth: String,
        @Path("id") id: String,
        @Body body: UpdateReviewRequest
    ): Response<Unit>

    @DELETE("reviews/{id}")
    suspend fun delete(
        @Header("Authorization") auth: String,
        @Path("id") id: String
    ): Response<Unit>
}

data class SubmitReviewRequest(
    val contentId: String,
    val contentType: String,
    val body: String,
    val rating: Int? = null
)

data class UpdateReviewRequest(
    val body: String? = null,
    val rating: Int? = null
)
