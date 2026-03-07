package com.disone.core.api
import retrofit2.Response
import retrofit2.http.*

interface AddonApi {

    @GET("addons")
    suspend fun getAddons(
        @Header("Authorization") auth: String
    ): Response<AddonsResponse>

    @POST("addons")
    suspend fun addAddon(
        @Header("Authorization") auth: String,
        @Body body: AddAddonRequest
    ): Response<AddonResponse>

    @DELETE("addons")
    suspend fun removeAddon(
        @Header("Authorization") auth: String,
        @Query("url") url: String
    ): Response<Unit>

    @PATCH("addons")
    suspend fun setAddonEnabled(
        @Header("Authorization") auth: String,
        @Body body: SetAddonEnabledRequest
    ): Response<AddonResponse>
}

data class AddonsResponse(
    val addons: List<AddonResponse>
)

data class AddAddonRequest(
    val url: String,
    val manifest: Map<String, Any?>,
    val enabled: Boolean = true
)

data class SetAddonEnabledRequest(
    val url: String,
    val enabled: Boolean
)

data class AddonResponse(
    val url: String,
    val manifest: Map<String, Any?>,
    val enabled: Boolean
)
