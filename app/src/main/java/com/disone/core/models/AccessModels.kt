package com.disone.core.models

import com.google.gson.annotations.SerializedName

data class AccessCheckResponse(
    @SerializedName("canWatch") val canWatch: Boolean,
    @SerializedName("hasSeekerGenesisToken") val hasSeekerGenesisToken: Boolean = false,
    @SerializedName("freeTrialEndsAt") val freeTrialEndsAt: String? = null,
    @SerializedName("reason") val reason: String? = null
)
