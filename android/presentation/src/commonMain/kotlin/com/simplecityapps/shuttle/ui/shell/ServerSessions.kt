package com.simplecityapps.shuttle.ui.shell

import com.simplecityapps.shuttle.model.MediaProviderType
import kotlinx.coroutines.flow.Flow

/**
 * The signed-in media servers' sessions. Each platform merges its Jellyfin, Emby and Plex credential stores'
 * `sessionExpired` signals (#577), so the shell can tell the user which server signed them out.
 */
interface ServerSessions {
    /** The server whose session the server rejected mid-session, each time one is cleared. */
    val expired: Flow<MediaProviderType>
}
