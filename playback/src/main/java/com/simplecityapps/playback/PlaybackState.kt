package com.simplecityapps.playback

sealed class PlaybackState {
    object Loading : PlaybackState() {
        override fun toString(): String {
            return "Loading"
        }
    }
    object Playing : PlaybackState(){
        override fun toString(): String {
            return "Playing"
        }
    }
    object Paused : PlaybackState()
    override fun toString(): String {
        return "Paused"
    }
}