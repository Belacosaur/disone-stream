package com.disone.ui.screens

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.disone.core.auth.AuthRepository
import com.disone.core.auth.AuthState
import com.disone.core.auth.AuthUseCase
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class WalletConnectState {
    object Idle : WalletConnectState()
    object Loading : WalletConnectState()
    data class Connected(val wallet: String, val plan: String) : WalletConnectState()
    data class Error(val message: String) : WalletConnectState()
}

@HiltViewModel
class WalletConnectViewModel @Inject constructor(
    private val authUseCase: AuthUseCase,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow<WalletConnectState>(WalletConnectState.Idle)
    val state: StateFlow<WalletConnectState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.initialize()
        }
        viewModelScope.launch {
            authRepository.authState.collect { updateFromAuth(it) }
        }
    }

    private fun updateFromAuth(auth: AuthState) {
        when (auth) {
            is AuthState.Authenticated -> _state.value = WalletConnectState.Connected(auth.wallet, auth.plan)
            else -> if (_state.value !is WalletConnectState.Loading) _state.value = WalletConnectState.Idle
        }
    }

    fun connectWallet(sender: ActivityResultSender) {
        viewModelScope.launch {
            _state.value = WalletConnectState.Loading
            val result = authUseCase.signInWithWallet(sender)
            result.fold(
                onSuccess = { /* authState will update from flow */ },
                onFailure = { _state.value = WalletConnectState.Error(it.message ?: "Auth failed") }
            )
        }
    }

    fun setError(message: String) {
        _state.value = WalletConnectState.Error(message)
    }

    fun handleActivityResult(resultCode: Int, data: android.content.Intent?) {
        if (resultCode != Activity.RESULT_OK) {
            _state.value = WalletConnectState.Error("Wallet connection cancelled")
        }
    }

    fun useOfflineMode() {
        viewModelScope.launch {
            // Ensure addons (Cinemeta, Torrentio) are installed before Discovery loads
            authRepository.initialize()
            _state.value = WalletConnectState.Connected(
                wallet = "offline",
                plan = "P2P"
            )
        }
    }
}
