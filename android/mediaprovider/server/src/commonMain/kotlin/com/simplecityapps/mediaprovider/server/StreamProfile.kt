package com.simplecityapps.mediaprovider.server

/**
 * What a platform's player plays: for Jellyfin and Emby's universal audio endpoint, the formats it direct-plays and
 * what the server transcodes everything else to; for Plex, which serves a part file as it is, the [directPlayFormats]
 * that decide between it and a transcode, which [transcodingProtocol] shapes the same way. Each platform binds one:
 * [Android] (AndroidStreamProfileModule), [Ios] (:shared's playback module).
 */
data class StreamProfile(
    /** The `Container=` value: `container|codec` entries, a bare container accepting any codec. */
    val directPlayContainers: String,
    /** The `TranscodingContainer=` value. */
    val transcodingContainer: String,
    /** The `TranscodingProtocol=` value: `hls` for a segmented transcode, `http` for a progressive one. */
    val transcodingProtocol: String,
    /** The `AudioCodec=` value: the codec a transcode is encoded with. */
    val transcodingAudioCodec: String,
    /** The files the player decodes as they are, for a server that serves the original file (Plex). */
    val directPlayFormats: DirectPlayFormats
) {
    companion object {
        /**
         * Media3 plays HLS, so a transcode is AAC in HLS segments, which stay seekable. Direct play is a subset of
         * [DirectPlayFormats.Android] (webma being Jellyfin's name for weba).
         */
        val Android = StreamProfile(
            directPlayContainers = "opus,mp3|mp3,aac,m4a,m4b|aac,flac,webma,webm,wav,ogg",
            transcodingContainer = "ts",
            transcodingProtocol = "hls",
            transcodingAudioCodec = "aac",
            directPlayFormats = DirectPlayFormats.Android
        )

        /**
         * The iOS engine's FFmpeg (ios/scripts/build-ffmpeg.sh) has no network or HLS demuxers, so a transcode is one
         * progressive MP3 stream: both servers label it `audio/mpeg` (Emby calls its ADTS AAC `audio/mp4`) and its
         * constant bitrate keeps the byte count proportional to time. Direct play covers the build's demuxers (ogg,
         * matroska, which reads WebM too, wav, flac, mov, mp3, aac, aiff) with the codecs it decodes; `mov` holding
         * AAC or ALAC. Jellyfin names WebM audio `webma`, and reports an MP4 family file as `mov,mp4,m4a,...`, which
         * matches `m4a`.
         */
        val Ios = StreamProfile(
            directPlayContainers = "mp3|mp3,aac|aac,m4a|aac,m4a|alac,m4b|aac,m4b|alac,mp4|aac,mp4|alac," +
                "flac,ogg,oga,opus,mka,matroska,webm,webma,wav,aiff,aif",
            transcodingContainer = "mp3",
            transcodingProtocol = "http",
            transcodingAudioCodec = "mp3",
            directPlayFormats = DirectPlayFormats.Ios
        )

        /** `StartTimeTicks` counts 100 ns ticks. */
        fun startTimeTicks(positionMs: Long): Long = positionMs * 10_000
    }
}
