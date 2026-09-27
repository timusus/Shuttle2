package com.simplecityapps.shuttle.ui.screens.sources.servers

import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.ui.screens.sources.servers.emby.EmbyServerAuthentication
import com.simplecityapps.shuttle.ui.screens.sources.servers.jellyfin.JellyfinServerAuthentication
import com.simplecityapps.shuttle.ui.screens.sources.servers.plex.PlexServerAuthentication
import com.simplecityapps.trial.MonetisationAnalytics
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap

@InstallIn(SingletonComponent::class)
@Module
abstract class ServerAuthenticationModule {
    @Binds
    @IntoMap
    @ServerTypeKey(MediaProviderType.Plex)
    abstract fun bindPlex(authentication: PlexServerAuthentication): ServerAuthentication

    @Binds
    @IntoMap
    @ServerTypeKey(MediaProviderType.Jellyfin)
    abstract fun bindJellyfin(authentication: JellyfinServerAuthentication): ServerAuthentication

    @Binds
    @IntoMap
    @ServerTypeKey(MediaProviderType.Emby)
    abstract fun bindEmby(authentication: EmbyServerAuthentication): ServerAuthentication

    companion object {
        @Provides
        fun provideServerSignInAnalytics(analytics: MonetisationAnalytics): ServerSignInAnalytics = ServerSignInAnalytics(analytics::serverConnected)
    }
}
