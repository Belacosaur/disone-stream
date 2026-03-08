package com.disone.core.library

import com.disone.core.api.LibraryApi
import com.disone.core.auth.AuthRepository
import com.disone.core.models.LibraryAddRequest
import com.disone.core.models.LibraryItem
import com.disone.core.models.LibraryProgress
import com.disone.core.models.LibraryRewindRequest
import com.disone.core.models.LibraryUpdateProgressRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryRepository @Inject constructor(
    private val libraryApi: LibraryApi,
    private val authRepository: AuthRepository
) {

    suspend fun list(type: String? = null, sort: String = "added", page: Int = 1, limit: Int = 50): Result<Pair<List<LibraryItem>, Boolean>> {
        val auth = authRepository.getAuthHeader() ?: return Result.failure(Exception("Not authenticated"))
        return runCatching {
            val response = libraryApi.list(auth, type, sort, page, limit)
            when {
                response.isSuccessful -> {
                    val body = response.body()
                    if (body != null) Result.success(body.catalog to body.hasNextPage)
                    else Result.failure(Exception("Empty response"))
                }
                response.code() == 401 -> Result.failure(Exception("Unauthorized"))
                else -> Result.failure(Exception(response.errorBody()?.string() ?: "Failed to fetch library"))
            }
        }.getOrElse { Result.failure(it) }
    }

    suspend fun continueWatching(): Result<List<LibraryItem>> {
        val auth = authRepository.getAuthHeader() ?: return Result.failure(Exception("Not authenticated"))
        return runCatching {
            val response = libraryApi.continueWatching(auth)
            when {
                response.isSuccessful -> {
                    val body = response.body()
                    if (body != null) Result.success(body.catalog)
                    else Result.success(emptyList())
                }
                response.code() == 401 -> Result.failure(Exception("Unauthorized"))
                else -> Result.failure(Exception(response.errorBody()?.string() ?: "Failed"))
            }
        }.getOrElse { Result.failure(it) }
    }

    suspend fun contains(contentId: String): Result<Boolean> {
        val auth = authRepository.getAuthHeader() ?: return Result.success(false)
        val id = if (contentId.contains(":")) contentId else "movie:$contentId"
        return runCatching {
            val response = libraryApi.contains(auth, contentId = id)
            when {
                response.isSuccessful -> Result.success(response.body()?.inLibrary ?: false)
                response.code() == 401 -> Result.success(false)
                else -> Result.failure(Exception(response.errorBody()?.string() ?: "Failed"))
            }
        }.getOrElse { Result.failure(it) }
    }

    /** Fetches library progress for an item. Returns LibraryProgress or null if not in library or no progress. */
    suspend fun getProgress(libraryItemId: String): Result<LibraryProgress?> {
        val auth = authRepository.getAuthHeader() ?: return Result.success(null)
        val id = if (libraryItemId.contains(":")) libraryItemId else "movie:$libraryItemId"
        return runCatching {
            val response = libraryApi.contains(auth, contentId = id)
            when {
                response.isSuccessful -> {
                    val body = response.body()
                    if (body?.inLibrary == true && body.timeOffset != null && body.timeOffset > 0 && body.isWatched != true) {
                        LibraryProgress(body.timeOffset, body.duration ?: 0L, body.videoId)
                    } else {
                        null
                    }
                }
                response.code() == 401 -> null
                else -> throw Exception(response.errorBody()?.string() ?: "Failed")
            }
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun add(libraryItemId: String, name: String, type: String, metaId: String, poster: String? = null): Result<Unit> {
        val auth = authRepository.getAuthHeader() ?: return Result.failure(Exception("Not authenticated"))
        val id = if (libraryItemId.contains(":")) libraryItemId else "$type:$libraryItemId"
        val meta = if (metaId.contains(":")) metaId else "$type:$metaId"
        return runCatching {
            val response = libraryApi.add(auth, LibraryAddRequest(id, name, type, meta, poster))
            when {
                response.isSuccessful -> Result.success(Unit)
                response.code() == 401 -> Result.failure(Exception("Unauthorized"))
                else -> Result.failure(Exception(response.errorBody()?.string() ?: "Failed to add"))
            }
        }.getOrElse { Result.failure(it) }
    }

    suspend fun remove(libraryItemId: String): Result<Unit> {
        val auth = authRepository.getAuthHeader() ?: return Result.failure(Exception("Not authenticated"))
        val id = if (libraryItemId.contains(":")) libraryItemId else "movie:$libraryItemId"
        return runCatching {
            val response = libraryApi.remove(auth, id)
            when {
                response.isSuccessful -> Result.success(Unit)
                response.code() == 401 -> Result.failure(Exception("Unauthorized"))
                else -> Result.failure(Exception(response.errorBody()?.string() ?: "Failed to remove"))
            }
        }.getOrElse { Result.failure(it) }
    }

    suspend fun updateProgress(libraryItemId: String, timeOffset: Long, duration: Long, videoId: String? = null): Result<Unit> {
        val auth = authRepository.getAuthHeader() ?: return Result.failure(Exception("Not authenticated"))
        val id = if (libraryItemId.contains(":")) libraryItemId else "movie:$libraryItemId"
        return runCatching {
            val response = libraryApi.updateProgress(auth, LibraryUpdateProgressRequest(id, timeOffset, null, null, videoId, duration))
            when {
                response.isSuccessful -> Result.success(Unit)
                response.code() == 401 -> Result.failure(Exception("Unauthorized"))
                response.code() == 404 -> Result.failure(Exception("Not in library"))
                else -> Result.failure(Exception(response.errorBody()?.string() ?: "Failed"))
            }
        }.getOrElse { Result.failure(it) }
    }

    suspend fun rewind(libraryItemId: String): Result<Unit> {
        val auth = authRepository.getAuthHeader() ?: return Result.failure(Exception("Not authenticated"))
        val id = if (libraryItemId.contains(":")) libraryItemId else "movie:$libraryItemId"
        return runCatching {
            val response = libraryApi.rewind(auth, LibraryRewindRequest(id))
            when {
                response.isSuccessful -> Result.success(Unit)
                response.code() == 401 -> Result.failure(Exception("Unauthorized"))
                else -> Result.failure(Exception(response.errorBody()?.string() ?: "Failed"))
            }
        }.getOrElse { Result.failure(it) }
    }
}
