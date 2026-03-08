package com.disone.core.auth

import com.disone.core.api.AuthApi
import com.disone.core.wallet.WalletManager
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthUseCase @Inject constructor(
    private val authApi: AuthApi,
    private val walletManager: WalletManager,
    private val authRepository: AuthRepository
) {

    /**
     * Sign in with wallet using a single approval flow. Connect and sign are combined into one
     * wallet interaction to avoid issues with wallets (e.g. Seker) that get stuck when showing
     * connect and sign as separate screens.
     */
    suspend fun signInWithWallet(sender: ActivityResultSender): Result<Unit> {
        return try {
            val signResult = withContext(Dispatchers.Main.immediate) {
                walletManager.connectAndSign(sender) { address ->
                    withContext(Dispatchers.IO) {
                        val nonceResponse = authApi.getNonce(address)
                        if (!nonceResponse.isSuccessful) {
                            throw Exception("Failed to get nonce")
                        }
                        nonceResponse.body()?.nonce ?: throw Exception("No nonce")
                    }
                }
            }
            signResult.fold(
                onSuccess = { sig ->
                    val nonce = sig.message.removePrefix("Sign in to Disone: ")
                    withContext(Dispatchers.IO) {
                        authRepository.signIn(
                            wallet = sig.address,
                            signature = sig.signature,
                            nonce = nonce
                        )
                    }
                },
                onFailure = { Result.failure(it) }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
