package com.simplecityapps.mediaprovider.server

import com.simplecityapps.shuttle.settings.TranscodeFormat
import com.simplecityapps.shuttle.streaming.DeliveredFormat

/**
 * What a platform's player plays: for Jellyfin and Emby's universal audio endpoint, the formats it direct-plays and
 * what the server transcodes everything else to ([streamTarget]); for Plex, which serves a part file as it is, the
 * [directPlayFormats] that decide between it and a transcode, which [streamTarget] shapes the same way. Each platform
 * binds one: [Android] (AndroidStreamProfileModule), [Ios] (:shared's playback module).
 */
data class StreamProfile(
    /** The `Container=` value: `container|codec` entries, a bare container accepting any codec. */
    val directPlayContainers: String,
    /** Whether the player plays HLS, so a streamed transcode is segmented and stays seekable. */
    val playsHls: Boolean,
    /** The codec [TranscodeFormat.Auto] transcodes to. */
    val autoCodec: TranscodeCodec,
    /** The files the player decodes as they are, for a server that serves the original file (Plex). */
    val directPlayFormats: DirectPlayFormats
) {
    /**
     * What a stream is transcoded to in [format]: HLS segments where the player plays HLS, else one progressive file.
     * Opus in HLS would need fragmented MP4 segments, which no server's HLS output is known to play in Media3, so an
     * HLS stream falls back to AAC; a progressive stream (and a download) keeps Opus.
     */
    fun streamTarget(format: TranscodeFormat): TranscodeTarget {
        val codec = codec(format)
        return TranscodeTarget(if (playsHls && codec == TranscodeCodec.Opus) TranscodeCodec.Aac else codec, hls = playsHls)
    }

    /** What a download is transcoded to in [format]: always one progressive file, since a download is saved as one. */
    fun downloadTarget(format: TranscodeFormat): TranscodeTarget = TranscodeTarget(codec(format), hls = false)

    private fun codec(format: TranscodeFormat): TranscodeCodec = when (format) {
        TranscodeFormat.Auto -> autoCodec
        TranscodeFormat.Opus -> TranscodeCodec.Opus
        TranscodeFormat.Aac -> TranscodeCodec.Aac
        TranscodeFormat.Mp3 -> TranscodeCodec.Mp3
    }

    companion object {
        /**
         * Media3 plays HLS, so a transcode is in HLS segments, which stay seekable: AAC unless the user picks another
         * codec. Direct play is a subset of [DirectPlayFormats.Android] (webma being Jellyfin's name for weba).
         */
        val Android = StreamProfile(
            directPlayContainers = "opus,mp3|mp3,aac|aac,m4a|aac,m4b|aac,flac,webma,webm,wav,ogg",
            playsHls = true,
            autoCodec = TranscodeCodec.Aac,
            directPlayFormats = DirectPlayFormats.Android
        )

        /**
         * The iOS engine's FFmpeg (shuttle-playback's scripts/build-ffmpeg.sh) has no network or HLS demuxers, so a transcode is one
         * progressive stream, MP3 unless the user picks another codec: both servers label it `audio/mpeg` (Emby calls
         * its ADTS AAC `audio/mp4`). Direct play covers the build's demuxers (ogg, matroska, which reads WebM too, wav,
         * flac, mov, mp3, aac, aiff) with the codecs it decodes; `mov` holding AAC or ALAC. Jellyfin names WebM audio
         * `webma`, and reports an MP4 family file as `mov,mp4,m4a,...`, which matches `m4a`.
         */
        val Ios = StreamProfile(
            directPlayContainers = "mp3|mp3,aac|aac,m4a|aac,m4a|alac,m4b|aac,m4b|alac,mp4|aac,mp4|alac," +
                "flac,ogg,oga,opus,mka,matroska,webm,webma,wav,aiff,aif",
            playsHls = false,
            autoCodec = TranscodeCodec.Mp3,
            directPlayFormats = DirectPlayFormats.Ios
        )

        /** `StartTimeTicks` counts 100 ns ticks. */
        fun startTimeTicks(positionMs: Long): Long = positionMs * 10_000
    }
}

/**
 * A codec a server transcodes to: its `AudioCodec=` name, the container a progressive file of it comes in, the
 * container its HLS segments use, and the MIME type of the progressive file. Opus has no MPEG-TS mapping, so its
 * segments would be fragmented MP4 ([StreamProfile.streamTarget] never streams it in HLS). [label] is how Now Playing names it.
 */
enum class TranscodeCodec(
    val codec: String,
    val progressiveContainer: String,
    val segmentContainer: String,
    val mimeType: String,
    val label: String
) {
    Aac(codec = "aac", progressiveContainer = "aac", segmentContainer = "ts", mimeType = "audio/aac", label = "AAC"),
    Mp3(codec = "mp3", progressiveContainer = "mp3", segmentContainer = "ts", mimeType = "audio/mpeg", label = "MP3"),
    Opus(codec = "opus", progressiveContainer = "ogg", segmentContainer = "mp4", mimeType = "audio/ogg", label = "OPUS");

    /** A stream of this codec at [bitrateKbps] (null when the server picks it), as Now Playing's badge shows it. */
    fun delivered(bitrateKbps: Int?): DeliveredFormat = DeliveredFormat(label, bitrateKbps)
}

/** A transcode: [codec], in HLS segments or one progressive file. */
data class TranscodeTarget(
    val codec: TranscodeCodec,
    val hls: Boolean
) {
    /** The `TranscodingContainer=` value: the segments' container for HLS, else the file's. */
    val container: String get() = if (hls) codec.segmentContainer else codec.progressiveContainer

    /** The `TranscodingProtocol=` value. */
    val protocol: String get() = if (hls) "hls" else "http"

    /** The MIME type of what the URL serves: the playlist for HLS, else the file. */
    val mimeType: String get() = if (hls) HLS_MIME_TYPE else codec.mimeType

    companion object {
        const val HLS_MIME_TYPE = "application/x-mpegURL"
    }
}
