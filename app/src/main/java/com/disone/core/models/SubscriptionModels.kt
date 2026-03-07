package com.disone.core.models

import com.google.gson.annotations.SerializedName

data class SubscriptionPackage(
    val id: String,
    @SerializedName("package_key") val packageKey: String,
    val name: String,
    val description: String?,
    @SerializedName("amount_sol") val amountSol: String,
    @SerializedName("duration_months") val durationMonths: Int
)

data class BlockhashResponse(
    val blockhash: String
)

data class CreateTransactionRequest(
    @SerializedName("package_key") val packageKey: String,
    @SerializedName("payer_public_key") val payerPublicKey: String
)

data class CreateTransactionResponse(
    val transaction: String
)

data class SubscriptionPackagesResponse(
    val packages: List<SubscriptionPackage>,
    @SerializedName("recipient_address") val recipientAddress: String?
)

data class PurchaseRequest(
    val txSignature: String,
    val packageKey: String
)

data class PurchaseResponse(
    val success: Boolean,
    val plan: String,
    val packageName: String? = null,
    val packageKey: String? = null,
    val amountSol: String? = null,
    val txSignature: String? = null,
    val token: String,
    @SerializedName("expires_in") val expiresIn: Int,
    @SerializedName("expires_at") val expiresAt: String
)
