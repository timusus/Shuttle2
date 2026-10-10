package com.simplecityapps.provider.jellyfin

import com.simplecityapps.mediaprovider.StreamingPolicy
import com.simplecityapps.mediaprovider.server.mediabrowser.MediaBrowserStreamUrlProvider
import dev.zacsweers.metro.Inject

@Inject
class JellyfinStreamUrlProvider(
    authenticationManager: JellyfinAuthenticationManager,
    streamingPolicy: StreamingPolicy
) : MediaBrowserStreamUrlProvider(authenticationManager, streamingPolicy)
