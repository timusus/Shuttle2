package com.simplecityapps.provider.subsonic

import com.simplecityapps.mediaprovider.DownloadSource
import com.simplecityapps.mediaprovider.StreamUrlProvider
import com.simplecityapps.mediaprovider.StreamingPolicy
import com.simplecityapps.mediaprovider.TimeSeekableStream
import com.simplecityapps.mediaprovider.isLosslessCodec
import com.simplecityapps.mediaprovider.server.AuthenticatedCredentials
import com.simplecityapps.mediaprovider.server.StreamProfile
import com.simplecityapps.mediaprovider.server.TranscodeCodec
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.provider.subsonic.http.ClientInfoDto
import com.simplecityapps.provider.subsonic.http.DirectPlayProfileDto
import com.simplecityapps.provider.subsonic.http.SubsonicService
import com.simplecityapps.provider.subsonic.http.TranscodingProfileDto
import com.simplecityapps.shuttle.logging.Logger
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.settings.TranscodeFormat
import com.simplecityapps.shuttle.streaming.DeliveredFormat

/**
 * Where a song streams from. A transcode the server can start part way in has [timeSeek]: it has no byte ranges to seek
 * by, so a seek re-requests it from that time. A transcode without one can't be seeked. [delivered] is the transcode's
 * codec and bitrate, null for the original file.
 */
data class SubsonicStream(
    val url: String,
    val mimeType: String,
    val timeSeek: TimeSeekableStream? = null,
    val delivered: DeliveredFormat? = null
)

/**
 * A Subsonic song's stream, under the current [StreamingPolicy]:
 *
 * 1. The original file (`stream?format=raw`), seekable by byte range, when the player decodes it and it's within the cap.
 * 2. Otherwise, on a server with OpenSubsonic's `transcoding` extension, what `getTranscodeDecision` decides for this
 *    player: the original after all, or a transcode (`getTranscodeStream`), the chosen codec first, then MP3.
 * 3. Otherwise, or when that fails, the classic `stream?format=<codec>&maxBitRate=...` transcode, which every server
 *    has, in the chosen codec ([transcodeCodec]).
 *
 * A transcode is seekable by time where the server can start it part way in: `getTranscodeStream` always takes an
 * `offset` in seconds (it's part of the `transcoding` extension), while the classic `stream` honours `timeOffset` for
 * music only on a server listing the `transcodeOffset` extension (Subsonic documents it for video), so elsewhere it
 * isn't seekable. A transcode is never at a higher bitrate than a lossy source's own (see [transcodeKbps]).
 *
 * Every URL carries freshly salted credentials.
 */
