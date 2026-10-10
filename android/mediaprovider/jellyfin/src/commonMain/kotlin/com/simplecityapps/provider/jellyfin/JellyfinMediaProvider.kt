package com.simplecityapps.provider.jellyfin

import com.simplecityapps.mediaprovider.server.ServerStrings
import com.simplecityapps.mediaprovider.server.mediabrowser.ItemsService
import com.simplecityapps.mediaprovider.server.mediabrowser.MediaBrowserMediaProvider

class JellyfinMediaProvider(
    strings: ServerStrings,
    authenticationManager: JellyfinAuthenticationManager,
    itemsService: ItemsService
) : MediaBrowserMediaProvider(strings, authenticationManager, itemsService)
