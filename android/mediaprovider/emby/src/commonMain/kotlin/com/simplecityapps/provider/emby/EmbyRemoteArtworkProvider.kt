package com.simplecityapps.provider.emby

import com.simplecityapps.mediaprovider.server.mediabrowser.ItemsService
import com.simplecityapps.mediaprovider.server.mediabrowser.MediaBrowserRemoteArtworkProvider
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named

class EmbyRemoteArtworkProvider
@Inject
constructor(
    authenticationManager: EmbyAuthenticationManager,
    @Named("EmbyItemsService") itemsService: ItemsService
) : MediaBrowserRemoteArtworkProvider(authenticationManager, itemsService)
