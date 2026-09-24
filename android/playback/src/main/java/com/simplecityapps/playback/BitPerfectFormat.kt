package com.simplecityapps.playback

/** A mixer configuration a USB audio device offers (an [android.media.AudioMixerAttributes]), reduced to what matching needs. */
data class MixerFormat(
    val sampleRate: Int,
    val channelCount: Int,
    val encoding: Int,
    val isBitPerfect: Boolean
)

/**
 * The PCM stream the player writes to its AudioTrack, as the audio sink reports it. With float output off, the
 * sink converts every bit depth to 16-bit after the app's processors, so [encoding] is 16-bit PCM.
 */
data class OutputFormat(
    val sampleRate: Int,
    val channelCount: Int,
    val encoding: Int
)

/**
 * The bit-perfect mixer format that carries [output] unchanged, or null when the device offers none. Only an
 * exact match will do: [android.media.AudioManager.setPreferredMixerAttributes] requires playback to use the
 * mixer's format, and a format that differs would mean resampling or requantising, which bit-perfect avoids.
 */
fun selectBitPerfectFormat(
    candidates: List<MixerFormat>,
    output: OutputFormat
): MixerFormat? = candidates.firstOrNull { candidate ->
    candidate.isBitPerfect &&
        candidate.sampleRate == output.sampleRate &&
        candidate.channelCount == output.channelCount &&
        candidate.encoding == output.encoding
}
