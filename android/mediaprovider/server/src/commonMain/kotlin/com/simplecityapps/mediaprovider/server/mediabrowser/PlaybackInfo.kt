@file:OptIn(ExperimentalSerializationApi::class)

package com.simplecityapps.mediaprovider.server.mediabrowser

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The `PlaybackInfo` request: the same profile the universal endpoint builds from its query. */
@Serializable
data class PlaybackInfoRequest(
    @SerialName("DeviceProfile") val deviceProfile: DeviceProfile
)

@Serializable
data class DeviceProfile(
    @SerialName("MaxStreamingBitrate") @EncodeDefault(EncodeDefault.Mode.NEVER) val maxStreamingBitrate: Int? = null,
    @SerialName("DirectPlayProfiles") val directPlayProfiles: List<DirectPlayProfile>,
    @SerialName("TranscodingProfiles") val transcodingProfiles: List<TranscodingProfile>
)

/** One `Container=` entry: a container, and the codec it's limited to (none accepting any). */
@Serializable
data class DirectPlayProfile(
    @SerialName("Type") val type: String = "Audio",
    @SerialName("Container") val container: String,
    @SerialName("AudioCodec") @EncodeDefault(EncodeDefault.Mode.NEVER) val audioCodec: String? = null
)

@Serializable
data class TranscodingProfile(
    @SerialName("Type") val type: String = "Audio",
    @SerialName("Container") val container: String,
    @SerialName("AudioCodec") val audioCodec: String,
    @SerialName("Protocol") val protocol: String,
    @SerialName("Context") val context: String = "Streaming"
)

@Serializable
data class PlaybackInfo(
    @SerialName("MediaSources") val mediaSources: List<MediaSource> = emptyList()
)

/**
 * How the server will send a source. The universal endpoint serves the original only when `SupportsDirectStream` is
 * set (`isStatic = mediaSource.SupportsDirectStream`, UniversalAudioController.cs); `SupportsDirectPlay` alone only
 * allows a redirect for remote HTTP sources, so it doesn't make the stream a file.
 */
@Serializable
data class MediaSource(
    @SerialName("SupportsDirectPlay") val supportsDirectPlay: Boolean = false,
    @SerialName("SupportsDirectStream") val supportsDirectStream: Boolean = false
) {
    val transcodes: Boolean get() = !supportsDirectStream
}
