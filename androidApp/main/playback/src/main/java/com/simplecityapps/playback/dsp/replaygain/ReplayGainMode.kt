package com.simplecityapps.playback.dsp.replaygain

enum class ReplayGainMode {
    Track, Album, Off;

    companion object {
        fun init(ordinal: Int): ReplayGainMode {
            return when (ordinal) {
                Track.ordinal -> Track
                Album.ordinal -> Album
                Off.ordinal -> Off
                else -> Off
            }
        }
    }
}