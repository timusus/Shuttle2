package com.simplecityapps.shuttle.ui.shell

import com.simplecityapps.shuttle.model.MediaProviderType
import kotlinx.coroutines.flow.Flow

/** Merges each server's expired-session signal, so the shell can name the server that signed the user out. */
interface ServerSessions {
    /** The server whose session the server rejected mid-session, each time one is cleared. */
    val expired: Flow<MediaProviderType>
}
