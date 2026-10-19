package com.simplecityapps.provider.jellyfin

import com.simplecityapps.mediaprovider.server.ServerStrings
import com.simplecityapps.mediaprovider.server.mediabrowser.ItemsService
import com.simplecityapps.mediaprovider.server.mediabrowser.MediaBrowserMediaProvider
import kotlin.time.Clock

class JellyfinMediaProvider(
    strings: ServerStrings,
    authenticationManager: JellyfinAuthenticationManager,
    itemsService: ItemsService,
    clock: Clock
) : MediaBrowserMediaProvider(strings, authenticationManager, itemsService, clock)
