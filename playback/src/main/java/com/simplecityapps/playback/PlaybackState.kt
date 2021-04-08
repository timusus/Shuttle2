package com.simplecityapps.playback

sealed class PlaybackState {
    object Loading : PlaybackState()
    object Playing : PlaybackState()
    object Paused : PlaybackState()
}