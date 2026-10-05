package com.simplecityapps.provider.plex.http

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class QueryResult(
    @SerialName("MediaContainer") val mediaContainer: MediaContainer = MediaContainer()
)

@Serializable
data class MediaContainer(
    @SerialName("Metadata") val metadata: List<Metadata>? = null,
    @SerialName("Directory") val directories: List<Directory>? = null,
    // The size of the whole listing, present on a paged request (`X-Plex-Container-Start`/`X-Plex-Container-Size`)
    @SerialName("totalSize") val totalSize: Int? = null
)

@Serializable
data class Directory(
    @SerialName("key") val key: String,
    @SerialName("type") val type: String? = null
)

/** A track. Only [key] is always there: a track the server hasn't analysed yet has no duration or media, and a loose one no album or artist. */
@Serializable
data class Metadata(
    @SerialName("key") val key: String,
    @SerialName("type") val type: String? = null,
    @SerialName("guid") val guid: String? = null,
    // This item's own id on the server: a playlist's, in /playlists/{ratingKey}/items
    @SerialName("ratingKey") val ratingKey: String? = null,
    @SerialName("index") val index: Int? = null,
    @SerialName("parentIndex") val parentIndex: Int? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("duration") val duration: Long? = null,
    @SerialName("parentTitle") val parentTitle: String? = null,
    @SerialName("grandparentTitle") val grandparentTitle: String? = null,
    @SerialName("parentYear") val year: Int? = null,
    @SerialName("Media") val media: List<Media> = emptyList(),
    // Epoch seconds
    @SerialName("addedAt") val addedAt: Long? = null,
    @SerialName("updatedAt") val updatedAt: Long? = null,
    // Server-relative image paths, resolved against the server address and signed with its token
    @SerialName("thumb") val thumb: String? = null,
    @SerialName("parentThumb") val parentThumb: String? = null,
    @SerialName("grandparentThumb") val grandparentThumb: String? = null,
    // The track's own artist, when it differs from the album's (grandparentTitle)
    @SerialName("originalTitle") val originalTitle: String? = null,
    // The album's and the album artist's ids on the server
    @SerialName("parentRatingKey") val parentRatingKey: String? = null,
    @SerialName("grandparentRatingKey") val grandparentRatingKey: String? = null,
    // Only with includeGuids: the track's external ids, such as "mbid://<recording id>"
    @SerialName("Guid") val guids: List<Guid> = emptyList(),
    // The user's rating out of 10 (5 stars), and when they gave it in epoch seconds; a favourite is a 10
    @SerialName("userRating") val userRating: Double? = null,
    @SerialName("lastRatedAt") val lastRatedAt: Long? = null,
    // Absent for a track never played; lastViewedAt is epoch seconds
    @SerialName("viewCount") val viewCount: Int? = null,
    @SerialName("lastViewedAt") val lastViewedAt: Long? = null
)

@Serializable
data class Guid(
    @SerialName("id") val id: String
)

@Serializable
data class Media(
    @SerialName("id") val id: Long? = null,
    @SerialName("duration") val duration: Long? = null,
    @SerialName("bitrate") val bitrate: Int? = null,
    @SerialName("audioChannels") val audioChannels: Int? = null,
    @SerialName("audioCodec") val audioCodec: String? = null,
    @SerialName("container") val container: String? = null,
    @SerialName("Part") val parts: List<Part> = emptyList()
)

@Serializable
data class Part(
    @SerialName("id") val id: Long? = null,
    @SerialName("key") val key: String? = null,
    @SerialName("duration") val duration: Long? = null,
    @SerialName("file") val file: String? = null,
    // Bytes; a Long, since a hi-res file can pass 2 GB
    @SerialName("size") val size: Long? = null,
    @SerialName("container") val container: String? = null,
    // Only on a track's own metadata (or a listing asked for streams): the part's audio, video and subtitle streams
    @SerialName("Stream") val streams: List<Stream> = emptyList()
)

/** One stream of a [Part]; the audio one ([STREAM_TYPE_AUDIO]) holds the bit depth. */
@Serializable
data class Stream(
    @SerialName("streamType") val streamType: Int? = null,
    @SerialName("codec") val codec: String? = null,
    @SerialName("bitDepth") val bitDepth: Int? = null
)

const val STREAM_TYPE_AUDIO = 2
