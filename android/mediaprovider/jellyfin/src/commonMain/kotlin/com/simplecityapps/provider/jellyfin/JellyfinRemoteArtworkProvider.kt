package com.simplecityapps.provider.jellyfin

import com.simplecityapps.mediaprovider.server.mediabrowser.ItemsService
import com.simplecityapps.mediaprovider.server.mediabrowser.MediaBrowserRemoteArtworkProvider
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named

class JellyfinRemoteArtworkProvider
@Inject
constructor(
    authenticationManager: JellyfinAuthenticationManager,
    @Named("JellyfinItemsService") itemsService: ItemsService
) : MediaBrowserRemoteArtworkProvider(authenticationManager, itemsService)
