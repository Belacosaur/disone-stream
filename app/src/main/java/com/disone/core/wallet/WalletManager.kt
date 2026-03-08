package com.disone.core.wallet

import android.net.Uri
import android.util.Base64
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.Solana
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import com.solana.publickey.SolanaPublicKey
import com.solana.transaction.Message
import com.solana.transaction.Transaction
import android.util.Log
import java.util.concurrent.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WalletManager @Inject constructor() {

    private val identity = ConnectionIdentity(
        identityUri = Uri.parse("https://disone.stream"),
        iconUri = Uri.parse("favicon.ico"),
        identityName = "Disone"
    )

    private val walletAdapter = MobileWalletAdapter(connectionIdentity = identity).apply {
        blockchain = Solana.Mainnet
    }

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

    /**
     * Connect and sign in a single wallet interaction. Some wallets (e.g. Seker) get stuck
     * when asked to approve connect and sign as separate intents. This combines both so the
     * user only sees one approval screen.
     */
    suspend fun connectAndSign(
        activityResultSender: ActivityResultSender,
        getNonce: suspend (String) -> String
    ): Result<WalletSignResult> {
        var capturedSignResult: WalletSignResult? = null
        return when (val result = walletAdapter.transact(activityResultSender) { authResult ->
            val accounts = authResult.accounts
            if (accounts.isEmpty()) return@transact
            val publicKey = accounts.first().publicKey
            val address = publicKey.toBase58()
            val nonce = getNonce(address)
            val message = "Sign in to Disone: $nonce"
            val messageBytes = message.toByteArray(Charsets.UTF_8)
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

    /**
     * Sign and send a pre-built transaction from the server (Solana Pay pattern).
     * The server builds the tx; this only gets payer from wallet and invokes getTransaction.
     * Returns transaction signature on success.
     */
    suspend fun signAndSendPrebuiltTransaction(
        activityResultSender: ActivityResultSender,
        getTransaction: suspend (String) -> ByteArray
    ): Result<String> {
        var capturedSignature: String? = null
        return when (val result = walletAdapter.transact(activityResultSender) { authResult ->
            val accounts = authResult.accounts
            if (accounts.isEmpty()) return@transact
            val payerAddress = accounts.first().publicKey.toBase58()
            val txBytes = getTransaction(payerAddress)
            val sendResult = signAndSendTransactions(arrayOf(txBytes))
            val sigBytes = sendResult?.signatures?.firstOrNull()
            if (sigBytes != null) {
                capturedSignature = sigBytes.toBase58()
            }
        }) {
            is TransactionResult.Success -> {
                capturedSignature?.let {
                    Log.i("WalletManager", "signAndSendPrebuilt success tx=${it.take(16)}...")
                    Result.success(it)
                } ?: run {
                    Log.e("WalletManager", "signAndSendPrebuilt Success but no signature captured")
                    Result.failure(Exception("No signature returned from wallet"))
                }
            }
            is TransactionResult.Failure -> {
                val cause = result.e
                val msg = when {
                    cause?.cause is CancellationException ->
                        "Wallet closed before completing. Stay in the wallet app until the transaction confirms, then return to Disone."
                    cause?.message?.isNotBlank() == true -> cause.message!!
                    cause?.cause?.message?.isNotBlank() == true -> cause.cause!!.message!!
                    else -> "Transaction failed. Ensure you have enough SOL and try again."
                }
                Log.e("WalletManager", "signAndSendPrebuilt Failure: $msg", result.e)
                Result.failure(Exception(msg, result.e))
            }
            is TransactionResult.NoWalletFound -> {
                Log.e("WalletManager", "signAndSendPrebuilt NoWalletFound: ${result.message}")
                Result.failure(Exception(result.message ?: "No MWA wallet found"))
            }
        }
    }

    /**
     * Send SOL to recipient. Returns transaction signature on success.
     * Lamports: 1 SOL = 1_000_000_000 lamports.
     * @param getBlockhash Optional. When provided, fetches blockhash inside the wallet callback
     *   (fresher, reduces blockhash expiry). When null, uses blockhash param or public RPC.
     */
    suspend fun sendSol(
        activityResultSender: ActivityResultSender,
        recipientAddress: String,
        lamports: Long,
        blockhash: String? = null,
        getBlockhash: (suspend () -> String)? = null
    ): Result<String> {
        var capturedSignature: String? = null
        val fallbackHash: String? = if (getBlockhash == null) {
            blockhash ?: runCatching { BlockhashFetcher.getLatestBlockhash() }.getOrElse { return Result.failure(it) }
        } else null

        return when (val result = walletAdapter.transact(activityResultSender) { authResult ->
            val accounts = authResult.accounts
            if (accounts.isEmpty()) return@transact
            val hash = getBlockhash?.invoke() ?: fallbackHash!!
            val fromKey = SolanaPublicKey(accounts.first().publicKey)
            val toKey = SolanaPublicKey(recipientAddress.decodeBase58())

            val transferIx = createTransferInstruction(fromKey, toKey, lamports)
            val blockhashKey = SolanaPublicKey(hash.decodeBase58())
            val transferTx = Transaction(
                Message.Builder()
                    .addInstruction(transferIx)
                    .setRecentBlockhash(blockhashKey)
                    .build()
            )

            val serialized = transferTx.serialize()
            val sendResult = signAndSendTransactions(arrayOf(serialized))
            val sigBytes = sendResult?.signatures?.firstOrNull()
            if (sigBytes != null) {
                capturedSignature = sigBytes.toBase58()
            }
        }) {
            is TransactionResult.Success -> {
                capturedSignature?.let {
                    Log.i("WalletManager", "sendSol success tx=${it.take(16)}...")
                    Result.success(it)
                } ?: run {
                    Log.e("WalletManager", "sendSol Success but no signature captured")
                    Result.failure(Exception("No signature returned from wallet"))
                }
            }
            is TransactionResult.Failure -> {
                val cause = result.e
                val msg = when {
                    cause?.cause is CancellationException ->
                        "Wallet closed before completing. Stay in the wallet app until the transaction confirms, then return to Disone."
                    cause?.message?.isNotBlank() == true -> cause.message!!
                    cause?.cause?.message?.isNotBlank() == true -> cause.cause!!.message!!
                    else -> "Transaction failed. Ensure you have enough SOL and try again."
                }
                Log.e("WalletManager", "sendSol Failure: $msg", result.e)
                Result.failure(Exception(msg, result.e))
            }
            is TransactionResult.NoWalletFound -> {
                Log.e("WalletManager", "sendSol NoWalletFound: ${result.message}")
                Result.failure(Exception(result.message ?: "No MWA wallet found"))
            }
        }
    }

    data class WalletSignResult(
        val address: String,
        val signature: String,
        val message: String
    )
}
