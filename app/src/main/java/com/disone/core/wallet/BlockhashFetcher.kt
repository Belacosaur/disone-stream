package com.disone.core.wallet

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Fetches latest blockhash from Solana RPC. Uses OkHttp to avoid RPC lib dependency issues.
 */
object BlockhashFetcher {

    private const val RPC_URL = "https://api.mainnet-beta.solana.com"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun getLatestBlockhash(): String = withContext(Dispatchers.IO) {
        val body = """{"jsonrpc":"2.0","id":1,"method":"getLatestBlockhash","params":[{"commitment":"finalized"}]}"""

        val request = Request.Builder()
            .url(RPC_URL)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("Could not fetch blockhash (RPC ${response.code}). Try again in a moment.")
        }
        val json = JSONObject(response.body!!.string())
        if (json.has("error")) {
            val err = json.optJSONObject("error")
            val msg = err?.optString("message", "RPC error") ?: "RPC error"
            throw Exception("Blockhash error: $msg")
        }
        val result = json.getJSONObject("result")
        val value = result.getJSONObject("value")
        value.getString("blockhash")
    }
}
