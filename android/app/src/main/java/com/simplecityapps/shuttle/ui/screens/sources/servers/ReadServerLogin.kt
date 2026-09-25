package com.simplecityapps.shuttle.ui.screens.sources.servers

import com.simplecityapps.shuttle.model.MediaProviderType
import javax.inject.Inject

/** A [type] server's saved address and login, to fill its sign-in form. */
class ReadServerLogin @Inject constructor(
    private val authentications: Map<MediaProviderType, @JvmSuppressWildcards ServerAuthentication>,
) {
    operator fun invoke(type: MediaProviderType): SavedServerLogin = authentications.getValue(type).savedLogin()
}
