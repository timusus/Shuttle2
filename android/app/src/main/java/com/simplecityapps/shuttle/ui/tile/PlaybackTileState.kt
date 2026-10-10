package com.simplecityapps.shuttle.ui.tile

import com.simplecityapps.playback.PlaybackState

/** What the Quick Settings tile shows, and what a tap does. */
data class PlaybackTileState(
    val isActive: Boolean,
    val subtitle: String?,
    val tapAction: TapAction
) {
    enum class TapAction { TogglePlayback, OpenApp }

    companion object {
        fun from(
            playbackState: PlaybackState,
            currentTitle: String?,
            hasQueue: Boolean
        ): PlaybackTileState = PlaybackTileState(
            isActive = hasQueue && playbackState == PlaybackState.Playing,
            subtitle = currentTitle?.takeIf { hasQueue && it.isNotBlank() },
            tapAction = if (hasQueue) TapAction.TogglePlayback else TapAction.OpenApp
        )
    }
}
