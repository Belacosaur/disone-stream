package com.disone.core.api

import com.disone.core.models.AuthVerifyRequest
import com.disone.core.models.AuthVerifyResponse
import com.disone.core.models.NonceResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface AuthApi {

    @GET("auth/nonce")
    suspend fun getNonce(@Query("wallet") wallet: String? = null): Response<NonceResponse>

    @POST("auth/verify")
    suspend fun verify(@Body request: AuthVerifyRequest): Response<AuthVerifyResponse>
}
