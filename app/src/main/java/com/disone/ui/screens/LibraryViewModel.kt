package com.disone.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.disone.core.auth.AuthRepository
import com.disone.core.auth.AuthState
import com.disone.core.library.LibraryRepository
import com.disone.core.models.LibraryItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryState(
    val continueWatching: List<LibraryItem> = emptyList(),
    val libraryItems: List<LibraryItem> = emptyList(),
    val hasNextPage: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val typeFilter: String? = null,
    val sortBy: String = "lastWatched",
    val searchQuery: String = ""
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    val authState: StateFlow<AuthState> = authRepository.authState

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val cwResult = libraryRepository.continueWatching()
            val listResult = libraryRepository.list(
                type = _state.value.typeFilter,
                sort = _state.value.sortBy,
                page = 1,
                limit = 50
            )
            _state.value = _state.value.copy(
                continueWatching = cwResult.getOrNull() ?: emptyList(),
                libraryItems = listResult.getOrNull()?.first ?: emptyList(),
                hasNextPage = listResult.getOrNull()?.second ?: false,
                isLoading = false,
                error = listResult.fold(
                    onSuccess = { null },
                    onFailure = { it.message }
                )
            )
        }
    }

    fun setTypeFilter(type: String?) {
        _state.value = _state.value.copy(typeFilter = type)
        load()
    }

    fun setSort(sort: String) {
        _state.value = _state.value.copy(sortBy = sort)
        load()
    }

    fun removeFromLibrary(libraryItemId: String) {
        viewModelScope.launch {
            libraryRepository.remove(libraryItemId).onSuccess {
                load()
            }.onFailure {
                _state.value = _state.value.copy(error = it.message)
            }
        }
    }

    fun rewind(libraryItemId: String) {
        viewModelScope.launch {
            libraryRepository.rewind(libraryItemId).onSuccess {
                load()
            }.onFailure {
                _state.value = _state.value.copy(error = it.message)
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun setSearchQuery(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
    }
}
