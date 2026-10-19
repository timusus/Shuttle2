package com.simplecityapps.provider.emby

import com.simplecityapps.mediaprovider.server.ServerStrings
import com.simplecityapps.mediaprovider.server.mediabrowser.ItemsService
import com.simplecityapps.mediaprovider.server.mediabrowser.MediaBrowserMediaProvider
import kotlin.time.Clock

class EmbyMediaProvider(
    strings: ServerStrings,
    authenticationManager: EmbyAuthenticationManager,
    itemsService: ItemsService,
    clock: Clock
) : MediaBrowserMediaProvider(strings, authenticationManager, itemsService, clock)
