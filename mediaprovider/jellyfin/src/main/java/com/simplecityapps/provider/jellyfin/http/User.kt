package com.simplecityapps.provider.jellyfin.http

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.io.Serializable

@JsonClass(generateAdapter = true)
data class User(
    @Json(name = "Id") val id: String,
    @Json(name = "Name") val name: String
) : Serializable