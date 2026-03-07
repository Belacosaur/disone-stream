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

    suspend fun signInWithWallet(sender: ActivityResultSender): Result<Unit> {
        // MWA connect/transact use Activity Result API - must run on Main thread
        val connectResult = withContext(Dispatchers.Main.immediate) {
            walletManager.connect(sender)
        }
        val address = connectResult.getOrElse { return Result.failure(it) }
        val nonceResponse = withContext(Dispatchers.IO) {
            authApi.getNonce(address)
        }
        if (!nonceResponse.isSuccessful) {
            return Result.failure(Exception("Failed to get nonce"))
        }
        val nonce = nonceResponse.body()?.nonce ?: return Result.failure(Exception("No nonce"))
        val message = "Sign in to Disone: $nonce"
        val signResult = withContext(Dispatchers.Main.immediate) {
            walletManager.signMessage(sender, message)
        }
        return signResult.fold(
            onSuccess = { sig ->
                authRepository.signIn(
                    wallet = sig.address,
                    signature = sig.signature,
                    nonce = nonce
                )
            },
            onFailure = { Result.failure(it) }
        )
    }
}
