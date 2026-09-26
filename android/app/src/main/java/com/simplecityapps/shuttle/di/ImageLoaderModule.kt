package com.simplecityapps.shuttle.di

import com.simplecityapps.mediaprovider.AggregateRemoteArtworkProvider
import com.simplecityapps.mediaprovider.RemoteArtworkProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class ImageLoaderModule {
    // Each provider module contributes its own artwork provider to the set.
    @Singleton
    @Provides
    fun provideAggregateRemoteArtworkProvider(
        providers: Set<@JvmSuppressWildcards RemoteArtworkProvider>
    ): AggregateRemoteArtworkProvider = AggregateRemoteArtworkProvider(providers)
}
