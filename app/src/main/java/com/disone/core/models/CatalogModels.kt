package com.disone.core.models

import com.google.gson.annotations.SerializedName

data class CatalogPage(
    val items: List<CatalogItem>,
    val total: Int,
    val page: Int,
    @SerializedName("has_more") val hasMore: Boolean
)

data class CatalogItem(
    val id: String,
    val title: String,
    val poster: String?,
    val year: Int?,
    val streams: List<StreamOption>
)

data class StreamOption(
    val magnet: String,
    val resolution: String?,
    val size: Long,
    val seeders: Int,
    val mode: String
)
