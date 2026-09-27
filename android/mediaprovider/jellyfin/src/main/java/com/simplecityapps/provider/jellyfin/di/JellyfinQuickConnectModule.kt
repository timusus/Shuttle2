package com.simplecityapps.provider.jellyfin.di

import com.simplecityapps.mediaprovider.MediaProviderTypeKey
import com.simplecityapps.mediaprovider.server.QuickConnectAuthentication
import com.simplecityapps.provider.jellyfin.JellyfinQuickConnectAuthentication
import com.simplecityapps.shuttle.model.MediaProviderType
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap

@InstallIn(SingletonComponent::class)
@Module
abstract class JellyfinQuickConnectModule {
    @Binds
    @IntoMap
    @MediaProviderTypeKey(MediaProviderType.Jellyfin)
    abstract fun bindJellyfinQuickConnect(authentication: JellyfinQuickConnectAuthentication): QuickConnectAuthentication
}
