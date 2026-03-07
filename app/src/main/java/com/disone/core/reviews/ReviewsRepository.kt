package com.disone.core.reviews

import com.disone.core.api.ReviewsApi
import com.disone.core.api.SubmitReviewRequest
import com.disone.core.api.UpdateReviewRequest
import com.disone.core.auth.AuthRepository
import com.disone.core.models.ReviewsData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReviewsRepository @Inject constructor(
    private val reviewsApi: ReviewsApi,
    private val authRepository: AuthRepository
) {

    suspend fun get(contentId: String, contentType: String, page: Int = 1, limit: Int = 10): Result<ReviewsData> {
        return runCatching {
            val response = reviewsApi.get(contentId, contentType, page, limit)
            when {
                response.isSuccessful -> {
                    val body = response.body()
                    if (body != null && body.data != null) Result.success(body.data)
                    else Result.failure(Exception("Empty response"))
                }
                else -> Result.failure(Exception(response.errorBody()?.string() ?: "Failed to get reviews"))
            }
        }.getOrElse { Result.failure(it) }
    }

    suspend fun submit(contentId: String, contentType: String, body: String, rating: Int?): Result<Unit> {
        val auth = authRepository.getAuthHeader() ?: return Result.failure(Exception("Not authenticated"))
        return runCatching {
            val response = reviewsApi.submit(auth, SubmitReviewRequest(contentId, contentType, body, rating))
            when {
                response.isSuccessful -> Result.success(Unit)
                response.code() == 401 -> Result.failure(Exception("Unauthorized"))
                else -> Result.failure(Exception(response.errorBody()?.string() ?: "Failed to submit review"))
            }
        }.getOrElse { Result.failure(it) }
    }

    suspend fun update(id: String, body: String?, rating: Int?): Result<Unit> {
        val auth = authRepository.getAuthHeader() ?: return Result.failure(Exception("Not authenticated"))
        return runCatching {
            val response = reviewsApi.update(auth, id, UpdateReviewRequest(body, rating))
            when {
                response.isSuccessful -> Result.success(Unit)
                response.code() == 401 -> Result.failure(Exception("Unauthorized"))
                else -> Result.failure(Exception(response.errorBody()?.string() ?: "Failed to update review"))
            }
        }.getOrElse { Result.failure(it) }
    }

    suspend fun delete(id: String): Result<Unit> {
        val auth = authRepository.getAuthHeader() ?: return Result.failure(Exception("Not authenticated"))
        return runCatching {
            val response = reviewsApi.delete(auth, id)
            when {
                response.isSuccessful -> Result.success(Unit)
                response.code() == 401 -> Result.failure(Exception("Unauthorized"))
                else -> Result.failure(Exception(response.errorBody()?.string() ?: "Failed to delete review"))
            }
        }.getOrElse { Result.failure(it) }
    }
}
