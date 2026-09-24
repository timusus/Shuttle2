package com.simplecityapps.playback

import android.media.AudioFormat

/** A mixer configuration a USB audio device offers (an [android.media.AudioMixerAttributes]), reduced to what matching needs. */
data class MixerFormat(
    val sampleRate: Int,
    val channelCount: Int,
    val encoding: Int,
    val isBitPerfect: Boolean
)

/** The PCM stream the player writes to its AudioTrack for a song. */
data class OutputFormat(
    val sampleRate: Int,
    val channelCount: Int,
    val encoding: Int
) {
    companion object {
        /**
         * ExoPlayer's audio sink converts decoded PCM of any bit depth to 16-bit before the app's processors,
         * since float output is off, so the AudioTrack is always 16-bit at the source's sample rate and
         * channel count. Null when either is unknown.
         */
        fun of(
            sampleRate: Int?,
            channelCount: Int?
        ): OutputFormat? {
            if (sampleRate == null || sampleRate <= 0 || channelCount == null || channelCount <= 0) return null
            return OutputFormat(sampleRate, channelCount, AudioFormat.ENCODING_PCM_16BIT)
        }
    }
}

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
