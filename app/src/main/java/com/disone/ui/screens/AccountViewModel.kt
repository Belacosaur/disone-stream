package com.disone.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.disone.core.auth.AuthRepository
import com.disone.core.auth.AuthState
import com.disone.core.models.SubscriptionPackage
import com.disone.core.models.UserProfile
import com.disone.core.profile.ProfileRepository
import com.disone.core.subscription.PurchaseConfirmation
import com.disone.core.subscription.SubscriptionUseCase
import com.disone.core.subscription.VerificationPendingException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import javax.inject.Inject

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository,
    private val subscriptionUseCase: SubscriptionUseCase
) : ViewModel() {

    val authState: StateFlow<AuthState> = authRepository.authState

    private val _profile = MutableStateFlow<UserProfile?>(null)
    val profile: StateFlow<UserProfile?> = _profile.asStateFlow()

    private val _packages = MutableStateFlow<List<SubscriptionPackage>>(emptyList())
    val packages: StateFlow<List<SubscriptionPackage>> = _packages.asStateFlow()

    private val _recipientAddress = MutableStateFlow<String?>(null)
    val recipientAddress: StateFlow<String?> = _recipientAddress.asStateFlow()

    private val _packagesLoading = MutableStateFlow(false)
    val packagesLoading: StateFlow<Boolean> = _packagesLoading.asStateFlow()

    private val _purchaseInProgress = MutableStateFlow(false)
    val purchaseInProgress: StateFlow<Boolean> = _purchaseInProgress.asStateFlow()

    private val _pendingVerification = MutableStateFlow<Pair<String, String>?>(null)
    val pendingVerification: StateFlow<Pair<String, String>?> = _pendingVerification.asStateFlow()

    private val _purchaseConfirmation = MutableStateFlow<PurchaseConfirmation?>(null)
    val purchaseConfirmation: StateFlow<PurchaseConfirmation?> = _purchaseConfirmation.asStateFlow()

    init {
        viewModelScope.launch {
            authState.collectLatest { state ->
                when (state) {
                    is AuthState.Authenticated -> {
                        profileRepository.getUser()
                            .onSuccess { _profile.value = it }
                            .onFailure { /* Keep existing profile - don't clear on network/auth error */ }
                    }
                    else -> _profile.value = null
                }
            }
        }
    }

    fun refreshProfile() {
        viewModelScope.launch {
            profileRepository.getUser().onSuccess { _profile.value = it }
        }
    }

    fun updateUsername(username: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = profileRepository.updateUsername(username)
            result.onSuccess { refreshProfile() }
            onResult(result)
        }
    }

    fun uploadAvatar(byteArray: ByteArray, filename: String, onResult: (Result<UserProfile>) -> Unit) {
        viewModelScope.launch {
            val result = profileRepository.uploadAvatar(byteArray, filename)
            result.onSuccess { _profile.value = it }
            onResult(result)
        }
    }

    fun deleteAvatar(onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = profileRepository.deleteAvatar()
            result.onSuccess { refreshProfile() }
            onResult(result)
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
        }
    }

    fun loadPackages() {
        viewModelScope.launch {
            _packagesLoading.value = true
            subscriptionUseCase.getPackages()
                .onSuccess { result ->
                    _packages.value = result.packages
                    _recipientAddress.value = result.recipientAddress
                }
                .onFailure {
                    _packages.value = emptyList()
                    _recipientAddress.value = null
                }
            _packagesLoading.value = false
        }
    }

    fun purchasePackage(
        activityResultSender: ActivityResultSender,
        packageItem: SubscriptionPackage,
        onResult: (Result<PurchaseConfirmation>, canRetryVerification: Boolean) -> Unit
    ) {
        viewModelScope.launch {
            _purchaseInProgress.value = true
            _pendingVerification.value = null
            _purchaseConfirmation.value = null
            val result = subscriptionUseCase.purchase(activityResultSender, packageItem)
            _purchaseInProgress.value = false
            result.onSuccess { confirmation ->
                _pendingVerification.value = null
                _purchaseConfirmation.value = confirmation
                refreshProfile()
                onResult(result, false)
            }.onFailure { e ->
                val canRetry = e is VerificationPendingException
                if (canRetry) {
                    _pendingVerification.value = (e as VerificationPendingException).txSignature to e.packageKey
                } else {
                    _pendingVerification.value = null
                }
                onResult(result, canRetry)
            }
        }
    }

    fun retryPendingVerification(onResult: (Result<PurchaseConfirmation>) -> Unit) {
        val pending = _pendingVerification.value ?: return
        viewModelScope.launch {
            _purchaseInProgress.value = true
            val result = subscriptionUseCase.confirmBySignature(pending.first, pending.second)
            _purchaseInProgress.value = false
            result.onSuccess { confirmation ->
                _pendingVerification.value = null
                _purchaseConfirmation.value = confirmation
                refreshProfile()
            }
            onResult(result)
        }
    }

    fun clearPendingVerification() {
        _pendingVerification.value = null
    }

    fun dismissPurchaseConfirmation() {
        _purchaseConfirmation.value = null
    }
}
