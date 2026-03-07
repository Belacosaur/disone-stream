package com.disone.core.comments

import com.disone.core.api.CommentsApi
import com.disone.core.api.SubmitCommentRequest
import com.disone.core.api.UpdateCommentRequest
import com.disone.core.auth.AuthRepository
import com.disone.core.models.CommentsData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommentsRepository @Inject constructor(
    private val commentsApi: CommentsApi,
    private val authRepository: AuthRepository
) {

    suspend fun get(contentId: String, contentType: String, page: Int = 1, limit: Int = 20): Result<CommentsData> {
        return runCatching {
            val response = commentsApi.get(contentId, contentType, page, limit)
            when {
                response.isSuccessful -> {
                    val body = response.body()
                    if (body != null && body.data != null) Result.success(body.data)
                    else Result.failure(Exception("Empty response"))
                }
                else -> Result.failure(Exception(response.errorBody()?.string() ?: "Failed to get comments"))
            }
        }.getOrElse { Result.failure(it) }
    }

    suspend fun submit(contentId: String, contentType: String, body: String, parentId: String?): Result<Unit> {
        val auth = authRepository.getAuthHeader() ?: return Result.failure(Exception("Not authenticated"))
        return runCatching {
            val response = commentsApi.submit(auth, SubmitCommentRequest(contentId, contentType, body, parentId))
            when {
                response.isSuccessful -> Result.success(Unit)
                response.code() == 401 -> Result.failure(Exception("Unauthorized"))
                else -> Result.failure(Exception(response.errorBody()?.string() ?: "Failed to submit comment"))
            }
        }.getOrElse { Result.failure(it) }
    }

    suspend fun update(id: String, body: String): Result<Unit> {
        val auth = authRepository.getAuthHeader() ?: return Result.failure(Exception("Not authenticated"))
        return runCatching {
            val response = commentsApi.update(auth, id, UpdateCommentRequest(body))
            when {
                response.isSuccessful -> Result.success(Unit)
                response.code() == 401 -> Result.failure(Exception("Unauthorized"))
                else -> Result.failure(Exception(response.errorBody()?.string() ?: "Failed to update comment"))
            }
        }.getOrElse { Result.failure(it) }
    }

    suspend fun delete(id: String): Result<Unit> {
        val auth = authRepository.getAuthHeader() ?: return Result.failure(Exception("Not authenticated"))
        return runCatching {
            val response = commentsApi.delete(auth, id)
            when {
                response.isSuccessful -> Result.success(Unit)
                response.code() == 401 -> Result.failure(Exception("Unauthorized"))
                else -> Result.failure(Exception(response.errorBody()?.string() ?: "Failed to delete comment"))
            }
        }.getOrElse { Result.failure(it) }
    }
}
