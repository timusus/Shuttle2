package com.simplecityapps.playback

sealed class PlaybackState {
    object Loading : PlaybackState() {
        override fun toString(): String = "Loading"
    }

    object Playing : PlaybackState() {
        override fun toString(): String = "Playing"
    }

    object Paused : PlaybackState()

    override fun toString(): String = "Paused"
}
