package com.simplecityapps.shuttle.ui.screens.sources.servers

import com.simplecityapps.mediaprovider.server.SavedServerLogin
import com.simplecityapps.mediaprovider.server.ServerAuthentication
import com.simplecityapps.shuttle.model.MediaProviderType
import dev.zacsweers.metro.Inject

/** A [type] server's saved address and login, to fill its sign-in form. */
class ReadServerLogin @Inject constructor(
    private val authentications: Map<MediaProviderType, ServerAuthentication>,
) {
    operator fun invoke(type: MediaProviderType): SavedServerLogin = authentications.getValue(type).savedLogin()
}
