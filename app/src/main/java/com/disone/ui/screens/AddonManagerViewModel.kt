package com.disone.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.disone.core.addons.AddonManager
import com.disone.core.addons.AddonParseException
import com.disone.core.addons.AddonNetworkException
import com.disone.core.addons.InstalledAddon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddonManagerViewModel @Inject constructor(
    private val addonManager: AddonManager
) : ViewModel() {

    val installedAddons: StateFlow<List<InstalledAddon>> = addonManager.installedAddons
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _addUrlDialogState = MutableStateFlow(false)
    val addUrlDialogState: StateFlow<Boolean> = _addUrlDialogState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun showAddUrlDialog() {
        _addUrlDialogState.value = true
        _errorMessage.value = null
    }

    fun dismissAddUrlDialog() {
        _addUrlDialogState.value = false
    }

    fun addAddon(url: String) {
        if (url.isBlank()) return
        viewModelScope.launch {
            _errorMessage.value = null
            addonManager.addAddon(url).fold(
                onSuccess = {
                    _addUrlDialogState.value = false
                },
                onFailure = { e ->
                    _errorMessage.value = when (e) {
                        is AddonParseException -> "Invalid addon: ${e.message}"
                        is AddonNetworkException -> "Network error: ${e.message}"
                        else -> "Failed to add: ${e.message}"
                    }
                }
            )
        }
    }

    fun removeAddon(url: String) {
        viewModelScope.launch {
            addonManager.removeAddon(url)
        }
    }

    fun setEnabled(url: String, enabled: Boolean) {
        viewModelScope.launch {
            addonManager.setEnabled(url, enabled)
        }
    }
}
