package com.disone.core.wallet

import android.net.Uri
import android.util.Base64
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WalletManager @Inject constructor() {

    private val identity = ConnectionIdentity(
        identityUri = Uri.parse("https://disone.app"),
        iconUri = Uri.parse("favicon.ico"),
        identityName = "Disone"
    )

    private val walletAdapter = MobileWalletAdapter(connectionIdentity = identity)

    suspend fun connect(activityResultSender: ActivityResultSender): Result<String> {
        return when (val result = walletAdapter.connect(activityResultSender)) {
            is TransactionResult.Success -> {
                val accounts = result.authResult.accounts
                if (accounts.isEmpty()) {
                    Result.failure(Exception("No accounts"))
                } else {
                    Result.success(accounts.first().publicKey.toBase58())
                }
            }
            is TransactionResult.Failure -> Result.failure(Exception(result.e?.message ?: "Wallet error", result.e))
            is TransactionResult.NoWalletFound -> Result.failure(Exception(result.message ?: "No MWA wallet found"))
        }
    }

    suspend fun signMessage(
        activityResultSender: ActivityResultSender,
        message: String
    ): Result<WalletSignResult> {
        val messageBytes = message.toByteArray(Charsets.UTF_8)
        var capturedSignResult: WalletSignResult? = null
        return when (val result = walletAdapter.transact(activityResultSender) { authResult ->
            val accounts = authResult.accounts
            if (accounts.isEmpty()) return@transact
            val publicKey = accounts.first().publicKey
            val address = publicKey.toBase58()
            val signResponse = signMessagesDetached(arrayOf(messageBytes), arrayOf(publicKey))
            val sigBytes = signResponse?.messages?.firstOrNull()?.signatures?.firstOrNull()
            if (sigBytes != null) {
                capturedSignResult = WalletSignResult(
                    address = address,
                    signature = Base64.encodeToString(sigBytes, Base64.NO_WRAP),
                    message = message
                )
            }
        }) {
            is TransactionResult.Success -> {
                capturedSignResult?.let { Result.success(it) }
                    ?: Result.failure(Exception("No signature"))
            }
            is TransactionResult.Failure -> Result.failure(Exception(result.e?.message ?: "Wallet error", result.e))
            is TransactionResult.NoWalletFound -> Result.failure(Exception(result.message ?: "No MWA wallet found"))
        }
    }

    data class WalletSignResult(
        val address: String,
        val signature: String,
        val message: String
    )
}
