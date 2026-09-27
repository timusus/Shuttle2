package com.simplecityapps.shuttle.ui.screens.sources

import com.simplecityapps.shuttle.model.MediaProviderType
import dev.zacsweers.metro.Inject

/** A server's sign-in dialog succeeded: enable its provider and start a scan, wherever the sign-in happened. */
class ConnectServer @Inject constructor(
    private val mediaSources: MediaSources,
) {
    operator fun invoke(type: MediaProviderType) {
        mediaSources.enable(type)
        mediaSources.scan()
    }
}
