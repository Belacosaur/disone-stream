package com.disone.core.models

import com.google.gson.annotations.SerializedName

data class Comment(
    val id: String,
    val body: String,
    @SerializedName("createdAt") val createdAt: String,
    @SerializedName("updatedAt") val updatedAt: String,
    @SerializedName("parentId") val parentId: String? = null,
    val user: CommentUser,
    val replies: List<Comment> = emptyList()
)

data class CommentUser(
    val id: String,
    val username: String,
    @SerializedName("avatarUrl") val avatarUrl: String? = null
)

data class CommentsResponse(
    val success: Boolean = true,
    val data: CommentsData
)

data class CommentsData(
    val comments: List<Comment>,
    val pagination: Pagination
)
