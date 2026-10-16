package com.simplecityapps.playback.dsp.replaygain

import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.model.workProgress

/**
 * A song's ReplayGain tags, in dB.
 */
data class ReplayGain(
    val trackGain: Double?,
    val albumGain: Double?
)

/** The ReplayGain pre-amp's range, ±dB (`PlaybackSettings.PreAmpGain`). */
const val MAX_REPLAY_GAIN_PREAMP_DB = 12

val Song.replayGain: ReplayGain
    get() = ReplayGain(trackGain = replayGainTrack, albumGain = replayGainAlbum)

/**
 * The mode to apply to the song at [index] of [queue]. In Track mode a movement of a work (two or more consecutive
 * movements, as the album and now-playing views group them) uses its album gain, so a quiet Adagio keeps its level
 * against the Allegro around it. A song without an album tag, and every other mode, keeps [mode].
 */
fun replayGainModeAt(
    mode: ReplayGainMode,
    queue: List<Song>,
    index: Int
): ReplayGainMode {
    if (mode != ReplayGainMode.Track) return mode
    if (queue.getOrNull(index)?.replayGainAlbum == null) return mode
    return if (workProgress(queue, index) != null) ReplayGainMode.Album else mode
}

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
