package com.simplecityapps.mediaprovider

/**
 * A constant-bitrate transcode with no byte ranges to seek by, which its server starts at any whole second instead:
 * [urlAt] the stream from `offsetSeconds` in. At [bitrateKbps] a byte position stands for a time, so the player can
 * seek it as if it had byte ranges, the bytes from position `p` being those of [urlAt]`(p / bytesPerSecond)` (Subsonic's
 * transcodes).
 */
class TimeSeekableStream(
    val bitrateKbps: Int,
    val durationMs: Long,
    val urlAt: (offsetSeconds: Long) -> String
) {
    val bytesPerSecond: Long get() = bitrateKbps * 1000L / 8

    /** The size the whole stream comes to at [bitrateKbps], near enough. */
    val estimatedLength: Long get() = durationMs * bytesPerSecond / 1000
}
