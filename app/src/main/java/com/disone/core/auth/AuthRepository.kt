package com.disone.core.auth

import com.disone.core.addons.AddonRepository
import com.disone.core.api.AuthApi
import com.disone.core.api.StreamApi
import com.disone.core.models.AuthVerifyRequest
import com.disone.core.storage.SecureTokenStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.HttpURLConnection
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val streamApi: StreamApi,
    private val tokenStorage: SecureTokenStorage,
    private val addonRepository: AddonRepository
) {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    suspend fun initialize() {
        val token = tokenStorage.getToken()
        val plan = tokenStorage.getPlan()
        val wallet = tokenStorage.getWallet()
        if (token != null && wallet != null) {
            _authState.value = AuthState.Authenticated(wallet = wallet, plan = plan ?: "P2P", token = token)
            addonRepository.ensureDefaultAddons()
            addonRepository.syncFromServer()
        } else {
            _authState.value = AuthState.Unauthenticated
            addonRepository.ensureDefaultAddons()
        }
    }

    suspend fun signIn(wallet: String, signature: String, nonce: String): Result<Unit> {
        return try {
            val response = authApi.verify(AuthVerifyRequest(wallet = wallet, signature = signature, nonce = nonce))
            when {
                response.isSuccessful -> {
                    val body = response.body()
                    if (body != null) {
                        tokenStorage.saveToken(body.token, body.expiresIn)
                        tokenStorage.savePlan(body.plan)
                        tokenStorage.saveWallet(wallet)
                        _authState.value = AuthState.Authenticated(
                            wallet = wallet,
                            plan = body.plan,
                            token = body.token
                        )
                        addonRepository.syncFromServer()
                        Result.success(Unit)
                    } else {
                        Result.failure(Exception("Empty response"))
                    }
                }
                response.code() == HttpURLConnection.HTTP_UNAUTHORIZED -> {
                    Result.failure(Exception("Invalid signature. Please try connecting again."))
                }
                else -> {
                    val errBody = response.errorBody()?.string()?.trim()
                    Result.failure(Exception(errBody ?: "Auth failed (HTTP ${response.code()})"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signOut() {
        tokenStorage.clear()
        _authState.value = AuthState.Unauthenticated
    }

    /** Update auth state after subscription purchase (new token + PREMIUM plan).
     * Uses synchronous storage writes so the new token is visible before authState is emitted. */
    fun updateFromPurchase(token: String, expiresIn: Int, plan: String) {
        val wallet = tokenStorage.getWallet() ?: return
        tokenStorage.saveToken(token, expiresIn.toLong())
        tokenStorage.savePlan(plan)
        _authState.value = AuthState.Authenticated(wallet = wallet, plan = plan, token = token)
    }

    fun getAuthHeader(): String? {
        val token = tokenStorage.getToken()
        return if (token != null) "Bearer $token" else null
    }

    /** Current wallet address when authenticated. Used to pre-fetch transactions before opening wallet. */
    fun getCurrentWallet(): String? = tokenStorage.getWallet()
}

sealed class AuthState {
    object Unauthenticated : AuthState()
    data class Authenticated(val wallet: String, val plan: String, val token: String) : AuthState()
}
