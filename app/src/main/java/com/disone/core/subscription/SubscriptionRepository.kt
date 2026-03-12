package com.disone.core.subscription

import com.disone.core.api.SubscriptionApi
import com.disone.core.auth.AuthRepository
import com.disone.core.models.CreateTransactionRequest
import com.disone.core.models.PurchaseRequest
import com.disone.core.models.SubmitSignedTransactionRequest
import com.disone.core.models.PurchaseResponse
import com.disone.core.models.SubscriptionPackage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubscriptionRepository @Inject constructor(
    private val subscriptionApi: SubscriptionApi,
    private val authRepository: AuthRepository
) {

    suspend fun getBlockhash(): Result<String> {
        return try {
            val response = subscriptionApi.getBlockhash()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.blockhash.isNotBlank()) {
                    Result.success(body.blockhash)
                } else {
                    Result.failure(Exception("Empty blockhash response"))
                }
            } else {
                val errBody = response.errorBody()?.string()
                val msg = try {
                    val obj = com.google.gson.Gson().fromJson(errBody, com.google.gson.JsonObject::class.java)
                    obj?.get("error")?.asString ?: errBody ?: "Failed to fetch blockhash"
                } catch (_: Exception) {
                    errBody ?: "Failed to fetch blockhash"
                }
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    data class CreateTransactionResult(val txBytes: ByteArray, val lastValidBlockHeight: Long?)

    /** Fetch server-built transaction (base64). Returns serialized tx bytes and slot for wallet to sign. */
    suspend fun createTransaction(packageKey: String, payerPublicKey: String): Result<CreateTransactionResult> {
        val auth = authRepository.getAuthHeader() ?: return Result.failure(Exception("Not authenticated"))
        return try {
            val response = subscriptionApi.createTransaction(
                auth,
                CreateTransactionRequest(packageKey = packageKey, payerPublicKey = payerPublicKey)
            )
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.transaction.isNotBlank()) {
                    val bytes = android.util.Base64.decode(body.transaction, android.util.Base64.DEFAULT)
                    Result.success(CreateTransactionResult(bytes, body.lastValidBlockHeight))
                } else {
                    Result.failure(Exception("Empty transaction response"))
                }
            } else {
                val errBody = response.errorBody()?.string()
                val errObj = try {
                    com.google.gson.Gson().fromJson(errBody, com.google.gson.JsonObject::class.java)
                } catch (_: Exception) { null }
                val msg = errObj?.get("error")?.asString ?: errObj?.get("details")?.asString ?: errBody ?: "Failed to create transaction"
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun submitSignedTransaction(signedTxBase64: String): Result<String> {
        val auth = authRepository.getAuthHeader() ?: return Result.failure(Exception("Not authenticated"))
        return try {
            val response = subscriptionApi.submitSignedTransaction(
                auth,
                SubmitSignedTransactionRequest(signedTransaction = signedTxBase64)
            )
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.signature.isNotBlank()) {
                    Result.success(body.signature)
                } else {
                    Result.failure(Exception("Empty signature response"))
                }
            } else {
                val errBody = response.errorBody()?.string()
                val errObj = try {
                    com.google.gson.Gson().fromJson(errBody, com.google.gson.JsonObject::class.java)
                } catch (_: Exception) { null }
                val msg = errObj?.get("error")?.asString ?: errObj?.get("details")?.asString ?: errBody ?: "Failed to submit transaction"
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPackages(): Result<SubscriptionPackagesResult> {
        return try {
            val response = subscriptionApi.getPackages()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    Result.success(SubscriptionPackagesResult(body.packages, body.recipientAddress))
                } else {
                    Result.failure(Exception("Empty response"))
                }
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to fetch packages"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    data class SubscriptionPackagesResult(
        val packages: List<SubscriptionPackage>,
        val recipientAddress: String?
    )

    suspend fun purchase(txSignature: String, packageKey: String): Result<PurchaseResponse> {
        val auth = authRepository.getAuthHeader() ?: return Result.failure(Exception("Not authenticated"))
        return try {
            val response = subscriptionApi.purchase(
                auth,
                PurchaseRequest(
                    txSignature = txSignature,
                    packageKey = packageKey
                )
            )
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    Result.success(body)
                } else {
                    Result.failure(Exception("Empty response"))
                }
            } else {
                val errBody = response.errorBody()?.string()
                val errMsg = try {
                    val obj = com.google.gson.Gson().fromJson(errBody, com.google.gson.JsonObject::class.java)
                    val main = obj?.get("error")?.asString ?: "Purchase failed"
                    val details = obj?.get("details")?.asString
                    if (!details.isNullOrBlank()) "$main: $details" else main
                } catch (_: Exception) {
                    errBody ?: "Purchase failed"
                }
                Result.failure(Exception(errMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
