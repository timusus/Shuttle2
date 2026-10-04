package com.simplecityapps.provider.emby.http

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Every field but the id can be missing: Emby leaves out what an item doesn't have. Emby's ids are numeric, but sent
// as strings.

@Serializable
data class ArtistItem(
    @SerialName("Name") val name: String? = null,
    @SerialName("Id") val id: String? = null
)

@Serializable
data class MediaStream(
    @SerialName("Type") val type: String? = null,
    @SerialName("Codec") val codec: String? = null,
    @SerialName("BitDepth") val bitDepth: Int? = null
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
    @SerialName("AlbumArtists") val albumArtists: List<ArtistItem> = emptyList(),
    @SerialName("IndexNumber") val indexNumber: Int? = null,
    @SerialName("ParentIndexNumber") val parentIndexNumber: Int? = null,
    @SerialName("ProductionYear") val productionYear: Int? = null,
    @SerialName("Genres") val genres: List<String> = emptyList(),
    // Changes whenever the album's image does
    @SerialName("AlbumPrimaryImageTag") val albumPrimaryImageTag: String? = null,
    // Only returned when requested in 'fields'
    @SerialName("DateCreated") val dateCreated: String? = null,
    // Only returned when requested in 'fields': the file's MusicBrainz tags, keyed "MusicBrainzTrack" (the recording,
    // which is what a file's MUSICBRAINZ_TRACKID holds), "MusicBrainzAlbum", "MusicBrainzReleaseGroup",
    // "MusicBrainzArtist" and "MusicBrainzAlbumArtist"
    @SerialName("ProviderIds") val providerIds: Map<String, String> = emptyMap(),
    // Only returned when requested in 'fields': the file's streams, of which the audio one carries its codec and bit depth
    @SerialName("MediaStreams") val mediaStreams: List<MediaStream> = emptyList()
)
