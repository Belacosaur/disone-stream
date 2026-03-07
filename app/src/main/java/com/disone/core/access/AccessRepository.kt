package com.disone.core.access

import com.disone.core.api.AccessApi
import com.disone.core.auth.AuthRepository
import com.disone.core.models.AccessCheckResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccessRepository @Inject constructor(
    private val accessApi: AccessApi,
    private val authRepository: AuthRepository
) {

    suspend fun checkAccess(): Result<AccessCheckResponse> {
        val auth = authRepository.getAuthHeader() ?: return Result.failure(Exception("Not authenticated"))
        return runCatching {
            val response = accessApi.check(auth)
            when {
                response.isSuccessful -> {
                    val body = response.body()
                    if (body != null) Result.success(body)
                    else Result.failure(Exception("Empty response"))
                }
                response.code() == 401 -> Result.failure(Exception("Unauthorized"))
                else -> Result.failure(Exception(response.errorBody()?.string() ?: "Failed to check access"))
            }
        }.getOrElse { Result.failure(it) }
    }
}
