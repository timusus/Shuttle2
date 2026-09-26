package com.simplecityapps.shuttle.di

import com.simplecityapps.mediaprovider.AggregateRemoteArtworkProvider
import com.simplecityapps.mediaprovider.RemoteArtworkProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
abstract class ImageLoaderModule {
    // Each provider module contributes its own artwork provider to the set; declared here so it resolves, empty, with none installed.
    @Multibinds
    abstract fun remoteArtworkProviders(): Set<RemoteArtworkProvider>

    companion object {
        @Singleton
        @Provides
        fun provideAggregateRemoteArtworkProvider(
            providers: Set<@JvmSuppressWildcards RemoteArtworkProvider>
        ): AggregateRemoteArtworkProvider = AggregateRemoteArtworkProvider(providers)
    }
}
