package com.disone.core.auth

import com.disone.core.api.AuthApi
import com.disone.core.wallet.WalletManager
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthUseCase @Inject constructor(
    private val authApi: AuthApi,
    private val walletManager: WalletManager,
    private val authRepository: AuthRepository
) {

    suspend fun signInWithWallet(sender: ActivityResultSender): Result<Unit> {
        return try {
            warmUpNetwork()
            val nonce = withContext(Dispatchers.IO) {
                fetchNonceWithRetry(maxAttempts = 5)
            }
            val statement = "Sign in to Disone: $nonce"
            val signResult = withContext(Dispatchers.Main.immediate) {
                if (walletManager.hasMwAuthToken()) {
                    walletManager.signInOnly(sender, nonce)
                } else {
                    // First-time: signMessage does connect+sign in ONE Phantom session (no token needed)
                    walletManager.signMessage(sender, statement)
                }
            }
            signResult.fold(
                onSuccess = { sig ->
                    withContext(Dispatchers.IO) {
                        delay(800)
                        signInWithRetry(sig.address, sig.signature, nonce)
                    }
                },
                onFailure = { Result.failure(it) }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun warmUpNetwork() {
        withContext(Dispatchers.IO) {
            try { authApi.getNonce(null) } catch (_: Exception) { }
        }
    }

    private suspend fun fetchNonceWithRetry(attempt: Int = 1, maxAttempts: Int = 5): String {
        return try {
            val nonceResponse = authApi.getNonce(null)
            if (nonceResponse.isSuccessful) {
                nonceResponse.body()?.nonce ?: throw Exception("No nonce in response")
            } else {
                val errBody = nonceResponse.errorBody()?.string()
                throw IOException("Failed to get nonce: HTTP ${nonceResponse.code()}")
            }
        } catch (e: Exception) {
            if (attempt < maxAttempts) {
                delay(500L * attempt)
                fetchNonceWithRetry(attempt + 1, maxAttempts)
            } else {
                throw e
            }
        }
    }

    private suspend fun signInWithRetry(wallet: String, signature: String, nonce: String, attempt: Int = 1): Result<Unit> {
        val result = authRepository.signIn(wallet, signature, nonce)
        return when {
            result.isSuccess -> result
            attempt < 5 -> {
                val isConnectionError = result.exceptionOrNull()?.message?.let { msg ->
                    msg.contains("connection", ignoreCase = true) ||
                        msg.contains("abort", ignoreCase = true) ||
                        msg.contains("reset", ignoreCase = true) ||
                        msg.contains("reach", ignoreCase = true) ||
                        msg.contains("unable to resolve", ignoreCase = true)
                } == true
                val delayMs = if (isConnectionError) 1500L else 600L
                delay(delayMs)
                signInWithRetry(wallet, signature, nonce, attempt + 1)
            }
            else -> result
        }
    }
}
