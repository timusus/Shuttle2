package com.simplecityapps.provider.jellyfin.http

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Every field but the id can be missing: Jellyfin leaves out what an item doesn't have.

@Serializable
data class ArtistItem(
    @SerialName("Name") val name: String? = null,
    @SerialName("Id") val id: String? = null
)

@Serializable
data class Item(
    @SerialName("Name") val name: String? = null,
    @SerialName("Id") val id: String,
    @SerialName("RunTimeTicks") val runTime: Long? = null,
    @SerialName("Album") val album: String? = null,
    @SerialName("AlbumId") val albumId: String? = null,
    @SerialName("Artists") val artists: List<String> = emptyList(),
    @SerialName("ArtistItems") val artistItems: List<ArtistItem> = emptyList(),
    @SerialName("AlbumArtist") val albumArtist: String? = null,
    @SerialName("IndexNumber") val indexNumber: Int? = null,
    @SerialName("ParentIndexNumber") val parentIndexNumber: Int? = null,
    @SerialName("ProductionYear") val productionYear: Int? = null,
    @SerialName("Genres") val genres: List<String> = emptyList(),
    // Changes whenever the album's image does
    @SerialName("AlbumPrimaryImageTag") val albumPrimaryImageTag: String? = null,
    // Only returned when requested in 'fields'
    @SerialName("DateCreated") val dateCreated: String? = null
)
