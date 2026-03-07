package com.disone.core.models

data class RatingData(
    val averageRating: Double,
    val totalRatings: Int,
    val userRating: Int? = null
)

data class RatingResponse(
    val success: Boolean = true,
    val data: RatingData
)

data class SubmitRatingRequest(
    val contentId: String,
    val contentType: String,
    val rating: Int
)
