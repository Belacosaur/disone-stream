package com.disone.core.models

import com.google.gson.annotations.SerializedName

data class Review(
    val id: String,
    val body: String,
    val rating: Int? = null,
    @SerializedName("createdAt") val createdAt: String,
    @SerializedName("updatedAt") val updatedAt: String,
    val user: ReviewUser
)

data class ReviewUser(
    val id: String,
    val username: String,
    @SerializedName("avatarUrl") val avatarUrl: String? = null
)

data class ReviewsResponse(
    val success: Boolean = true,
    val data: ReviewsData
)

data class ReviewsData(
    val reviews: List<Review>,
    val pagination: Pagination
)

data class Pagination(
    val page: Int,
    val limit: Int,
    val total: Int,
    @SerializedName("totalPages") val totalPages: Int
)

data class SubmitReviewResponse(
    val success: Boolean = true,
    val review: Review? = null
)
