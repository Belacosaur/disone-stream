package com.disone.core.api

import com.disone.core.models.RatingResponse
import com.disone.core.models.SubmitRatingRequest
import retrofit2.Response
import retrofit2.http.*

interface RatingsApi {

    @POST("ratings/submit")
    suspend fun submit(
        @Header("Authorization") auth: String,
        @Body body: SubmitRatingRequest
    ): Response<RatingResponse>

    @GET("ratings/{contentId}/{contentType}")
    suspend fun get(
        @Header("Authorization") auth: String?,
        @Path("contentId") contentId: String,
        @Path("contentType") contentType: String
    ): Response<RatingResponse>

    @DELETE("ratings/{contentId}/{contentType}")
    suspend fun delete(
        @Header("Authorization") auth: String,
        @Path("contentId") contentId: String,
        @Path("contentType") contentType: String
    ): Response<Unit>
}
