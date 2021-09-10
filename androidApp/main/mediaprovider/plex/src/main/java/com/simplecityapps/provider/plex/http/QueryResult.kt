package com.simplecityapps.provider.plex.http

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class QueryResult(
    @Json(name = "MediaContainer") val metadata: Metadata
)

@JsonClass(generateAdapter = true)
data class Metadata(
    @Json(name = "Metadata") val items: List<Item>
)

@JsonClass(generateAdapter = true)
data class Item(
    @Json(name = "type") val type: String,
    @Json(name = "guid") val guid: String,
    @Json(name = "index") val index: Int?,
    @Json(name = "title") val title: String,
    @Json(name = "duration") val duration: Long,
    @Json(name = "parentTitle") val parentTitle: String,
    @Json(name = "grandparentTitle") val grandparentTitle: String,
    @Json(name = "key") val key: String
)