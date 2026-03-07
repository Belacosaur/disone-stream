package com.disone.core.ratings

import com.disone.core.api.RatingsApi
import com.disone.core.auth.AuthRepository
import com.disone.core.models.RatingData
import com.disone.core.models.SubmitRatingRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RatingsRepository @Inject constructor(
    private val ratingsApi: RatingsApi,
    private val authRepository: AuthRepository
) {

    suspend fun get(contentId: String, contentType: String): Result<RatingData> {
        val auth = authRepository.getAuthHeader()
        return runCatching {
            val response = ratingsApi.get(auth, contentId, contentType)
            when {
                response.isSuccessful -> {
                    val body = response.body()
                    if (body != null && body.data != null) Result.success(body.data)
                    else Result.failure(Exception("Empty response"))
                }
                else -> Result.failure(Exception(response.errorBody()?.string() ?: "Failed to get ratings"))
            }
        }.getOrElse { Result.failure(it) }
    }

    suspend fun submit(contentId: String, contentType: String, rating: Int): Result<Unit> {
        val auth = authRepository.getAuthHeader() ?: return Result.failure(Exception("Not authenticated"))
        return runCatching {
            val response = ratingsApi.submit(auth, SubmitRatingRequest(contentId, contentType, rating))
            when {
                response.isSuccessful -> Result.success(Unit)
                response.code() == 401 -> Result.failure(Exception("Unauthorized"))
                else -> Result.failure(Exception(response.errorBody()?.string() ?: "Failed to submit rating"))
            }
        }.getOrElse { Result.failure(it) }
    }

    suspend fun delete(contentId: String, contentType: String): Result<Unit> {
        val auth = authRepository.getAuthHeader() ?: return Result.failure(Exception("Not authenticated"))
        return runCatching {
            val response = ratingsApi.delete(auth, contentId, contentType)
            when {
                response.isSuccessful -> Result.success(Unit)
                response.code() == 401 -> Result.failure(Exception("Unauthorized"))
                else -> Result.failure(Exception(response.errorBody()?.string() ?: "Failed to delete rating"))
            }
        }.getOrElse { Result.failure(it) }
    }
}
