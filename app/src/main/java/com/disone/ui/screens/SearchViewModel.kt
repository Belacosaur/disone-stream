package com.disone.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.disone.core.addons.AddonManager
import com.disone.core.catalog.CatalogRepository
import com.disone.core.models.CatalogItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchState(
    val query: String = "",
    val items: List<CatalogItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val addonManager: AddonManager
) : ViewModel() {

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            addonManager.ensureDefaultAddons()
        }
    }

    fun setQuery(query: String) {
        _state.value = _state.value.copy(query = query, error = null)
        searchJob?.cancel()
        if (query.isBlank()) {
            _state.value = _state.value.copy(items = emptyList(), isLoading = false)
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            performSearch(query)
        }
    }

    private suspend fun performSearch(query: String) {
        _state.value = _state.value.copy(isLoading = true, error = null)
        val movieResult = catalogRepository.fetchCatalog("movie", "top", mapOf("search" to query))
        val seriesResult = catalogRepository.fetchCatalog("series", "top", mapOf("search" to query))
        val movies = movieResult.getOrNull().orEmpty()
        val series = seriesResult.getOrNull().orEmpty()
        val error = movieResult.exceptionOrNull()?.message ?: seriesResult.exceptionOrNull()?.message
        _state.value = _state.value.copy(
            items = movies + series,
            isLoading = false,
            error = error?.let { "Failed to search: $it" }
        )
    }

    fun search() {
        val q = _state.value.query
        if (q.isNotBlank()) {
            searchJob?.cancel()
            viewModelScope.launch { performSearch(q) }
        }
    }
}
