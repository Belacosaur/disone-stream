package com.disone.core.streaming

import com.disone.core.addons.DisoneStream
import com.disone.core.api.StreamApi
import com.disone.core.auth.AuthRepository
import com.disone.core.models.CatalogItem
import com.disone.core.models.CatalogPage
import com.disone.core.models.PlayRequest
import com.disone.core.models.PlayResponse
import com.disone.core.models.PlayStreamRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreamingRepository @Inject constructor(
    private val streamApi: StreamApi,
    private val authRepository: AuthRepository
) {

    suspend fun play(magnet: String, fileIndex: Int = 0): Result<PlayResponse> = withContext(Dispatchers.IO) {
        try {
            val authHeader = authRepository.getAuthHeader()
            val response = streamApi.play(
                auth = authHeader,
                request = PlayRequest(
                    magnet = magnet,
                    clientSupportsP2p = true,
                    fileIndex = fileIndex
                )
            )
            when {
                response.isSuccessful -> {
                    val body = response.body()
                    if (body != null) Result.success(body)
                    else Result.failure(Exception("Empty play response"))
                }
                else -> Result.failure(Exception(response.errorBody()?.string() ?: "Play failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun playStream(stream: DisoneStream): Result<PlayResponse> = withContext(Dispatchers.IO) {
        val magnet = stream.magnet ?: stream.infoHash?.let { "magnet:?xt=urn:btih:$it" }
        if (magnet != null) {
            play(magnet, stream.fileIdx ?: 0)
        } else if (stream.url != null) {
            try {
                val authHeader = authRepository.getAuthHeader()
                val response = streamApi.play(
                    auth = authHeader,
                    request = PlayRequest(
                        magnet = null,
                        clientSupportsP2p = false,
                        fileIndex = stream.fileIdx,
                        infoHash = stream.infoHash,
                        url = stream.url,
                        stream = PlayStreamRequest(
                            infoHash = stream.infoHash,
                            magnet = null,
                            url = stream.url,
                            fileIdx = stream.fileIdx
                        )
                    )
                )
                when {
                    response.isSuccessful -> {
                        val body = response.body()
                        if (body != null) Result.success(body)
                        else Result.failure(Exception("Empty play response"))
                    }
                    else -> Result.failure(Exception(response.errorBody()?.string() ?: "Play failed"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        } else {
            Result.failure(Exception("Stream has no magnet or url"))
        }
    }

    suspend fun getCatalog(page: Int = 1, limit: Int = 20): Result<CatalogPage> = withContext(Dispatchers.IO) {
        try {
            val authHeader = authRepository.getAuthHeader()
            val response = streamApi.getCatalog(auth = authHeader, page = page, limit = limit)
            when {
                response.isSuccessful -> {
                    val body = response.body()
                    if (body != null) Result.success(body)
                    else Result.success(CatalogPage(emptyList(), 0, page, false))
                }
                response.code() == 404 -> Result.success(CatalogPage(emptyList(), 0, page, false))
                else -> Result.failure(Exception(response.errorBody()?.string() ?: "Catalog failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
