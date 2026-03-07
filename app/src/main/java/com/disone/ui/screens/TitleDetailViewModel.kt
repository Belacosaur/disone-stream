package com.disone.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.disone.core.addons.AddonMeta
import com.disone.core.addons.AddonParser
import com.disone.core.addons.AddonService
import com.disone.core.auth.AuthRepository
import com.disone.core.auth.AuthState
import com.disone.core.models.Comment
import com.disone.core.models.RatingData
import com.disone.core.models.Review
import com.disone.core.ratings.RatingsRepository
import com.disone.core.reviews.ReviewsRepository
import com.disone.core.comments.CommentsRepository
import com.disone.core.library.LibraryRepository
import com.disone.core.utils.toItemId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val CINEMETA_URL = "https://v3-cinemeta.strem.io/"

data class TitleDetailState(
    val meta: AddonMeta? = null,
    val ratings: RatingData? = null,
    val reviews: List<Review> = emptyList(),
    val reviewsPagination: com.disone.core.models.Pagination? = null,
    val comments: List<Comment> = emptyList(),
    val commentsPagination: com.disone.core.models.Pagination? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val reviewPage: Int = 1,
    val commentPage: Int = 1,
    val inLibrary: Boolean = false
)

@HiltViewModel
class TitleDetailViewModel @Inject constructor(
    private val addonService: AddonService,
    private val addonParser: AddonParser,
    private val ratingsRepository: RatingsRepository,
    private val reviewsRepository: ReviewsRepository,
    private val commentsRepository: CommentsRepository,
    private val libraryRepository: LibraryRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(TitleDetailState())
    val state: StateFlow<TitleDetailState> = _state.asStateFlow()

    val authState: StateFlow<AuthState> = authRepository.authState

    fun load(type: String, id: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val metaResult = addonService.fetchMeta(CINEMETA_URL, type, id).fold(
                onSuccess = { json -> addonParser.parseMetaResponse(json) },
                onFailure = { Result.failure(it) }
            )
            val meta = metaResult.getOrNull()?.meta
            if (meta == null && metaResult.isFailure) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = metaResult.exceptionOrNull()?.message ?: "Failed to load"
                )
                return@launch
            }

            val ratingsResult = ratingsRepository.get(id, type)
            val ratings = ratingsResult.getOrNull()

            val inLibrary = if (authRepository.authState.value is AuthState.Authenticated) {
                libraryRepository.contains(toItemId(type, id)).getOrNull() ?: false
            } else false

            val reviewsResult = reviewsRepository.get(id, type, page = 1, limit = 10)
            val reviewsData = reviewsResult.getOrNull()

            val commentsResult = commentsRepository.get(id, type, page = 1, limit = 20)
            val commentsData = commentsResult.getOrNull()

            _state.value = _state.value.copy(
                meta = meta,
                ratings = ratings,
                reviews = reviewsData?.reviews ?: emptyList(),
                reviewsPagination = reviewsData?.pagination,
                comments = commentsData?.comments ?: emptyList(),
                commentsPagination = commentsData?.pagination,
                isLoading = false,
                error = null,
                reviewPage = 1,
                commentPage = 1,
                inLibrary = inLibrary
            )
        }
    }

    fun submitRating(type: String, id: String, rating: Int) {
        viewModelScope.launch {
            ratingsRepository.submit(id, type, rating.coerceIn(0, 100)).onSuccess {
                load(type, id)
            }.onFailure {
                _state.value = _state.value.copy(error = it.message)
            }
        }
    }

    fun deleteRating(type: String, id: String) {
        viewModelScope.launch {
            ratingsRepository.delete(id, type).onSuccess {
                load(type, id)
            }.onFailure {
                _state.value = _state.value.copy(error = it.message)
            }
        }
    }

    fun submitReview(type: String, id: String, body: String, rating: Int?) {
        viewModelScope.launch {
            val rating100 = rating?.let { (it * 10).coerceIn(0, 100) }
            reviewsRepository.submit(id, type, body, rating100).onSuccess {
                load(type, id)
            }.onFailure {
                _state.value = _state.value.copy(error = it.message)
            }
        }
    }

    fun loadMoreReviews(type: String, id: String) {
        viewModelScope.launch {
            val pag = _state.value.reviewsPagination ?: return@launch
            if (_state.value.reviewPage >= pag.totalPages) return@launch
            val nextPage = _state.value.reviewPage + 1
            reviewsRepository.get(id, type, page = nextPage, limit = 10).onSuccess { data ->
                _state.value = _state.value.copy(
                    reviews = _state.value.reviews + data.reviews,
                    reviewsPagination = data.pagination,
                    reviewPage = nextPage
                )
            }
        }
    }

    fun editReview(type: String, id: String, reviewId: String, body: String, rating: Int?) {
        viewModelScope.launch {
            val rating100 = rating?.let { (it * 10).coerceIn(0, 100) }
            reviewsRepository.update(reviewId, body, rating100).onSuccess {
                load(type, id)
            }.onFailure {
                _state.value = _state.value.copy(error = it.message)
            }
        }
    }

    fun deleteReview(type: String, id: String, reviewId: String) {
        viewModelScope.launch {
            reviewsRepository.delete(reviewId).onSuccess {
                load(type, id)
            }.onFailure {
                _state.value = _state.value.copy(error = it.message)
            }
        }
    }

    fun submitComment(type: String, id: String, body: String, parentId: String?) {
        viewModelScope.launch {
            commentsRepository.submit(id, type, body, parentId).onSuccess {
                load(type, id)
            }.onFailure {
                _state.value = _state.value.copy(error = it.message)
            }
        }
    }

    fun loadMoreComments(type: String, id: String) {
        viewModelScope.launch {
            val pag = _state.value.commentsPagination ?: return@launch
            if (_state.value.commentPage >= pag.totalPages) return@launch
            val nextPage = _state.value.commentPage + 1
            commentsRepository.get(id, type, page = nextPage, limit = 20).onSuccess { data ->
                _state.value = _state.value.copy(
                    comments = _state.value.comments + data.comments,
                    commentsPagination = data.pagination,
                    commentPage = nextPage
                )
            }
        }
    }

    fun editComment(type: String, id: String, commentId: String, body: String) {
        viewModelScope.launch {
            commentsRepository.update(commentId, body).onSuccess {
                load(type, id)
            }.onFailure {
                _state.value = _state.value.copy(error = it.message)
            }
        }
    }

    fun deleteComment(type: String, id: String, commentId: String) {
        viewModelScope.launch {
            commentsRepository.delete(commentId).onSuccess {
                load(type, id)
            }.onFailure {
                _state.value = _state.value.copy(error = it.message)
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun addToLibrary() {
        val meta = _state.value.meta ?: return
        val type = meta.type
        val id = meta.id
        viewModelScope.launch {
            libraryRepository.add(toItemId(type, id), meta.name, type, id, meta.poster).onSuccess {
                _state.value = _state.value.copy(inLibrary = true)
            }.onFailure {
                _state.value = _state.value.copy(error = it.message)
            }
        }
    }

    fun removeFromLibrary() {
        val meta = _state.value.meta ?: return
        val type = meta.type
        val id = meta.id
        viewModelScope.launch {
            libraryRepository.remove(toItemId(type, id)).onSuccess {
                _state.value = _state.value.copy(inLibrary = false)
            }.onFailure {
                _state.value = _state.value.copy(error = it.message)
            }
        }
    }
}
