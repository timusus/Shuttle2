package com.simplecityapps.shuttle.ui.screens.settings.scrobbling

import com.simplecityapps.shuttle.settings.ScrobblingSettings
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart

/** Whether songs from Jellyfin, Emby and Plex are scrobbled too. */
class ObserveScrobbleServerStreams @Inject constructor(
    private val settings: ScrobblingSettings
) {
    operator fun invoke(): Flow<Boolean> = settings.scrobbleServerStreams.flow
        .onStart { emit(settings.scrobbleServerStreams.value) }
        .distinctUntilChanged()
}

/** Turns scrobbling of server-streamed songs on or off. */
class SetScrobbleServerStreams @Inject constructor(
    private val settings: ScrobblingSettings
) {
    operator fun invoke(enabled: Boolean) {
        settings.scrobbleServerStreams.value = enabled
    }
}
