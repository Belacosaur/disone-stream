package com.disone.core.models

import com.google.gson.annotations.SerializedName

data class LibraryListResponse(
    val success: Boolean,
    val catalog: List<LibraryItem>,
    val total: Int,
    val page: Int,
    val limit: Int,
    @SerializedName("hasNextPage") val hasNextPage: Boolean
)

data class LibraryItem(
    @SerializedName("_id") val id: String,
    val name: String,
    val type: String,
    val poster: String?,
    @SerializedName("posterShape") val posterShape: String?,
    val state: LibraryItemState?,
    val progress: Double,
    @SerializedName("deepLinks") val deepLinks: LibraryDeepLinks?
)

data class LibraryItemState(
    @SerializedName("lastWatched") val lastWatched: String?,
    @SerializedName("timeWatched") val timeWatched: Long?,
    @SerializedName("timeOffset") val timeOffset: Long?,
    val duration: Long?,
    val watched: String?,
    @SerializedName("flaggedWatched") val flaggedWatched: Int?
)

data class LibraryDeepLinks(
    @SerializedName("metaDetailsVideos") val metaDetailsVideos: String?,
    val player: String?
)

data class LibraryContainsResponse(
    @SerializedName("inLibrary") val inLibrary: Boolean
)

data class LibraryAddRequest(
    @SerializedName("library_item_id") val libraryItemId: String,
    val name: String,
    val type: String,
    @SerializedName("meta_id") val metaId: String,
    val poster: String? = null,
    @SerializedName("poster_shape") val posterShape: String? = "regular"
)

data class LibraryUpdateProgressRequest(
    @SerializedName("library_item_id") val libraryItemId: String,
    @SerializedName("time_offset") val timeOffset: Long? = null,
    @SerializedName("time_watched") val timeWatched: Long? = null,
    @SerializedName("overall_time_watched") val overallTimeWatched: Long? = null,
    @SerializedName("video_id") val videoId: String? = null,
    val duration: Long? = null
)

data class LibraryRewindRequest(
    @SerializedName("library_item_id") val libraryItemId: String
)
