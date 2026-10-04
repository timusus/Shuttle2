package com.simplecityapps.shuttle.sources

import com.simplecityapps.mediaprovider.server.ServerCredentialStore
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.ui.shell.ServerSessions
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultServerSessions @Inject constructor(
    @Named("JellyfinCredentialStore") jellyfin: ServerCredentialStore,
    @Named("EmbyCredentialStore") emby: ServerCredentialStore,
    @Named("PlexCredentialStore") plex: ServerCredentialStore,
) : ServerSessions {
    override val expired: Flow<MediaProviderType> = merge(
        jellyfin.sessionExpired.map { MediaProviderType.Jellyfin },
        emby.sessionExpired.map { MediaProviderType.Emby },
        plex.sessionExpired.map { MediaProviderType.Plex },
    )
}
