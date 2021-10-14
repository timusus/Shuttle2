package com.simplecityapps.shuttle.mediaprovider.di

import com.simplecityapps.shuttle.mediaprovider.AndroidMediaProviderFactory
import com.simplecityapps.shuttle.common.mediaprovider.mediastore.MediaStoreMediaProvider
import com.simplecityapps.shuttle.mediaprovider.common.MediaProviderFactory
import com.simplecityapps.shuttle.mediaprovider.jellyfin.JellyfinMediaProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class MediaImportModule {

    @Provides
    @Singleton
    fun provideMediaProviderFactory(
        mediaStoreMediaProvider: MediaStoreMediaProvider,
        jellyfinMediaProvider: JellyfinMediaProvider
    ): MediaProviderFactory {
        return AndroidMediaProviderFactory(
            mediaStoreMediaProvider = mediaStoreMediaProvider,
            jellyfinMediaProvider = jellyfinMediaProvider
        )
    }
}