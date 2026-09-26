package com.simplecityapps.shuttle.ui.widgets

import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.queue.RepeatMode
import com.simplecityapps.playback.queue.ShuffleMode
import com.simplecityapps.shuttle.model.Song

enum class WidgetRepeatMode {
    Off,
    All,
    One
}

/**
 * Everything the now playing widget draws. Persisted, so a widget redrawn after process death shows the last
 * known track rather than an empty frame. The artwork is a path to a file on disk; bitmaps never go in state.
 */
data class NowPlayingWidgetState(
    val hasTrack: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val isPlaying: Boolean = false,
    val shuffleOn: Boolean = false,
    val repeatMode: WidgetRepeatMode = WidgetRepeatMode.Off,
    val artworkPath: String? = null,
    /** The widget background opacity setting, a percentage. */
    val backgroundOpacity: Int = 100
) {
    companion object {
        val Idle = NowPlayingWidgetState()
    }
}

fun nowPlayingWidgetState(
    song: Song?,
    playbackState: PlaybackState,
    shuffleMode: ShuffleMode,
    repeatMode: RepeatMode,
    artworkPath: String?,
    backgroundOpacity: Int = 100
): NowPlayingWidgetState {
    if (song == null) return NowPlayingWidgetState.Idle.copy(backgroundOpacity = backgroundOpacity)
    return NowPlayingWidgetState(
        hasTrack = true,
        title = song.name.orEmpty(),
        artist = (song.friendlyArtistName ?: song.albumArtist).orEmpty(),
        album = song.album.orEmpty(),
        // Loading means playback is starting, so offer pause.
        isPlaying = playbackState != PlaybackState.Paused,
        shuffleOn = shuffleMode == ShuffleMode.On,
        repeatMode =
            when (repeatMode) {
                RepeatMode.Off -> WidgetRepeatMode.Off
                RepeatMode.All -> WidgetRepeatMode.All
                RepeatMode.One -> WidgetRepeatMode.One
            },
        artworkPath = artworkPath,
        backgroundOpacity = backgroundOpacity
    )
}
