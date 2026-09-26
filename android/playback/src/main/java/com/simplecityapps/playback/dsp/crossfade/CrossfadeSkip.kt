package com.simplecityapps.playback.dsp.crossfade

import com.simplecityapps.shuttle.analytics.Analytics

/**
 * Why one song played into the next without the crossfade it should have had: [Crossfade] reports one for each such
 * transition, so the testing tracks show how often, and why, crossfade silently doesn't happen on real devices.
 */
enum class CrossfadeSkip(val value: String) {
    /** The tail wasn't decoded in time to clip the playing item: it landed too close to the item's end, or never. */
    TailLate("tail_late"),

    /** The stream can't seek, so its tail can't be decoded ahead of time. */
    Unseekable("unseekable"),

    /** Decoding the tail failed, or produced no audio. */
    DecodeFailed("decode_failed"),

    /** The songs' sample rates or channel counts differ, so the tail played out unfaded instead (mixing needs a resampler). */
    FormatMismatch("format_mismatch"),

    /**
     * The tail landed after the player had loaded the playing item to its end (a short song, usually): moving its clip
     * then would have thrown away the next item's preload.
     */
    ShortSongLoaded("short_song_loaded"),

    /** The next song didn't follow gaplessly in the audio sink, so the tail had nothing to mix into. */
    NotGapless("not_gapless"),

    /** Playback is on a Cast receiver, which plays each song whole. */
    Cast("cast"),

    /** The output is float PCM, which the audio sink writes without running the app's processors, the mixer among them. */
    FloatOutput("float_output"),

    /** Audio offload is on: the hardware decodes, and the app's processors, the mixer among them, never see the audio. */
    Offload("offload")
}

/** Records a [CrossfadeSkip] as a `crossfade_skipped` event, with its [CrossfadeSkip.value] as the `reason`. */
fun Analytics.crossfadeSkipped(reason: CrossfadeSkip) = capture("crossfade_skipped", mapOf("reason" to reason.value))
