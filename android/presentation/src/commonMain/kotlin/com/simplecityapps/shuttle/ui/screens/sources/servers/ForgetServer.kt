package com.simplecityapps.shuttle.ui.screens.sources.servers

import com.simplecityapps.mediaprovider.server.ServerAuthentication
import com.simplecityapps.shuttle.model.MediaProviderType
import dev.zacsweers.metro.Inject

/** Forgets [MediaProviderType]'s server (its address, saved login and session), as removing it from Sources does. */
class ForgetServer @Inject constructor(
    private val authentications: Map<MediaProviderType, ServerAuthentication>,
) {
    operator fun invoke(type: MediaProviderType) {
        authentications[type]?.forgetServer()
    }
}
