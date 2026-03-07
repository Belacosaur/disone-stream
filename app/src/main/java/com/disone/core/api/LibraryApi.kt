package com.disone.core.api

import com.disone.core.models.LibraryAddRequest
import com.disone.core.models.LibraryContainsResponse
import com.disone.core.models.LibraryItem
import com.disone.core.models.LibraryListResponse
import com.disone.core.models.LibraryRewindRequest
import com.disone.core.models.LibraryUpdateProgressRequest
import retrofit2.Response
import retrofit2.http.*

interface LibraryApi {

    @GET("library/list")
    suspend fun list(
        @Header("Authorization") auth: String,
        @Query("type") type: String? = null,
        @Query("sort") sort: String = "added",
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50
    ): Response<LibraryListResponse>

    @GET("library/continue-watching")
    suspend fun continueWatching(
        @Header("Authorization") auth: String
    ): Response<ContinueWatchingResponse>

    @GET("library/contains")
    suspend fun contains(
        @Header("Authorization") auth: String,
        @Query("contentId") contentId: String? = null,
        @Query("library_item_id") libraryItemId: String? = null
    ): Response<LibraryContainsResponse>

    @POST("library/add")
    suspend fun add(
        @Header("Authorization") auth: String,
        @Body body: LibraryAddRequest
    ): Response<LibraryAddResponse>

    @DELETE("library/remove")
    suspend fun remove(
        @Header("Authorization") auth: String,
        @Query("library_item_id") libraryItemId: String
    ): Response<LibraryRemoveResponse>

    @POST("library/update-progress")
    suspend fun updateProgress(
        @Header("Authorization") auth: String,
        @Body body: LibraryUpdateProgressRequest
    ): Response<LibraryUpdateResponse>

    @POST("library/rewind")
    suspend fun rewind(
        @Header("Authorization") auth: String,
        @Body body: LibraryRewindRequest
    ): Response<LibraryUpdateResponse>
}

data class ContinueWatchingResponse(
    val success: Boolean? = true,
    val catalog: List<LibraryItem>
)

data class LibraryAddResponse(
    val success: Boolean? = true,
    @com.google.gson.annotations.SerializedName("library_item_id") val libraryItemId: String? = null
)

data class LibraryRemoveResponse(
    val success: Boolean? = true
)

data class LibraryUpdateResponse(
    val success: Boolean? = true
)
