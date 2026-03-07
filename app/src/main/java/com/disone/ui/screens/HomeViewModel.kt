package com.disone.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.disone.core.addons.AddonCatalogResponse
import com.disone.core.addons.AddonMetaPreview
import com.disone.core.addons.AddonParser
import com.disone.core.addons.AddonService
import com.disone.core.models.CatalogItem
import com.disone.core.models.CatalogPage
import com.disone.core.streaming.StreamingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val CINEMETA_URL = "https://v3-cinemeta.strem.io/"

data class HomeState(
    val items: List<CatalogItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val magnetToPlay: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val streamingRepository: StreamingRepository,
    private val addonService: AddonService,
    private val addonParser: AddonParser
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    /**
     * Discovery catalog: fetch directly from Cinemeta. No addon storage, no ensureDefaultAddons.
     * Merges with backend catalog if available.
     */
    fun loadCatalog() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val backendDeferred = async { streamingRepository.getCatalog(page = 1) }
            val cinemetaDeferred = async {
                addonService.fetchCatalog(CINEMETA_URL, "movie", "top").fold(
                    onSuccess = { json -> addonParser.parseCatalogResponse(json) },
                    onFailure = { Result.failure(it) }
                )
            }
            val (backendResult, cinemetaResult) = awaitAll(backendDeferred, cinemetaDeferred)
            val backendItems = (backendResult as Result<CatalogPage>).getOrNull()?.items ?: emptyList()
            val metas = (cinemetaResult as Result<AddonCatalogResponse>).getOrNull()?.metas.orEmpty()
            cinemetaResult.onFailure { Log.e("HomeViewModel", "Cinemeta fetch failed", it) }
            val addonItems = metas.mapNotNull { meta ->
                val id = meta.effectiveId()
                if (id.isBlank()) return@mapNotNull null
                CatalogItem(
                    id = "${meta.type ?: "movie"}:$id",
                    title = meta.name ?: "",
                    poster = meta.poster,
                    year = meta.year?.toIntOrNull(),
                    streams = emptyList()
                )
            }
            val seenIds = mutableSetOf<String>()
            val merged = (addonItems + backendItems).filter { item -> seenIds.add(item.id) }
            _state.value = _state.value.copy(
                items = merged,
                isLoading = false,
                error = if (merged.isEmpty()) "Could not load catalog. Check internet." else null
            )
        }
    }

    companion object {
        private const val TAG = "HomeViewModel"
    }

    fun showMagnetDialog() {
        _state.value = _state.value.copy(magnetToPlay = "")
    }

    fun updateMagnetInput(value: String) {
        _state.value = _state.value.copy(magnetToPlay = value)
    }

    fun dismissMagnetDialog() {
        _state.value = _state.value.copy(magnetToPlay = null)
    }
}
