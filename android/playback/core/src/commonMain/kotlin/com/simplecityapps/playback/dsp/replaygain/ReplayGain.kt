package com.simplecityapps.playback.dsp.replaygain

import com.simplecityapps.shuttle.model.Song

/**
 * A song's ReplayGain tags, in dB.
 */
data class ReplayGain(
    val trackGain: Double?,
    val albumGain: Double?
)

val Song.replayGain: ReplayGain
    get() = ReplayGain(trackGain = replayGainTrack, albumGain = replayGainAlbum)

/**
 * The gain to apply, in dB, for [replayGain] under [mode]. Falls back to the other tag when the
 * preferred one is missing, and always includes the pre-amp.
 */
fun replayGainDb(
    mode: ReplayGainMode,
    preAmpGain: Double,
    replayGain: ReplayGain?
): Double = preAmpGain +
    when (mode) {
        ReplayGainMode.Track -> replayGain?.trackGain ?: replayGain?.albumGain ?: 0.0
        ReplayGainMode.Album -> replayGain?.albumGain ?: replayGain?.trackGain ?: 0.0
        ReplayGainMode.Off -> 0.0
    }
