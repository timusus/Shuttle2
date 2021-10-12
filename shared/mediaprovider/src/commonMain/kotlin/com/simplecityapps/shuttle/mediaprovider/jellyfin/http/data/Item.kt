package com.simplecityapps.shuttle.mediaprovider.jellyfin.http.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ArtistItem(
    @SerialName("Name") val name: String?,
    @SerialName("Id") val id: String?
)

@Serializable
data class Item(
    @SerialName("Name") val name: String?,
    @SerialName("Id") val id: String,
    @SerialName("RunTimeTicks") val runTime: Long?,
    @SerialName("Album") val album: String?,
    @SerialName("AlbumId") val albumId: String?,
    @SerialName("Artists") val artists: List<String> = emptyList(),
    @SerialName("ArtistItems") val artistItems: List<ArtistItem> = emptyList(),
    @SerialName("AlbumArtist") val albumArtist: String?,
    @SerialName("IndexNumber") val indexNumber: Int?,
    @SerialName("ParentIndexNumber") val parentIndexNumber: Int?,
    @SerialName("ProductionYear") val productionYear: Int?,
    @SerialName("Genres") val genres: List<String> = emptyList(),
)