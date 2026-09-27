package com.simplecityapps.shuttle.ui.screens.sources.servers

import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.ui.screens.sources.servers.emby.EmbyServerAuthentication
import com.simplecityapps.shuttle.ui.screens.sources.servers.jellyfin.JellyfinServerAuthentication
import com.simplecityapps.shuttle.ui.screens.sources.servers.plex.PlexServerAuthentication
import com.simplecityapps.trial.MonetisationAnalytics
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides

@ContributesTo(AppScope::class)
@BindingContainer
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
