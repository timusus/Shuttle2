package com.simplecityapps.provider.plex.http

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class QueryResult(
    @Json(name = "MediaContainer") val mediaContainer: MediaContainer
)

@JsonClass(generateAdapter = true)
data class MediaContainer(
    @Json(name = "Metadata") val metadata: List<Metadata>?,
    @Json(name = "Directory") val directories: List<Directory>?
)

@JsonClass(generateAdapter = true)
data class Directory(
    @Json(name = "key") val key: String,
    @Json(name = "title") val title: String,
    @Json(name = "type") val type: String
)

@JsonClass(generateAdapter = true)
data class Metadata(
    @Json(name = "key") val key: String,
    @Json(name = "type") val type: String,
    @Json(name = "guid") val guid: String,
    @Json(name = "index") val index: Int?,
    @Json(name = "parentIndex") val parentIndex: Int?,
    @Json(name = "title") val title: String,
    @Json(name = "duration") val duration: Long,
    @Json(name = "parentTitle") val parentTitle: String,
    @Json(name = "grandparentTitle") val grandparentTitle: String,
    @Json(name = "parentYear") val year: Int?,
    @Json(name = "Media") val media: List<Media>
)

@JsonClass(generateAdapter = true)
data class Media(
    @Json(name = "id") val id: Int,
    @Json(name = "duration") val duration: Int,
    @Json(name = "bitrate") val bitrate: Int?,
    @Json(name = "audioChannels") val audioChannels: Int,
    @Json(name = "audioCodec") val audioCodec: String,
    @Json(name = "container") val container: String,
    @Json(name = "Part") val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class Part(
    @Json(name = "id") val id: Int,
    @Json(name = "key") val key: String,
    @Json(name = "duration") val duration: Int?,
    @Json(name = "file") val file: String,
    @Json(name = "size") val size: Int,
    @Json(name = "container") val container: String?
)
