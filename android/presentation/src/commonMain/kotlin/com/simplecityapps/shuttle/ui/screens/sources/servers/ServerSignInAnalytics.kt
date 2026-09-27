package com.simplecityapps.shuttle.ui.screens.sources.servers

import com.simplecityapps.shuttle.model.MediaProviderType

/** Records a successful server sign-in for the monetisation funnel. */
fun interface ServerSignInAnalytics {
    fun onServerConnected(type: MediaProviderType)
}
