package com.disone.core.api

import com.disone.core.models.AccessCheckResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header

interface AccessApi {

    @GET("access/check")
    suspend fun check(
        @Header("Authorization") auth: String
    ): Response<AccessCheckResponse>
}
