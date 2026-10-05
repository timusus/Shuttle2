package com.simplecityapps.provider.subsonic.http

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The Subsonic API's JSON envelope (`f=json`): every response, success or failure, is a `subsonic-response` object, and a
 * failure still comes with HTTP 200, as `status: "failed"` and an [ErrorDto]. Written from the Subsonic 1.16.1 and
 * OpenSubsonic specs; each endpoint fills one of the optional payloads.
 */
@Serializable
class SubsonicEnvelope(
    @SerialName("subsonic-response") val response: SubsonicResponse
)

@Serializable
data class SubsonicResponse(
    val status: String,
    val version: String? = null,
    /** The server software (`navidrome`, `gonic`), on an OpenSubsonic server. */
    val type: String? = null,
    val serverVersion: String? = null,
    val openSubsonic: Boolean = false,
    val error: ErrorDto? = null,
    val searchResult3: SearchResult3? = null,
    val albumList2: AlbumList2? = null,
    val album: AlbumDto? = null,
    val artist: ArtistDto? = null,
    val playlists: PlaylistsDto? = null,
    val playlist: PlaylistDto? = null,
    val openSubsonicExtensions: List<ExtensionDto>? = null,
    val transcodeDecision: TranscodeDecisionDto? = null
) {
    val isOk: Boolean get() = status == "ok"
}

@Serializable
data class ErrorDto(
    val code: Int,
    val message: String? = null
)

@Serializable
data class ExtensionDto(
    val name: String,
    val versions: List<Int> = emptyList()
)

@Serializable
data class SearchResult3(
    val song: List<SongDto> = emptyList()
)

@Serializable
data class AlbumList2(
    val album: List<AlbumDto> = emptyList()
)

@Serializable
data class AlbumDto(
    val id: String,
    val name: String? = null,
    val songCount: Int? = null,
    val song: List<SongDto> = emptyList()
)

@Serializable
data class ArtistDto(
    val id: String,
    val name: String? = null,
    val coverArt: String? = null
)

@Serializable
data class PlaylistsDto(
    val playlist: List<PlaylistDto> = emptyList()
)

@Serializable
data class PlaylistDto(
    val id: String,
    val name: String? = null,
    val songCount: Int? = null,
    val entry: List<SongDto> = emptyList()
)

/** An artist credit: OpenSubsonic's `artists` and `albumArtists` entries. */
@Serializable
data class ArtistRefDto(
    val id: String? = null,
    val name: String? = null
)

@Serializable
data class GenreRefDto(
    val name: String? = null
)

/** OpenSubsonic's `replayGain`, in dB. A server with no tags sends `{}`, or zeros. */
@Serializable
data class ReplayGainDto(
    val trackGain: Double? = null,
    val albumGain: Double? = null,
    val trackPeak: Double? = null,
    val albumPeak: Double? = null
)

/**
 * A song: Subsonic's `Child`, with OpenSubsonic's additions (multi-valued [artists], [albumArtists] and [genres],
 * [musicBrainzId], [replayGain], audio properties). [duration] is in seconds, [bitRate] in kbps.
 */
@Serializable
data class SongDto(
    val id: String,
    val isDir: Boolean = false,
    val isVideo: Boolean = false,
    val title: String? = null,
    val album: String? = null,
    val albumId: String? = null,
    val artist: String? = null,
    val artistId: String? = null,
    val artists: List<ArtistRefDto> = emptyList(),
    val albumArtists: List<ArtistRefDto> = emptyList(),
    val displayArtist: String? = null,
    val displayAlbumArtist: String? = null,
    val track: Int? = null,
    val discNumber: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val genres: List<GenreRefDto> = emptyList(),
    val coverArt: String? = null,
    val size: Long? = null,
    val contentType: String? = null,
    val suffix: String? = null,
    val duration: Int? = null,
    val bitRate: Int? = null,
    val samplingRate: Int? = null,
    val bitDepth: Int? = null,
    val channelCount: Int? = null,
    val created: String? = null,
    val starred: String? = null,
    val playCount: Int? = null,
    val musicBrainzId: String? = null,
    val replayGain: ReplayGainDto? = null
)

/** OpenSubsonic's `transcoding` extension: `getTranscodeDecision`'s answer for one song and [ClientInfoDto]. */
@Serializable
data class TranscodeDecisionDto(
    val canDirectPlay: Boolean = false,
    val canTranscode: Boolean = false,
    val transcodeReason: List<String> = emptyList(),
    val errorReason: String? = null,
    /** An opaque token naming the decision, which `getTranscodeStream` streams. */
    val transcodeParams: String? = null,
    val sourceStream: StreamDetailsDto? = null,
    val transcodeStream: StreamDetailsDto? = null
)

/** A stream's format. [audioBitrate] is in bits per second. */
@Serializable
data class StreamDetailsDto(
    val protocol: String? = null,
    val container: String? = null,
    val codec: String? = null,
    val audioChannels: Int? = null,
    val audioBitrate: Int? = null,
    val audioSamplerate: Int? = null,
    val audioBitdepth: Int? = null
)

/** What the client plays, for `getTranscodeDecision`. Bitrates are in bits per second. */
@Serializable
data class ClientInfoDto(
    val name: String,
    val platform: String,
    val maxAudioBitrate: Int? = null,
    val maxTranscodingAudioBitrate: Int? = null,
    val directPlayProfiles: List<DirectPlayProfileDto>,
    val transcodingProfiles: List<TranscodingProfileDto>,
    val codecProfiles: List<CodecProfileDto> = emptyList()
)

@Serializable
data class DirectPlayProfileDto(
    val containers: List<String>,
    val audioCodecs: List<String> = emptyList(),
    val protocols: List<String> = listOf("http"),
    val maxAudioChannels: Int? = null
)

@Serializable
data class TranscodingProfileDto(
    val container: String,
    val audioCodec: String,
    val protocol: String = "http",
    val maxAudioChannels: Int? = null
)

@Serializable
data class CodecProfileDto(
    val type: String,
    val name: String
)
