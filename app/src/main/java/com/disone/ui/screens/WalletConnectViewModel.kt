package com.disone.ui.screens

import android.app.Activity
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.disone.core.access.AccessRepository
import com.disone.core.auth.AuthRepository
import com.disone.core.auth.AuthState
import com.disone.core.auth.AuthUseCase
import com.disone.core.models.AccessCheckResponse
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class WalletConnectState {
    object Initializing : WalletConnectState()
    object Idle : WalletConnectState()
    object Loading : WalletConnectState()
    data class Connected(val wallet: String, val plan: String) : WalletConnectState()
    data class Error(val message: String) : WalletConnectState()
}

@HiltViewModel
class WalletConnectViewModel @Inject constructor(
    private val authUseCase: AuthUseCase,
    private val authRepository: AuthRepository,
    private val accessRepository: AccessRepository
) : ViewModel() {

    private val _state = MutableStateFlow<WalletConnectState>(WalletConnectState.Initializing)
    val state: StateFlow<WalletConnectState> = _state.asStateFlow()

    private val _accessStatus = MutableStateFlow<AccessCheckResponse?>(null)
    val accessStatus: StateFlow<AccessCheckResponse?> = _accessStatus.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.authState.collect { updateFromAuth(it) }
        }
        viewModelScope.launch {
            authUseCase.warmUpNetwork()
        }
    }

    private fun updateFromAuth(auth: AuthState) {
        when (auth) {
            is AuthState.Authenticated -> {
                _state.value = WalletConnectState.Connected(auth.wallet, auth.plan)
                if (auth.plan == "P2P") {
                    viewModelScope.launch {
                        accessRepository.checkAccess().onSuccess { _accessStatus.value = it }
                            .onFailure { _accessStatus.value = null }
                    }
                } else {
                    _accessStatus.value = null
                }
            }
            else -> {
                _accessStatus.value = null
                if (_state.value !is WalletConnectState.Loading) {
                    _state.value = WalletConnectState.Idle
                }
            }
        }
    }

    fun connectWallet(sender: ActivityResultSender) {
        viewModelScope.launch {
            _state.value = WalletConnectState.Loading
            try {
                val result = authUseCase.signInWithWallet(sender)
                result.fold(
                    onSuccess = { /* authState will update from flow */ },
                    onFailure = {
                        Log.e("WalletConnect", "Auth failed", it)
                        _state.value = WalletConnectState.Error(formatConnectionError(it))
                    }
                )
            } catch (e: Exception) {
                Log.e("WalletConnect", "Auth exception", e)
                _state.value = WalletConnectState.Error(formatConnectionError(e))
            }
        }
    }

    private fun formatConnectionError(e: Throwable): String {
        val raw = (e.cause?.message ?: e.message) ?: return "Connection failed. Please try again."
        val hint = when {
            raw.contains("Unable to resolve host", ignoreCase = true) ||
            raw.contains("No address associated with hostname", ignoreCase = true) ->
                "Disone servers couldn't be reached. Try mobile data or a different network. $raw"
            raw.contains("Failed to get nonce", ignoreCase = true) ||
            raw.contains("Auth failed", ignoreCase = true) ->
                raw
            raw.contains("failed to connect", ignoreCase = true) ||
            raw.contains("Connection refused", ignoreCase = true) ||
            raw.contains("Connection reset", ignoreCase = true) ->
                "Couldn't connect to Disone. $raw"
            raw.contains("timeout", ignoreCase = true) ||
            raw.contains("timed out", ignoreCase = true) ->
                "Request timed out. $raw"
            raw.contains("Unable to connect to websocket", ignoreCase = true) ->
                "Wallet connection failed. Disable battery saver for Disone and your wallet app, then try again."
            raw.contains("SSL", ignoreCase = true) ||
            raw.contains("certificate", ignoreCase = true) ->
                "Secure connection failed. $raw"
            else -> raw
        }
        return hint
    }

    fun setError(message: String) {
        _state.value = WalletConnectState.Error(message)
    }

    fun handleActivityResult(resultCode: Int, data: android.content.Intent?) {
        if (resultCode != Activity.RESULT_OK) {
            _state.value = WalletConnectState.Error("Wallet connection cancelled")
        }
    }
}
