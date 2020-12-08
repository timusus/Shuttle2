package com.simplecityapps.provider.emby.http

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SessionInfo(
    @Json(
        name = "DeviceId"
    ) val deviceId: String
)