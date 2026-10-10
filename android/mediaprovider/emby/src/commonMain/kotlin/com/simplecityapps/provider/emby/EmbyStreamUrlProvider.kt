package com.simplecityapps.provider.emby

import com.simplecityapps.mediaprovider.StreamingPolicy
import com.simplecityapps.mediaprovider.server.mediabrowser.MediaBrowserStreamUrlProvider
import dev.zacsweers.metro.Inject

@Inject
class EmbyStreamUrlProvider(
    authenticationManager: EmbyAuthenticationManager,
    streamingPolicy: StreamingPolicy
) : MediaBrowserStreamUrlProvider(authenticationManager, streamingPolicy)
