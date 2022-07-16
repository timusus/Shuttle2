package com.simplecityapps.shuttle.mediaprovider.emby.http.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class ArtistItemResponse(
    @SerialName("Name") val name: String?,
    @SerialName("Id") val id: Int?
)

@Serializable
data class ItemResponse(
    @SerialName("Name") val name: String?,
    @SerialName("Id") val id: String,
    @SerialName("RunTimeTicks") val runTime: Long?,
    @SerialName("Album") val album: String?,
    @SerialName("AlbumId") val albumId: Int?,
    @SerialName("Artists") val artists: List<String> = emptyList(),
    @SerialName("ArtistItems") val artistItems: List<ArtistItemResponse> = emptyList(),
    @SerialName("AlbumArtist") val albumArtist: String?,
    @SerialName("IndexNumber") val indexNumber: Int?,
    @SerialName("ParentIndexNumber") val parentIndexNumber: Int?,
    @SerialName("ProductionYear") val productionYear: Int?,
    @SerialName("Genres") val genres: List<String> = emptyList(),
)