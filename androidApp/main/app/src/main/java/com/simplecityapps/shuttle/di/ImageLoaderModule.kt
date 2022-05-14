package com.simplecityapps.shuttle.di

import android.content.Context
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import au.com.simplecityapps.shuttle.imageloading.glide.GlideImageLoader
import com.simplecityapps.mediaprovider.AggregateRemoteArtworkProvider
import com.simplecityapps.provider.emby.EmbyRemoteArtworkProvider
import com.simplecityapps.provider.jellyfin.JellyfinRemoteArtworkProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class ImageLoaderModule {

    @Singleton
    @Provides
    fun provideImageLoader(
        @ApplicationContext context: Context
    ): ArtworkImageLoader {
        return GlideImageLoader(context)
    }

    @Singleton
    @Provides
    fun provideAggregateRemoteArtworkProvider(
        embyRemoteArtworkProvider: EmbyRemoteArtworkProvider,
        jellyfinRemoteArtworkProvider: JellyfinRemoteArtworkProvider
    ): AggregateRemoteArtworkProvider {
        return AggregateRemoteArtworkProvider(
            mutableSetOf(
                embyRemoteArtworkProvider,
                jellyfinRemoteArtworkProvider
            )
        )
    }
}
