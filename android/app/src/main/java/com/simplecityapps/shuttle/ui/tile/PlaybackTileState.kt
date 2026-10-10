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
            hasQueue: Boolean,
            isRestored: Boolean = true
        ): PlaybackTileState = PlaybackTileState(
            isActive = hasQueue && playbackState == PlaybackState.Playing,
            subtitle = currentTitle?.takeIf { hasQueue && it.isNotBlank() },
            tapAction = tapAction(hasQueue, isRestored)
        )

        /** Until the saved queue is restored an empty queue means "not known yet", so a tap toggles; the service waits for the restore. */
        fun tapAction(
            hasQueue: Boolean,
            isRestored: Boolean
        ): TapAction = if (hasQueue || !isRestored) TapAction.TogglePlayback else TapAction.OpenApp
    }
}
