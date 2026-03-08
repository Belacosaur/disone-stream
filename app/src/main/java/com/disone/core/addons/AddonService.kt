package com.disone.core.addons

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_PARALLEL_ADDON_REQUESTS = 3
private const val REQUEST_TIMEOUT_SEC = 20

@Singleton
class AddonService @Inject constructor(
    private val client: OkHttpClient
) {

    private val addonClient = client.newBuilder()
        .connectTimeout(REQUEST_TIMEOUT_SEC.toLong(), TimeUnit.SECONDS)
        .readTimeout(REQUEST_TIMEOUT_SEC.toLong(), TimeUnit.SECONDS)
        .writeTimeout(REQUEST_TIMEOUT_SEC.toLong(), TimeUnit.SECONDS)
        .build()

    private val requestSemaphore = Semaphore(MAX_PARALLEL_ADDON_REQUESTS)

    suspend fun fetchManifest(addonBaseUrl: String): Result<String> = withContext(Dispatchers.IO) {
        requestSemaphore.withPermit {
            fetchJson(normalizeAddonUrl(addonBaseUrl) + "manifest.json")
        }
    }

    suspend fun fetchCatalog(
        addonBaseUrl: String,
        type: String,
        id: String,
        extra: String? = null,
        extraParams: Map<String, String>? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        requestSemaphore.withPermit {
            // Stremio protocol: extraArgs is "key=value" where only values are URL-encoded (e.g. "search=game%20of%20thrones&skip=100")
            val encodedExtra = when {
                extraParams != null && extraParams.isNotEmpty() -> {
                    extraParams.entries.joinToString("&") { (k, v) ->
                        "$k=${URLEncoder.encode(v, StandardCharsets.UTF_8.name())}"
                    }
                }
                extra != null -> extra
                else -> null
            }
            val path = if (encodedExtra != null) {
                "catalog/$type/$id/$encodedExtra.json"
            } else {
                "catalog/$type/$id.json"
            }
            fetchJson(normalizeAddonUrl(addonBaseUrl) + path)
        }
    }

    suspend fun fetchMeta(addonBaseUrl: String, type: String, id: String): Result<String> =
        withContext(Dispatchers.IO) {
            requestSemaphore.withPermit {
                fetchJson(normalizeAddonUrl(addonBaseUrl) + "meta/$type/$id.json")
            }
        }

    suspend fun fetchStreams(addonBaseUrl: String, type: String, videoId: String): Result<String> =
        withContext(Dispatchers.IO) {
            requestSemaphore.withPermit {
                val pathId = encodePathId(videoId)
                fetchJson(normalizeAddonUrl(addonBaseUrl) + "stream/$type/$pathId.json")
            }
        }

    suspend fun fetchSubtitles(addonBaseUrl: String, type: String, videoId: String): Result<String> =
        withContext(Dispatchers.IO) {
            requestSemaphore.withPermit {
                val pathId = encodePathId(videoId)
                fetchJson(normalizeAddonUrl(addonBaseUrl) + "subtitles/$type/$pathId.json")
            }
        }

    /**
     * Downloads a subtitle file to a temp file. Use this instead of passing remote URLs to libVLC,
     * as subs5.strem.io may block libVLC's default User-Agent. Uses Stremio-like User-Agent.
     */
    suspend fun downloadSubtitleToFile(url: String): Result<File> = withContext(Dispatchers.IO) {
        requestSemaphore.withPermit {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Stremio/4.0 (Disone)")
                    .get()
                    .build()
                val response = addonClient.newCall(request).execute()
                when {
                    !response.isSuccessful -> Result.failure(
                        AddonNetworkException("HTTP ${response.code}: ${response.message}", response.code)
                    )
                    response.body == null -> Result.failure(
                        AddonNetworkException("Empty response", response.code)
                    )
                    else -> {
                        val ext = when {
                            url.contains(".vtt") -> ".vtt"
                            url.contains(".ass") -> ".ass"
                            url.contains(".ssa") -> ".ssa"
                            else -> ".srt"
                        }
                        val file = File.createTempFile("sub_", ext)
                        file.deleteOnExit()
                        response.body!!.byteStream().use { input ->
                            file.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        Result.success(file)
                    }
                }
            } catch (e: Exception) {
                Result.failure(
                    if (e is AddonNetworkException) e
                    else AddonNetworkException("Download failed: ${e.message}", -1, e)
                )
            }
        }
    }

    private fun encodePathId(videoId: String): String =
        if (videoId.all { it.isLetterOrDigit() || it == '-' || it == '_' }) videoId
        else java.net.URLEncoder.encode(videoId, "UTF-8")

    private fun normalizeAddonUrl(url: String): String {
        val trimmed = url.trim().removeSuffix("/")
        return if (trimmed.endsWith("/manifest.json")) {
            trimmed.removeSuffix("manifest.json")
        } else if (!trimmed.endsWith("/")) {
            "$trimmed/"
        } else {
            trimmed
        }
    }

    private fun fetchJson(urlString: String): Result<String> {
        return try {
            val request = Request.Builder()
                .url(urlString)
                .get()
                .build()
            val response = addonClient.newCall(request).execute()
            when {
                !response.isSuccessful -> Result.failure(
                    AddonNetworkException("HTTP ${response.code}: ${response.message}", response.code)
                )
                response.body == null -> Result.failure(
                    AddonNetworkException("Empty response", response.code)
                )
                else -> Result.success(response.body!!.string())
            }
        } catch (e: Exception) {
            Result.failure(
                if (e is AddonNetworkException) e
                else AddonNetworkException("Request failed: ${e.message}", -1, e)
            )
        }
    }
}

class AddonNetworkException(
    message: String,
    val code: Int = -1,
    cause: Throwable? = null
) : Exception(message, cause)
