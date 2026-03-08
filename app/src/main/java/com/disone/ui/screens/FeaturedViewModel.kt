package com.disone.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.disone.core.auth.AuthRepository
import com.disone.core.auth.AuthState
import com.disone.core.catalog.CatalogRepository
import com.disone.core.library.LibraryRepository
import com.disone.core.models.CatalogItem
import com.disone.core.models.LibraryItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CatalogRow(
    val title: String,
    val items: List<CatalogItem>
)

data class FeaturedState(
    val continueWatching: List<LibraryItem> = emptyList(),
    val rows: List<CatalogRow> = emptyList(),
    val type: String = "movie",
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class FeaturedViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val libraryRepository: LibraryRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(FeaturedState())
    val state: StateFlow<FeaturedState> = _state.asStateFlow()

    val authState: StateFlow<AuthState> = authRepository.authState

    init {
        viewModelScope.launch {
            authState.collectLatest { load() }
        }
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val currentType = _state.value.type
            val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR).toString()

            val continueWatchingDeferred = async {
                if (authState.value is AuthState.Authenticated) {
                    libraryRepository.continueWatching().getOrNull().orEmpty()
                } else {
                    emptyList()
                }
            }

            val popularDeferred = async {
                catalogRepository.fetchCatalog(currentType, "top", null).getOrNull().orEmpty()
            }

            val newThisYearDeferred = async {
                catalogRepository.fetchCatalog(
                    currentType,
                    "year",
                    mapOf("genre" to currentYear)
                ).getOrNull().orEmpty()
            }

            val featuredDeferred = async {
                catalogRepository.fetchCatalog(currentType, "imdbRating", null).getOrNull().orEmpty()
            }

            val netflixDeferred = async {
                catalogRepository.fetchCatalog(currentType, "nfx", null).getOrNull().orEmpty()
            }
            val hboMaxDeferred = async {
                catalogRepository.fetchCatalog(currentType, "hbm", null).getOrNull().orEmpty()
            }
            val disneyPlusDeferred = async {
                catalogRepository.fetchCatalog(currentType, "dnp", null).getOrNull().orEmpty()
            }
            val primeVideoDeferred = async {
                catalogRepository.fetchCatalog(currentType, "amp", null).getOrNull().orEmpty()
            }
            val appleTvDeferred = async {
                catalogRepository.fetchCatalog(currentType, "atp", null).getOrNull().orEmpty()
            }

            val continueWatching = continueWatchingDeferred.await()
            val popular = popularDeferred.await()
            val newThisYear = newThisYearDeferred.await()
            val featured = featuredDeferred.await()
            val netflix = netflixDeferred.await()
            val hboMax = hboMaxDeferred.await()
            val disneyPlus = disneyPlusDeferred.await()
            val primeVideo = primeVideoDeferred.await()
            val appleTv = appleTvDeferred.await()

            val rows = mutableListOf<CatalogRow>()
            if (popular.isNotEmpty()) rows.add(CatalogRow("Popular", popular))
            if (newThisYear.isNotEmpty()) rows.add(CatalogRow("New This Year", newThisYear))
            if (featured.isNotEmpty()) rows.add(CatalogRow("Featured", featured))
            if (netflix.isNotEmpty()) rows.add(CatalogRow("Netflix", netflix))
            if (hboMax.isNotEmpty()) rows.add(CatalogRow("HBO Max", hboMax))
            if (disneyPlus.isNotEmpty()) rows.add(CatalogRow("Disney+", disneyPlus))
            if (primeVideo.isNotEmpty()) rows.add(CatalogRow("Prime Video", primeVideo))
            if (appleTv.isNotEmpty()) rows.add(CatalogRow("Apple TV+", appleTv))

            _state.value = _state.value.copy(
                continueWatching = continueWatching,
                rows = rows,
                isLoading = false,
                error = null
            )
        }
    }

    fun setType(type: String) {
        _state.value = _state.value.copy(type = type)
        load()
    }

    fun clearProgress(libraryItemId: String) {
        viewModelScope.launch {
            libraryRepository.rewind(libraryItemId).onSuccess {
                load()
            }.onFailure {
                _state.value = _state.value.copy(error = it.message)
            }
        }
    }
}
