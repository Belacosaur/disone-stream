package com.disone.core.subscription

import android.util.Log
import com.disone.core.auth.AuthRepository
import com.disone.core.models.SubscriptionPackage
import com.disone.core.wallet.WalletManager
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
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
     * Purchase a package: server builds tx, wallet signs and sends, then API confirms.
     * Uses Solana Pay pattern - backend controls transaction construction.
     */
    suspend fun purchase(
        activityResultSender: ActivityResultSender,
        packageItem: SubscriptionPackage
    ): Result<PurchaseConfirmation> {
        val sendResult = walletManager.signAndSendPrebuiltTransaction(
            activityResultSender
        ) { payerAddress ->
            subscriptionRepository.createTransaction(packageItem.packageKey, payerAddress)
                .getOrElse { throw it }
        }
        val txSignature = sendResult.getOrElse {
            Log.e("SubscriptionUseCase", "sendSol failed", it)
            return Result.failure(it)
        }
        Log.i("SubscriptionUseCase", "sendSol ok, calling API purchase tx=$txSignature")
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
