package com.disone.core.subscription

import android.util.Log
import com.disone.core.auth.AuthRepository
import com.disone.core.models.SubscriptionPackage
import com.disone.core.wallet.WalletManager
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import javax.inject.Inject
import javax.inject.Singleton

/** Confirmation details to show after successful purchase. */
data class PurchaseConfirmation(
    val packageName: String,
    val amountSol: String,
    val txSignature: String
)

/** Thrown when tx succeeded on-chain but RPC hasn't indexed it yet. Call confirmBySignature to retry. */
class VerificationPendingException(
    message: String,
    val txSignature: String,
    val packageKey: String
) : Exception(message)

@Singleton
class SubscriptionUseCase @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
    private val walletManager: WalletManager,
    private val authRepository: AuthRepository
) {

    suspend fun getPackages(): Result<SubscriptionRepository.SubscriptionPackagesResult> =
        subscriptionRepository.getPackages()

    /**
     * Phase 1 only: Fetch transaction from backend. Call this BEFORE opening Phantom.
     * Returns tx bytes + lastValidBlockHeight. Do not call signPrebuiltTransaction until this completes.
     */
    suspend fun prepareTransaction(
        packageKey: String,
        payerPublicKey: String
    ): Result<SubscriptionRepository.CreateTransactionResult> {
        return withContext(Dispatchers.IO) {
            subscriptionRepository.createTransaction(packageKey, payerPublicKey)
        }
    }

    /**
     * Phase 2 only: Open wallet and sign the PRE-FETCHED transaction. No network calls inside transact{}.
     * Must be called only after prepareTransaction() has completed successfully.
     */
    suspend fun signAndCompletePurchase(
        activityResultSender: ActivityResultSender,
        packageItem: SubscriptionPackage,
        txResult: SubscriptionRepository.CreateTransactionResult
    ): Result<PurchaseConfirmation> {
        val txSignature = when (val signResult = walletManager.signPrebuiltTransaction(
            activityResultSender,
            txResult.txBytes,
            txResult.lastValidBlockHeight?.toInt()
        ).getOrElse { return Result.failure(it) }) {
            is WalletManager.SignThenSubmitResult.SignedBytes -> {
                val signedBase64 = android.util.Base64.encodeToString(signResult.bytes, android.util.Base64.NO_WRAP)
                subscriptionRepository.submitSignedTransaction(signedBase64).getOrElse {
                    Log.e("SubscriptionUseCase", "submitSignedTransaction failed", it)
                    return Result.failure(it)
                }
            }
            is WalletManager.SignThenSubmitResult.Signature -> signResult.signature
        }
        Log.d("SubscriptionUseCase", "tx signed, waiting 2s for tx to land then calling purchase tx=$txSignature")
        kotlinx.coroutines.delay(2000)
        val purchaseResult = subscriptionRepository.purchase(txSignature, packageItem.packageKey)
        val response = purchaseResult.getOrElse {
            Log.e("SubscriptionUseCase", "purchase API failed", it)
            if (it.message?.contains("Transaction not found") == true) {
                return Result.failure(VerificationPendingException(it.message!!, txSignature, packageItem.packageKey))
            }
            return Result.failure(it)
        }

        authRepository.updateFromPurchase(response.token, response.expiresIn, response.plan)
        val confirmation = PurchaseConfirmation(
            packageName = response.packageName ?: packageItem.name,
            amountSol = packageItem.amountSol,
            txSignature = response.txSignature ?: txSignature
        )
        return Result.success(confirmation)
    }

    /**
     * Purchase: two-phase flow to guarantee tx is fetched BEFORE Phantom opens.
     * Phantom requires the signing request immediately after session – any delay causes no tx UI.
     */
    suspend fun purchase(
        activityResultSender: ActivityResultSender,
        packageItem: SubscriptionPackage
    ): Result<PurchaseConfirmation> {
        val wallet = authRepository.getCurrentWallet()
            ?: return Result.failure(Exception("Not logged in. Sign in first."))

        if (!walletManager.hasMwAuthToken()) {
            return Result.failure(Exception(
                "Please connect your wallet first. Go to Account → Connect Wallet, approve there, then return to purchase."
            ))
        }

        // Phase 1: Fetch tx on IO. Must complete before we touch the wallet.
        val txResult = prepareTransaction(packageItem.packageKey, wallet).getOrElse {
            Log.e("SubscriptionUseCase", "createTransaction failed", it)
            return Result.failure(it)
        }
        yield() // Ensure phase 1 is fully committed before phase 2

        // Phase 2: Open Phantom on Main. Tx bytes are in memory – no network inside transact{}.
        return withContext(Dispatchers.Main.immediate) {
            signAndCompletePurchase(activityResultSender, packageItem, txResult)
        }
    }

    /** Retry verification for a tx that succeeded on-chain but wasn't indexed in time. */
    suspend fun confirmBySignature(txSignature: String, packageKey: String): Result<PurchaseConfirmation> {
        val purchaseResult = subscriptionRepository.purchase(txSignature, packageKey)
        val response = purchaseResult.getOrElse { return Result.failure(it) }
        authRepository.updateFromPurchase(response.token, response.expiresIn, response.plan)
        val confirmation = PurchaseConfirmation(
            packageName = response.packageName ?: packageKey,
            amountSol = response.amountSol ?: "",
            txSignature = response.txSignature ?: txSignature
        )
        return Result.success(confirmation)
    }
}
