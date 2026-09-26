package com.simplecityapps.shuttle.di

import com.simplecityapps.mediaprovider.AggregateRemoteArtworkProvider
import com.simplecityapps.provider.emby.EmbyRemoteArtworkProvider
import com.simplecityapps.provider.jellyfin.JellyfinRemoteArtworkProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class ImageLoaderModule {
    @Singleton
    @Provides
    fun provideAggregateRemoteArtworkProvider(
        embyRemoteArtworkProvider: EmbyRemoteArtworkProvider,
        jellyfinRemoteArtworkProvider: JellyfinRemoteArtworkProvider
    ): AggregateRemoteArtworkProvider = AggregateRemoteArtworkProvider(
        mutableSetOf(
            embyRemoteArtworkProvider,
            jellyfinRemoteArtworkProvider
        )
    )
}
