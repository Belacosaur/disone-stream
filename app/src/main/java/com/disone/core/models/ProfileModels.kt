package com.disone.core.models

import com.google.gson.annotations.SerializedName

data class UserProfile(
    val id: String,
    val wallet: String,
    val plan: String,
    @SerializedName("packageName") val packageName: String? = null,
    @SerializedName("packageKey") val packageKey: String? = null,
    @SerializedName("expiresAt") val expiresAt: String? = null,
    val username: String? = null,
    @SerializedName("avatarUrl") val avatarUrl: String? = null
)

data class UserResponse(
    val user: UserProfile
)
