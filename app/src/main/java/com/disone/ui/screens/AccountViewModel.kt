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

    /** Package we're currently fetching tx for (phase 1). */
    private val _preparingPackage = MutableStateFlow<SubscriptionPackage?>(null)
    val preparingPackage = _preparingPackage.asStateFlow()

    /** When non-null, tx is fetched – tap "Confirm Payment" to open wallet. */
    private val _preparedForPurchase = MutableStateFlow<Pair<SubscriptionPackage, com.disone.core.subscription.SubscriptionRepository.CreateTransactionResult>?>(null)
    val preparedForPurchase = _preparedForPurchase.asStateFlow()

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

    suspend fun refreshProfileBlocking() {
        profileRepository.getUser().onSuccess { _profile.value = it }
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

    /** Step 1: Fetch tx from backend. No Phantom. Call when user taps Select. */
    fun prepareForPurchase(packageItem: SubscriptionPackage, onResult: (Result<Unit>) -> Unit) {
        android.util.Log.d("AccountVM", "prepareForPurchase called: ${packageItem.name} (NO wallet/Phantom)")
        val wallet = authRepository.getCurrentWallet()
        if (wallet == null) {
            onResult(Result.failure(Exception("Not logged in. Sign in first.")))
            return
        }
        viewModelScope.launch {
            _purchaseInProgress.value = true
            _preparingPackage.value = packageItem
            _preparedForPurchase.value = null
            val txResult = subscriptionUseCase.prepareTransaction(packageItem.packageKey, wallet)
            _preparingPackage.value = null
            _purchaseInProgress.value = false
            txResult.fold(
                onSuccess = { tx -> _preparedForPurchase.value = packageItem to tx; onResult(Result.success(Unit)) },
                onFailure = { onResult(Result.failure(it)) }
            )
        }
    }

    /** Step 2: Open Phantom with pre-fetched tx. Only call when user taps Pay with Phantom. */
    fun confirmPurchase(
        activityResultSender: ActivityResultSender,
        onResult: (Result<PurchaseConfirmation>, canRetryVerification: Boolean) -> Unit
    ) {
        val prepared = _preparedForPurchase.value ?: run {
            onResult(Result.failure(Exception("Transaction expired. Tap a plan again.")), false)
            return
        }
        android.util.Log.d("AccountVM", "confirmPurchase called: opening wallet for ${prepared.first.name}")
        val (packageItem, txResult) = prepared
        viewModelScope.launch {
            _purchaseInProgress.value = true
            _preparedForPurchase.value = null
            _purchaseConfirmation.value = null
            val result = subscriptionUseCase.signAndCompletePurchase(activityResultSender, packageItem, txResult)
            result.onSuccess { confirmation ->
                _pendingVerification.value = null
                _purchaseConfirmation.value = confirmation
                refreshProfileBlocking()
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
            _purchaseInProgress.value = false
        }
    }

    fun clearPreparedForPurchase() {
        _preparingPackage.value = null
        _preparedForPurchase.value = null
    }

    /** One-tap fallback: prepare then confirm (may still race on some devices). Prefer two-tap flow. */
    fun purchasePackage(
        activityResultSender: ActivityResultSender,
        packageItem: SubscriptionPackage,
        onResult: (Result<PurchaseConfirmation>, canRetryVerification: Boolean) -> Unit
    ) {
        prepareForPurchase(packageItem) { prepareResult ->
            prepareResult.onSuccess {
                confirmPurchase(activityResultSender, onResult)
            }.onFailure { onResult(Result.failure(it), false) }
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
