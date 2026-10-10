package com.simplecityapps.provider.emby

import com.simplecityapps.mediaprovider.server.ServerStrings
import com.simplecityapps.mediaprovider.server.mediabrowser.ItemsService
import com.simplecityapps.mediaprovider.server.mediabrowser.MediaBrowserMediaProvider

class EmbyMediaProvider(
    strings: ServerStrings,
    authenticationManager: EmbyAuthenticationManager,
    itemsService: ItemsService
) : MediaBrowserMediaProvider(strings, authenticationManager, itemsService)
