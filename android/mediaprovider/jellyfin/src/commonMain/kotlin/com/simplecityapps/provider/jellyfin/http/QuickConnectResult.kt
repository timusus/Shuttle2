package com.simplecityapps.provider.jellyfin.http

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Shared response shape for `/QuickConnect/Initiate` and `/QuickConnect/Connect`. */
@Serializable
data class QuickConnectResult(
    @SerialName("Authenticated") val authenticated: Boolean = false,
    @SerialName("Secret") val secret: String = "",
    @SerialName("Code") val code: String = ""
)
