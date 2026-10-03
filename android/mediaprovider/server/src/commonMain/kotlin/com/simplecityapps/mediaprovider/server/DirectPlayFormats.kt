package com.simplecityapps.mediaprovider.server

/**
 * The formats a platform's player decodes as they are, so a server that serves a file as it is (Plex) can send the
 * original instead of a transcode. Each platform's [StreamProfile] carries its own: [Android], [Ios].
 */
data class DirectPlayFormats(
    /** Containers (file extensions) the player plays directly. */
    val containers: Set<String>,
    /** Codecs the player can't decode, even inside a container it otherwise plays. */
    val undecodableCodecs: Set<String>
) {
    /**
     * Whether the player decodes a file in [container] (an extension) encoded with [audioCodec] as it is. An unknown
     * container or codec plays as it is. The container names the wrapper, not what's inside, so an undecodable codec
     * such as ALAC on Android is rejected even inside a playable container like `.m4a` (#567).
     */
    fun isDecodable(
        container: String?,
        audioCodec: String?
    ): Boolean {
        val containerDecodable = container.isNullOrEmpty() || container.lowercase() in containers
        val codecDecodable = audioCodec.isNullOrEmpty() || audioCodec.lowercase() !in undecodableCodecs
        return containerDecodable && codecDecodable
    }

    companion object {
        /**
         * Media3's extractors. ALAC has no MediaCodec decoder on Android, so it needs transcoding even inside a
         * container the player otherwise plays directly (m4a/mp4), same as Jellyfin transcodes it server-side.
         */
        val Android = DirectPlayFormats(
            containers = setOf("mp3", "aac", "m4a", "m4b", "mp4", "flac", "ogg", "oga", "opus", "wav", "webm", "weba"),
            undecodableCodecs = setOf("alac")
        )

        /**
         * The iOS engine's FFmpeg build (ios/scripts/build-ffmpeg.sh): its demuxers (ogg, matroska, which reads WebM
         * and MKA, wav, flac, mov, mp3, aac, aiff) and decoders (flac, alac, opus, vorbis, mp3, aac, PCM). Any other
         * codec, even inside a container it demuxes (a Matroska file with AC3, an m4a with E-AC3), has no decoder, so
         * the server transcodes it instead of the player failing.
         */
        val Ios = DirectPlayFormats(
            containers = setOf("mp3", "aac", "m4a", "m4b", "mp4", "flac", "ogg", "oga", "opus", "wav", "webm", "weba", "mka", "aiff", "aif"),
            undecodableCodecs = setOf(
                "ac3", "eac3", "dts", "dca", "dts-hd", "dtshd", "truehd", "mlp",
                "wmav1", "wmav2", "wmapro", "wmalossless", "wma",
                "ape", "wavpack", "wv", "tta", "musepack", "mpc", "mp2",
                "adpcm", "adpcm_ima_wav", "adpcm_ima_qt", "adpcm_ms", "adpcm_swf", "adpcm_yamaha", "adpcm_g722", "adpcm_g726"
            )
        )
    }
}
