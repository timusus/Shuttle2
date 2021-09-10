package com.simplecityapps.provider.jellyfin.http

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ArtistItem(
    @Json(name = "Name") val name: String?,
    @Json(name = "Id") val id: String?
)

@JsonClass(generateAdapter = true)
data class Item(
    @Json(name = "Name") val name: String?,
    @Json(name = "Id") val id: String,
    @Json(name = "RunTimeTicks") val runTime: Long?,
    @Json(name = "Album") val album: String?,
    @Json(name = "AlbumId") val albumId: String?,
    @Json(name = "Artists") val artists: List<String> = emptyList(),
    @Json(name = "ArtistItems") val artistItems: List<ArtistItem> = emptyList(),
    @Json(name = "AlbumArtist") val albumArtist: String?,
    @Json(name = "IndexNumber") val indexNumber: Int?,
    @Json(name = "ParentIndexNumber") val parentIndexNumber: Int?,
    @Json(name = "ProductionYear") val productionYear: Int?,
    @Json(name = "Genres") val genres: List<String> = emptyList(),
)