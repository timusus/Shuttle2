package com.simplecityapps.provider.jellyfin.di

import com.simplecityapps.mediaprovider.MediaProviderTypeKey
import com.simplecityapps.mediaprovider.server.QuickConnectAuthentication
import com.simplecityapps.provider.jellyfin.JellyfinQuickConnectAuthentication
import com.simplecityapps.shuttle.model.MediaProviderType
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap

@ContributesTo(AppScope::class)
@BindingContainer
abstract class JellyfinQuickConnectModule {
    @Binds
    @IntoMap
    @MediaProviderTypeKey(MediaProviderType.Jellyfin)
    abstract fun bindJellyfinQuickConnect(authentication: JellyfinQuickConnectAuthentication): QuickConnectAuthentication
}
