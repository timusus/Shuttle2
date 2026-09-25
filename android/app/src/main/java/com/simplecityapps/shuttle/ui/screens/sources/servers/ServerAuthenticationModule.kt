package com.simplecityapps.shuttle.ui.screens.sources.servers

import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.ui.screens.sources.servers.plex.PlexServerAuthentication
import com.simplecityapps.trial.EntitlementRepository
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

    companion object {
        @Provides
        fun provideServerTrial(entitlementRepository: EntitlementRepository): ServerTrial = ServerTrial(entitlementRepository::onServerConnected)
    }
}
