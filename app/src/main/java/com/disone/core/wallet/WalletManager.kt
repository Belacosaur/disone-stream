package com.disone.core.wallet

import android.net.Uri
import android.util.Base64
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.Solana
import com.solana.mobilewalletadapter.clientlib.TransactionParams
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import com.solana.mobilewalletadapter.common.signin.SignInWithSolana
import android.util.Log
import com.disone.core.storage.SecureTokenStorage
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WalletManager @Inject constructor(
    private val tokenStorage: SecureTokenStorage
) {

    private val identity = ConnectionIdentity(
        identityUri = Uri.parse("https://disone.stream"),
        iconUri = Uri.parse("favicon.ico"),
        identityName = "Disone"
    )

    private val walletAdapter = MobileWalletAdapter(connectionIdentity = identity).apply {
        blockchain = Solana.Mainnet  // mainnet-beta cluster
    }

    /** True if we have a cached MWA auth token. Required before purchase so Phantom shows tx (not connect). */
    fun hasMwAuthToken(): Boolean = tokenStorage.getMwAuthToken() != null

    suspend fun connect(activityResultSender: ActivityResultSender): Result<String> {
        return when (val result = walletAdapter.connect(activityResultSender)) {
            is TransactionResult.Success -> {
                result.authResult.authToken?.let { tokenStorage.saveMwAuthToken(it) }
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
     * Sign in with wallet. Requires cached MWA token from a previous connect.
     * One Phantom open: Connect (or skip with token) + Approve.
     */
    suspend fun signInOnly(
        activityResultSender: ActivityResultSender,
        nonce: String
    ): Result<WalletSignResult> {
        val mwaToken = tokenStorage.getMwAuthToken()
            ?: return Result.failure(Exception("Wallet session required. Open your wallet, connect to Disone there, then return."))
        walletAdapter.authToken = mwaToken

        val statement = "Sign in to Disone: $nonce"
        val payload = SignInWithSolana.Payload("disone.stream", statement)
        var capturedSignResult: WalletSignResult? = null

        val siwsResult = walletAdapter.transact(activityResultSender, payload) { authResult ->
            authResult.authToken?.let { tokenStorage.saveMwAuthToken(it) }
            val sr = authResult.signInResult
            if (sr != null) {
                capturedSignResult = WalletSignResult(
                    address = sr.publicKey.toBase58(),
                    signature = Base64.encodeToString(sr.signature, Base64.NO_WRAP),
                    message = String(sr.signedMessage, Charsets.UTF_8)
                )
            } else {
                val accounts = authResult.accounts
                if (accounts.isNotEmpty()) {
                    val publicKey = accounts.first().publicKey
                    val messageBytes = statement.toByteArray(Charsets.UTF_8)
                    val signResponse = signMessagesDetached(arrayOf(messageBytes), arrayOf(publicKey))
                    val sigBytes = signResponse?.messages?.firstOrNull()?.signatures?.firstOrNull()
                    if (sigBytes != null) {
                        capturedSignResult = WalletSignResult(
                            address = publicKey.toBase58(),
                            signature = Base64.encodeToString(sigBytes, Base64.NO_WRAP),
                            message = statement
                        )
                    }
                }
            }
        }
        return when (siwsResult) {
            is TransactionResult.Success -> {
                capturedSignResult?.let { Result.success(it) }
                    ?: Result.failure(Exception("No signature from wallet"))
            }
            is TransactionResult.Failure -> Result.failure(Exception(siwsResult.e?.message ?: "Wallet error", siwsResult.e))
            is TransactionResult.NoWalletFound -> Result.failure(Exception(siwsResult.message ?: "No MWA wallet found"))
        }
    }

    suspend fun signMessage(
        activityResultSender: ActivityResultSender,
        message: String
    ): Result<WalletSignResult> {
        val messageBytes = message.toByteArray(Charsets.UTF_8)
        var capturedSignResult: WalletSignResult? = null
        return when (val result = walletAdapter.transact(activityResultSender) { authResult ->
            authResult.authToken?.let { tokenStorage.saveMwAuthToken(it) }
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
     * Sign pre-built transaction. Tries signAndSendTransactions first (Phantom expects this), then signTransactions fallback.
     *
     * CRITICAL: Inside transact{} we may ONLY call signTransactions/signAndSendTransactions/signMessages.
     * No network calls, suspend calls, or delays. Phantom expects the signing request immediately
     * after session start – any network call before signing causes Phantom to close without showing the tx.
     *
     * @return SignThenSubmitResult.Signature when signAndSendTransactions succeeded (wallet sent tx)
     *         SignThenSubmitResult.SignedBytes when signTransactions succeeded (app must submit)
     */
    sealed class SignThenSubmitResult {
        data class SignedBytes(val bytes: ByteArray) : SignThenSubmitResult()
        data class Signature(val signature: String) : SignThenSubmitResult()
    }

    suspend fun signPrebuiltTransaction(
        activityResultSender: ActivityResultSender,
        txBytes: ByteArray,
        minContextSlot: Int? = null
    ): Result<SignThenSubmitResult> {
        if (txBytes.isEmpty()) {
            return Result.failure(Exception("Empty transaction"))
        }
        tokenStorage.getMwAuthToken()?.let { walletAdapter.authToken = it }
        val txParams = TransactionParams(
            minContextSlot = minContextSlot,
            commitment = null,
            skipPreflight = true,
            maxRetries = null,
            waitForCommitmentToSendNextTransaction = null
        )
        val tx = txBytes.copyOf()
        val payloads: Array<ByteArray> = arrayOf(tx)
        var capturedSignedBytes: ByteArray? = null
        var capturedSignature: String? = null
        Log.d("WalletManager", "signPrebuiltTransaction: calling transact (OPENS PHANTOM)")
        val result = walletAdapter.transact(activityResultSender) { authResult ->
            authResult.authToken?.let { tokenStorage.saveMwAuthToken(it) }
            val accounts = authResult.accounts
            if (accounts.isEmpty()) return@transact
            try {
                val sendResult = signAndSendTransactions(payloads, txParams)
                val signature = sendResult.signatures.first()
                Log.d("WalletManager", "signPrebuiltTransaction: signAndSendTransactions ok")
                capturedSignature = signature.toBase58()
            } catch (e: Throwable) {
                Log.d("WalletManager", "signAndSendTransactions not supported, trying signTransactions: ${e.message}")
                try {
                    val signOnly = signTransactions(payloads)
                    val signedPayload = signOnly.signedPayloads.firstOrNull()
                    if (signedPayload != null && signedPayload.isNotEmpty()) {
                        Log.d("WalletManager", "signPrebuiltTransaction: signTransactions ok")
                        capturedSignedBytes = signedPayload
                    }
                } catch (_: Throwable) {
                    // signTransactions not supported
                }
            }
        }
        return when (result) {
            is TransactionResult.Success -> {
                when {
                    capturedSignedBytes != null -> {
                        Log.d("WalletManager", "signPrebuiltTransaction success (signed bytes, app submits)")
                        Result.success(SignThenSubmitResult.SignedBytes(capturedSignedBytes!!))
                    }
                    capturedSignature != null -> {
                        Log.d("WalletManager", "signPrebuiltTransaction success (signature, wallet sent)")
                        Result.success(SignThenSubmitResult.Signature(capturedSignature!!))
                    }
                    else -> {
                        Log.e("WalletManager", "signPrebuiltTransaction: no signed payload or signature")
                        Result.failure(Exception("Wallet did not return signed transaction or signature."))
                    }
                }
            }
            is TransactionResult.Failure -> {
                val cause = result.e
                val msg = when {
                    cause?.cause is CancellationException ->
                        "Wallet closed before completing. Stay in the wallet app until you approve the transaction, then return to Disone."
                    cause is TimeoutException || cause?.cause is TimeoutException ->
                        "Wallet didn't respond in time. Use the two-tap flow: tap a plan first, wait for it to prepare, then tap Confirm Payment."
                    cause?.message?.isNotBlank() == true -> cause.message!!
                    cause?.cause?.message?.isNotBlank() == true -> cause.cause!!.message!!
                    else -> "Transaction signing failed. Ensure you have enough SOL and try again."
                }
                Log.e("WalletManager", "signPrebuiltTransaction Failure: $msg", result.e)
                Result.failure(Exception(msg, result.e))
            }
            is TransactionResult.NoWalletFound -> {
                Log.e("WalletManager", "signPrebuiltTransaction NoWalletFound: ${result.message}")
                Result.failure(Exception(result.message ?: "No MWA wallet found"))
            }
        }
    }

    /** @deprecated Use signPrebuiltTransaction; kept for backward compatibility. */
    suspend fun signAndSendPrebuiltTransaction(
        activityResultSender: ActivityResultSender,
        txBytes: ByteArray,
        minContextSlot: Int? = null
    ): Result<String> {
        if (txBytes.isEmpty()) {
            return Result.failure(Exception("Empty transaction"))
        }
        tokenStorage.getMwAuthToken()?.let { walletAdapter.authToken = it }
        val txParams = TransactionParams(
            minContextSlot = minContextSlot,
            commitment = null,
            skipPreflight = true,
            maxRetries = null,
            waitForCommitmentToSendNextTransaction = null
        )
        val tx = txBytes.copyOf()
        val payloads: Array<ByteArray> = arrayOf(tx)
        var capturedSignature: String? = null
        val result = walletAdapter.transact(activityResultSender) { authResult ->
            authResult.authToken?.let { tokenStorage.saveMwAuthToken(it) }
            val accounts = authResult.accounts
            if (accounts.isEmpty()) return@transact
            Log.d("WalletManager", "signAndSendPrebuilt: sending tx size=${txBytes.size} bytes (signAndSendTransactions)")
            val sendResult = signAndSendTransactions(payloads, txParams)
            val signature = sendResult.signatures.first()
            capturedSignature = signature.toBase58()
        }
        return when (result) {
            is TransactionResult.Success -> {
                capturedSignature?.let {
                    Log.d("WalletManager", "signAndSendPrebuilt success tx=${it.take(16)}...")
                    Result.success(it)
                } ?: run {
                    Log.e("WalletManager", "signAndSendPrebuilt: no signature captured")
                    Result.failure(Exception("No signature returned from wallet"))
                }
            }
            is TransactionResult.Failure -> {
                val cause = result.e
                val msg = when {
                    cause?.cause is CancellationException ->
                        "Wallet closed before completing. Stay in the wallet app until the transaction confirms, then return to Disone."
                    cause is TimeoutException || cause?.cause is TimeoutException ->
                        "Wallet didn't respond in time. Use the two-tap flow: tap a plan first, wait for it to prepare, then tap Confirm Payment."
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

    data class WalletSignResult(
        val address: String,
        val signature: String,
        val message: String
    )
}
