package com.disone.core.api

import com.disone.core.models.BlockhashResponse
import com.disone.core.models.CreateTransactionRequest
import com.disone.core.models.CreateTransactionResponse
import com.disone.core.models.PurchaseRequest
import com.disone.core.models.SubmitSignedTransactionRequest
import com.disone.core.models.SubmitSignedTransactionResponse
import com.disone.core.models.PurchaseResponse
import com.disone.core.models.SubscriptionPackagesResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface SubscriptionApi {

    @GET("subscription/blockhash")
    suspend fun getBlockhash(): Response<BlockhashResponse>

    @POST("subscription/create-transaction")
    suspend fun createTransaction(
        @Header("Authorization") auth: String,
        @Body body: CreateTransactionRequest
    ): Response<CreateTransactionResponse>

    @POST("subscription/submit-signed-transaction")
    suspend fun submitSignedTransaction(
        @Header("Authorization") auth: String,
        @Body body: SubmitSignedTransactionRequest
    ): Response<SubmitSignedTransactionResponse>

    @GET("subscription/packages")
    suspend fun getPackages(): Response<SubscriptionPackagesResponse>

    @POST("subscription/purchase")
    suspend fun purchase(
        @Header("Authorization") auth: String,
        @Body body: PurchaseRequest
    ): Response<PurchaseResponse>
}
