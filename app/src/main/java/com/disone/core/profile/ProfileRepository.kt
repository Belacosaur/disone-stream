package com.disone.core.profile

import com.disone.core.api.ProfileApi
import com.disone.core.api.UpdateUsernameRequest
import com.disone.core.auth.AuthRepository
import com.disone.core.models.UserProfile
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository @Inject constructor(
    private val profileApi: ProfileApi,
    private val authRepository: AuthRepository
) {

    suspend fun getUser(): Result<UserProfile> {
        val auth = authRepository.getAuthHeader() ?: return Result.failure(Exception("Not authenticated"))
        return runCatching {
            val response = profileApi.getUser(auth)
            when {
                response.isSuccessful -> {
                    val body = response.body()
                    if (body != null) Result.success(body.user)
                    else Result.failure(Exception("Empty response"))
                }
                response.code() == 401 -> Result.failure(Exception("Unauthorized"))
                else -> Result.failure(Exception(response.errorBody()?.string() ?: "Failed to get user"))
            }
        }.getOrElse { Result.failure(it) }
    }

    suspend fun updateUsername(username: String): Result<Unit> {
        val auth = authRepository.getAuthHeader() ?: return Result.failure(Exception("Not authenticated"))
        return runCatching {
            val response = profileApi.updateUsername(auth, UpdateUsernameRequest(username))
            when {
                response.isSuccessful -> Result.success(Unit)
                response.code() == 401 -> Result.failure(Exception("Unauthorized"))
                else -> Result.failure(Exception(response.errorBody()?.string() ?: "Failed to update username"))
            }
        }.getOrElse { Result.failure(it) }
    }

    suspend fun uploadAvatar(byteArray: ByteArray, filename: String): Result<UserProfile> {
        val auth = authRepository.getAuthHeader() ?: return Result.failure(Exception("Not authenticated"))
        return runCatching {
            val ext = filename.substringAfterLast('.', "jpg").let { if (it == filename) "jpg" else it }
            val tempFile = File.createTempFile("avatar_", ".$ext")
            tempFile.deleteOnExit()
            FileOutputStream(tempFile).use { it.write(byteArray) }
            val part = MultipartBody.Part.createFormData(
                "avatar",
                filename,
                tempFile.asRequestBody("image/*".toMediaTypeOrNull())
            )
            val response = profileApi.uploadAvatar(auth, part)
            tempFile.delete()
            when {
                response.isSuccessful -> {
                    val body = response.body()
                    if (body != null) Result.success(body.user)
                    else Result.failure(Exception("Empty response"))
                }
                response.code() == 401 -> Result.failure(Exception("Unauthorized"))
                else -> Result.failure(Exception(response.errorBody()?.string() ?: "Failed to upload avatar"))
            }
        }.getOrElse { Result.failure(it) }
    }

    suspend fun deleteAvatar(): Result<Unit> {
        val auth = authRepository.getAuthHeader() ?: return Result.failure(Exception("Not authenticated"))
        return runCatching {
            val response = profileApi.deleteAvatar(auth)
            when {
                response.isSuccessful -> Result.success(Unit)
                response.code() == 401 -> Result.failure(Exception("Unauthorized"))
                else -> Result.failure(Exception(response.errorBody()?.string() ?: "Failed to delete avatar"))
            }
        }.getOrElse { Result.failure(it) }
    }
}
