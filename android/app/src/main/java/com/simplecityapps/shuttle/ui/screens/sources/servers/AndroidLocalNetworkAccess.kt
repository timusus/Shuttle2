package com.simplecityapps.shuttle.ui.screens.sources.servers

import android.content.Context
import com.simplecityapps.shuttle.di.ApplicationContext
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

@ContributesBinding(AppScope::class)
class AndroidLocalNetworkAccess @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocalNetworkAccess {
    override fun needsRequest(address: String) = needsRequestToSearch() && LocalNetworkPermission.isLocalAddress(address)

    override fun needsRequestToSearch() = LocalNetworkPermission.isEnforced(context) && !LocalNetworkPermission.isGranted(context)
}
