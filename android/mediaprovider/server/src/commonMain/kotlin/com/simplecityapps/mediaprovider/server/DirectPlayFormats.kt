package com.simplecityapps.mediaprovider.server

/** The formats the player decodes as they are, so a server can send the original file instead of a transcode. */
object DirectPlayFormats {
    /** Containers (file extensions) the player plays directly. */
    val containers = setOf("mp3", "aac", "m4a", "m4b", "mp4", "flac", "ogg", "oga", "opus", "wav", "webm", "weba")

    // ALAC has no MediaCodec decoder on Android, so it needs transcoding even inside a container the player
    // otherwise plays directly (m4a/mp4), same as Jellyfin transcodes it server-side.
    val undecodableCodecs = setOf("alac")

    /**
     * Whether the player decodes a file in [container] (an extension) encoded with [audioCodec] as it is. An unknown
     * container or codec plays as it is. The container names the wrapper, not what's inside, so an undecodable codec
     * such as ALAC is rejected even inside a playable container like `.m4a` (#567).
     */
    fun isDecodable(
        container: String?,
        audioCodec: String?
    ): Boolean {
        val containerDecodable = container.isNullOrEmpty() || container.lowercase() in containers
        val codecDecodable = audioCodec.isNullOrEmpty() || audioCodec.lowercase() !in undecodableCodecs
        return containerDecodable && codecDecodable
    }
}