class SubsonicStreams(
    private val authenticationManager: SubsonicAuthenticationManager,
    private val service: SubsonicService,
    private val streamingPolicy: StreamingPolicy,
    private val streamProfile: StreamProfile,
    private val clientName: String
) {
    private val logger = Logger.tagged("SubsonicStreams")

    /** @throws IllegalStateException when the server isn't signed in to. */
    suspend fun stream(song: Song): SubsonicStream {
        val signedIn = signedIn()
        val maxBitrateKbps = streamingPolicy.maxBitrateKbps()
        if (isDecodable(song) && isWithinCap(song, maxBitrateKbps)) return opened(song, direct(signedIn, song))
        if (authenticationManager.serverInfo?.supports(SubsonicServerInfo.TRANSCODING) == true) {
            decided(signedIn, song, maxBitrateKbps)?.let { return opened(song, it) }
        }
        return opened(song, legacyTranscode(signedIn, song, maxBitrateKbps, transcodeCodec()))
    }

    /**
     * [stream] without asking the server, for a caller that can't wait on it: the original, or the classic MP3 transcode
     * from [startPositionMs] (from the start, on a server that can't start it part way in).
     *
     * @throws IllegalStateException when the server isn't signed in to.
     */
    fun immediateStream(song: Song, startPositionMs: Long = 0): SubsonicStream {
        val signedIn = signedIn()
        val maxBitrateKbps = streamingPolicy.maxBitrateKbps()
        if (isDecodable(song) && isWithinCap(song, maxBitrateKbps)) return opened(song, direct(signedIn, song))
        val transcode = legacyTranscode(signedIn, song, maxBitrateKbps, transcodeCodec())
        val timeSeek = transcode.timeSeek
        return opened(song, if (startPositionMs > 0 && timeSeek != null) transcode.copy(url = timeSeek.urlAt(startPositionMs / 1000)) else transcode)
    }

    /**
     * For Cast, which can't be handed a seek of our own: the original where it plays it, otherwise the classic MP3
     * transcode, whatever the chosen codec (every receiver plays MP3), which the server sizes (`estimateContentLength`)
     * so the receiver can seek it.
     */
    fun castStream(song: Song): SubsonicStream {
        val signedIn = signedIn()
        val maxBitrateKbps = streamingPolicy.maxBitrateKbps()
        if (isDecodable(song) && isWithinCap(song, maxBitrateKbps)) return opened(song, direct(signedIn, song))
        return opened(song, legacyTranscode(signedIn, song, maxBitrateKbps, TranscodeCodec.Mp3).copy(timeSeek = null))
    }

    /** Reports what [song]'s stream delivers, for Now Playing's badge, and hands it on. */
    private fun opened(song: Song, stream: SubsonicStream): SubsonicStream {
        streamingPolicy.streamOpened(song.path, stream.delivered)
        return stream
    }

    /**
     * The original file (`download`) where the player decodes it and it's within the download cap, recorded as the
     * song's own type; otherwise the classic transcode in the chosen codec at the cap (uncapped without one), so the
     * download is something it plays. A downloaded file seeks by its own index, so Opus is fine on Android here.
     */
    fun downloadSource(song: Song): DownloadSource? {
        val signedIn = runCatching { signedIn() }.getOrNull() ?: return null
        val maxBitrateKbps = streamingPolicy.downloadMaxBitrateKbps()
        if (isDecodable(song) && isWithinCap(song, maxBitrateKbps)) {
            return DownloadSource(service.url(signedIn.address, "download", signedIn.auth(), "id" to song.externalId), song.mimeType)
        }
        val transcode = legacyTranscode(signedIn, song, maxBitrateKbps, codecFor(streamingPolicy.transcodeFormat()))
        return DownloadSource(transcode.url, transcode.mimeType)
    }

    /**
     * The codec a stream is transcoded to: the chosen one, Auto being MP3 on both platforms. Media3 seeks a time-seekable
     * transcode by mapping bytes to times at a constant bitrate, which works for MP3 and ADTS AAC but not Ogg, whose
     * seeker reads granule positions; so a streamed Opus transcode is MP3 on Android (which plays HLS), and Opus on iOS,
     * whose engine re-opens a transcode at the time sought instead.
     */
    internal fun transcodeCodec(): TranscodeCodec {
        val codec = codecFor(streamingPolicy.transcodeFormat())
        return if (codec == TranscodeCodec.Opus && streamProfile.playsHls) TranscodeCodec.Mp3 else codec
    }

    private fun codecFor(format: TranscodeFormat): TranscodeCodec = when (format) {
        TranscodeFormat.Auto, TranscodeFormat.Mp3 -> TranscodeCodec.Mp3
        TranscodeFormat.Aac -> TranscodeCodec.Aac
        TranscodeFormat.Opus -> TranscodeCodec.Opus
    }

    fun isDecodable(song: Song): Boolean = streamProfile.directPlayFormats.isDecodable(container = song.container(), audioCodec = song.audioCodec)

    private fun isWithinCap(song: Song, maxBitrateKbps: Int?): Boolean {
        val bitRate = song.bitRate
        return maxBitrateKbps == null || (bitRate != null && bitRate <= maxBitrateKbps)
    }

    private fun direct(signedIn: SignedIn, song: Song) = SubsonicStream(
        url = service.url(signedIn.address, "stream", signedIn.auth(), "id" to song.externalId, "format" to "raw"),
        mimeType = song.mimeType
    )

    /**
     * The classic transcode: [codec] at [transcodeKbps] (`format` naming one of the server's transcodings, which have
     * the codecs' own names), which `timeOffset` starts part way in on a server with the `transcodeOffset` extension.
     */
    private fun legacyTranscode(signedIn: SignedIn, song: Song, maxBitrateKbps: Int?, codec: TranscodeCodec): SubsonicStream {
        val kbps = transcodeKbps(song, maxBitrateKbps)
        val urlAt = { offsetSeconds: Long ->
            service.url(
                signedIn.address,
                "stream",
                signedIn.auth(),
                "id" to song.externalId,
                "format" to codec.codec,
                "maxBitRate" to kbps,
                "estimateContentLength" to true,
                "timeOffset" to offsetSeconds.takeIf { it > 0 }
            )
        }
        val timeSeek = TimeSeekableStream(kbps, song.duration.toLong(), urlAt).takeIf { authenticationManager.serverInfo?.supports(SubsonicServerInfo.TRANSCODE_OFFSET) == true }
        return SubsonicStream(urlAt(0), codec.mimeType, timeSeek, codec.delivered(kbps))
    }

    /** What `getTranscodeDecision` says; null when it fails, or the server can do neither. */
    private suspend fun decided(signedIn: SignedIn, song: Song, maxBitrateKbps: Int?): SubsonicStream? {
        val id = song.externalId ?: return null
        val result = authenticationManager.request(signedIn.credentials) { auth -> service.transcodeDecision(signedIn.address, auth, id, clientInfo(maxBitrateKbps, transcodeKbps(song, maxBitrateKbps))) }
        val decision = when (result) {
            is NetworkResult.Success -> result.body

            is NetworkResult.Failure -> {
                logger.warn(result.error) { "getTranscodeDecision failed; falling back to the classic transcode" }
                return null
            }
        }
        val params = decision.transcodeParams
        return when {
            decision.canDirectPlay -> direct(signedIn, song)

            decision.canTranscode && params != null -> {
                val kbps = decision.transcodeStream?.audioBitrate?.takeIf { it > 0 }?.let { it / 1000 } ?: transcodeKbps(song, maxBitrateKbps)
                // getTranscodeStream's offset (seconds) is part of the transcoding extension, so no transcodeOffset to check
                val urlAt = { offsetSeconds: Long ->
                    service.url(
                        signedIn.address,
                        "getTranscodeStream",
                        signedIn.auth(),
                        "mediaId" to id,
                        "mediaType" to "song",
                        "transcodeParams" to params,
                        "offset" to offsetSeconds.takeIf { it > 0 }
                    )
                }
                val codec = (decision.transcodeStream?.codec ?: decision.transcodeStream?.container ?: TranscodeCodec.Mp3.codec).uppercase()
                SubsonicStream(urlAt(0), mimeTypeOf(decision.transcodeStream?.container), TimeSeekableStream(kbps, song.duration.toLong(), urlAt), DeliveredFormat(codec, kbps))
            }

            else -> {
                logger.warn { "The server can neither direct play nor transcode ${song.externalId}: ${decision.errorReason}" }
                null
            }
        }
    }

    /**
     * What this player plays, for `getTranscodeDecision`. Direct play is offered by container and codec (an `.m4a` of
     * ALAC isn't playable, one of AAC is), and the transcode targets are the chosen [transcodeCodec] first, then MP3: a
     * constant-bitrate MP3 is the one whose byte offsets map to times exactly, which [TimeSeekableStream] relies on. A
     * transcode is at most [transcodeKbps].
     */
    internal fun clientInfo(maxBitrateKbps: Int?, transcodeKbps: Int): ClientInfoDto {
        val containers = streamProfile.directPlayFormats.containers
        return ClientInfoDto(
            name = clientName,
            platform = if (streamProfile == StreamProfile.Ios) "iOS" else "Android",
            maxAudioBitrate = maxBitrateKbps?.let { it * 1000 },
            maxTranscodingAudioBitrate = transcodeKbps * 1000,
            directPlayProfiles = DIRECT_PLAY_CODECS.mapNotNull { (container, codecs) ->
                container.filter { it in containers }.takeIf { it.isNotEmpty() }?.let { DirectPlayProfileDto(containers = it, audioCodecs = codecs) }
            },
            transcodingProfiles = listOf(transcodeCodec(), TranscodeCodec.Mp3).distinct().map { codec ->
                TranscodingProfileDto(container = codec.progressiveContainer, audioCodec = codec.codec)
            }
        )
    }

    private fun signedIn(): SignedIn {
        val address = authenticationManager.getAddress() ?: throw IllegalStateException("No Subsonic server address")
        val credentials = authenticationManager.getAuthenticatedCredentials() ?: throw IllegalStateException("Failed to authenticate")
        return SignedIn(address, credentials)
    }

    private inner class SignedIn(
        val address: String,
        val credentials: AuthenticatedCredentials
    ) {
        fun auth() = authenticationManager.auth(credentials)
    }

    private fun mimeTypeOf(container: String?): String = when (container?.lowercase()) {
        null, "mp3" -> MP3_MIME_TYPE
        "ogg", "opus" -> "audio/ogg"
        "aac" -> "audio/aac"
        "m4a", "mp4" -> "audio/mp4"
        "flac" -> "audio/flac"
        else -> "audio/$container"
    }

    companion object {
        /** A transcode's bitrate with no cap set, the same as Plex's. */
        const val UNCAPPED_TRANSCODE_KBPS = 320

        const val MP3_MIME_TYPE = "audio/mpeg"

        /** The bitrates an MPEG-1 Layer III stream can have; an encoder asked for another picks the nearest of these. */
        private val MP3_BITRATES_KBPS = listOf(32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320)

        /**
         * The bitrate to transcode [song] at: the cap, or [UNCAPPED_TRANSCODE_KBPS] with none, but never above a lossy
         * source's own, which would only add bytes. It's rounded down to a bitrate MP3 has, since the encoder would round
         * any other to the nearest, and the bytes per second [TimeSeekableStream] maps would no longer be the stream's.
         */
        internal fun transcodeKbps(song: Song, maxBitrateKbps: Int?): Int {
            val kbps = maxBitrateKbps ?: UNCAPPED_TRANSCODE_KBPS
            val sourceKbps = song.bitRate?.takeIf { it > 0 && !isLosslessCodec(song.audioCodec) } ?: kbps
            val target = minOf(kbps, sourceKbps)
            return MP3_BITRATES_KBPS.lastOrNull { it <= target } ?: MP3_BITRATES_KBPS.first()
        }

        private val DIRECT_PLAY_CODECS = listOf(
            listOf("mp3") to listOf("mp3"),
            listOf("flac") to listOf("flac"),
            listOf("m4a", "m4b", "mp4", "aac") to listOf("aac"),
            listOf("ogg", "oga", "opus", "webm") to listOf("opus", "vorbis"),
            listOf("wav") to listOf("pcm")
        )
    }
}

/** The song's container, from its MIME type: Subsonic songs carry no file name. */
internal fun Song.container(): String? = when (val subtype = mimeType.substringAfter('/', "").lowercase()) {
    "", "*" -> null
    "mpeg", "mp3" -> "mp3"
    "mp4", "x-m4a", "m4a" -> "m4a"
    "x-flac", "flac" -> "flac"
    "wav", "x-wav", "wave" -> "wav"
    else -> subtype.removePrefix("x-")
}

/** A Subsonic song's stream, for the iOS player and downloads, which can't wait on the server's transcode decision. */
class SubsonicStreamUrlProvider(private val streams: SubsonicStreams) : StreamUrlProvider {
    override fun handles(scheme: String?): Boolean = scheme == "subsonic"

    override fun streamUrl(
        song: Song,
        startPositionMs: Long,
        playId: String?
    ): String = streams.immediateStream(song, startPositionMs).url

    override fun downloadSource(song: Song): DownloadSource? = streams.downloadSource(song)
}
