package com.simplecityapps.shuttle.ui.shell

import com.simplecityapps.shuttle.model.MediaProviderType
import kotlinx.coroutines.flow.Flow

/**
 * The signed-in media servers' sessions. `DefaultServerSessions` (`:android:mediaprovider:server`, common to both
 * platforms) merges the Jellyfin, Emby, Plex and Subsonic credential stores' `sessionExpired` signals (#577), so the
 * shell can tell the user which server signed them out.
 */
interface ServerSessions {
    /** The server whose session the server rejected mid-session, each time one is cleared. */
    val expired: Flow<MediaProviderType>
}
