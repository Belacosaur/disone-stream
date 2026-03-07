package com.disone.core.api

import com.disone.core.models.CatalogPage
import com.disone.core.models.PlayRequest
import com.disone.core.models.PlayResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface StreamApi {

    @POST("play")
    suspend fun play(
        @Header("Authorization") auth: String?,
        @Body request: PlayRequest
    ): Response<PlayResponse>

    @GET("catalog")
    suspend fun getCatalog(
        @Header("Authorization") auth: String?,
        @Query("page") page: Int,
        @Query("limit") limit: Int
    ): Response<CatalogPage>
}
