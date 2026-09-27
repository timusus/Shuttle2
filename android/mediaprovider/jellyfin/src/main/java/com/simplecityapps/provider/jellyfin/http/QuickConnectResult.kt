package com.simplecityapps.provider.jellyfin.http

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Shared response shape for `/QuickConnect/Initiate` and `/QuickConnect/Connect`. */
@JsonClass(generateAdapter = true)
data class QuickConnectResult(
    @Json(name = "Authenticated") val authenticated: Boolean = false,
    @Json(name = "Secret") val secret: String,
    @Json(name = "Code") val code: String = ""
)
