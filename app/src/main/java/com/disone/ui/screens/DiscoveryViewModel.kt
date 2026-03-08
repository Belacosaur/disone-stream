package com.disone.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.disone.core.addons.AddonCatalog
import com.disone.core.addons.AddonManager
import com.disone.core.catalog.CatalogRepository
import com.disone.core.models.CatalogItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DiscoveryState(
    val type: String = "movie",
    val catalogId: String = "top",
    val catalogName: String = "Popular",
    val genre: String? = null,
    val genreOptions: List<String> = emptyList(),
    val genreRequired: Boolean = false,
    val searchQuery: String = "",
    val items: List<CatalogItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val catalogs: List<AddonCatalog> = emptyList(),
    val hasNextPage: Boolean = false,
    val skip: Int = 0
)

@HiltViewModel
class DiscoveryViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val addonManager: AddonManager
) : ViewModel() {

    private val _state = MutableStateFlow(DiscoveryState())
    val state: StateFlow<DiscoveryState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            addonManager.ensureDefaultAddons() // Ensure addons before loading catalogs (Popular, New, Netflix, etc.)
            loadManifest()
            loadCatalog()
        }
    }

    private suspend fun loadManifest() {
        addonManager.getManifestCatalogs().fold(
            onSuccess = { catalogs ->
                _state.value = _state.value.copy(
                    catalogs = catalogs,
                    genreOptions = deriveGenreOptions(catalogs),
                    genreRequired = isYearRequired(catalogs)
                )
            },
            onFailure = { _ ->
                _state.value = _state.value.copy(catalogs = emptyList(), genreOptions = emptyList(), genreRequired = false)
            }
        )
    }

    private fun deriveGenreOptions(catalogs: List<AddonCatalog>): List<String> {
        val catalog = catalogs.find { it.type == _state.value.type && it.id == _state.value.catalogId }
            ?: catalogs.firstOrNull { it.type == _state.value.type }
            ?: return emptyList()
        return catalog.genres.orEmpty().ifEmpty {
            catalog.extra?.find { it.name == "genre" }?.options.orEmpty()
        }
    }

    private fun isYearRequired(catalogs: List<AddonCatalog>): Boolean {
        val catalog = catalogs.find { it.type == _state.value.type && it.id == _state.value.catalogId } ?: return false
        return catalog.extraRequired?.contains("genre") == true
    }

    fun loadCatalog() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val s = _state.value
            val extraParams = buildMap<String, String> {
                // When genreRequired (e.g. "New" year catalog), we must pass genre - use first option if none selected
                val genreToPass = s.genre ?: if (s.genreRequired && s.genreOptions.isNotEmpty()) s.genreOptions.first() else null
                genreToPass?.let { put("genre", it) }
                if (s.searchQuery.isNotBlank()) put("search", s.searchQuery)
                if (s.skip > 0) put("skip", s.skip.toString())
            }.takeIf { it.isNotEmpty() }

            catalogRepository.fetchCatalog(s.type, s.catalogId, extraParams).fold(
                onSuccess = { items ->
                    _state.value = _state.value.copy(
                        items = items,
                        isLoading = false,
                        error = null,
                        hasNextPage = items.size >= 20
                    )
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load"
                    )
                }
            )
        }
    }

    fun setType(type: String) {
        val catalogs = _state.value.catalogs.filter { it.type == type }
        val firstCatalog = catalogs.firstOrNull()
        val catalogId = firstCatalog?.id ?: "top"
        val catalogName = firstCatalog?.name ?: "Popular"
        val genreOptions = firstCatalog?.genres.orEmpty().ifEmpty {
            firstCatalog?.extra?.find { it.name == "genre" }?.options.orEmpty()
        }
        val requiredGenre = firstCatalog?.extraRequired?.contains("genre") == true
        val genre = if (requiredGenre && genreOptions.isNotEmpty()) genreOptions.first() else null

        _state.value = _state.value.copy(
            type = type,
            catalogId = catalogId,
            catalogName = catalogName,
            genre = genre,
            genreOptions = genreOptions,
            genreRequired = requiredGenre,
            skip = 0
        )
        loadCatalog()
    }

    fun setCatalog(catalogId: String, catalogName: String) {
        val catalogs = _state.value.catalogs.filter { it.type == _state.value.type }
        val catalog = catalogs.find { it.id == catalogId }
        val genreOptions = catalog?.genres.orEmpty().ifEmpty {
            catalog?.extra?.find { it.name == "genre" }?.options.orEmpty()
        }
        val requiredGenre = catalog?.extraRequired?.contains("genre") == true
        val genre = when {
            requiredGenre && genreOptions.isNotEmpty() -> genreOptions.first()
            !requiredGenre -> null
            else -> _state.value.genre
        }

        _state.value = _state.value.copy(
            catalogId = catalogId,
            catalogName = catalogName,
            genre = genre,
            genreOptions = genreOptions,
            genreRequired = requiredGenre,
            skip = 0
        )
        loadCatalog()
    }

    fun setGenre(genre: String?) {
        _state.value = _state.value.copy(genre = genre, skip = 0)
        loadCatalog()
    }

    fun setSearchQuery(query: String) {
        _state.value = _state.value.copy(searchQuery = query, skip = 0)
    }

    fun search() {
        loadCatalog()
    }

    fun loadMore() {
        _state.value = _state.value.copy(skip = _state.value.items.size)
        viewModelScope.launch {
            val s = _state.value
            val genreToPass = s.genre ?: if (s.genreRequired && s.genreOptions.isNotEmpty()) s.genreOptions.first() else null
            val extraParams = buildMap<String, String> {
                genreToPass?.let { put("genre", it) }
                if (s.searchQuery.isNotBlank()) put("search", s.searchQuery)
                put("skip", s.skip.toString())
            }

            catalogRepository.fetchCatalog(s.type, s.catalogId, extraParams).fold(
                onSuccess = { newItems ->
                    _state.value = _state.value.copy(
                        items = s.items + newItems,
                        hasNextPage = newItems.size >= 20
                    )
                },
                onFailure = { }
            )
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
