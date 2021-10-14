package com.simplecityapps.shuttle.common.mediaprovider.mediastore.di

import android.content.Context
import com.simplecityapps.shuttle.common.mediaprovider.mediastore.MediaStoreMediaProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class MediaImportModule {

    @Provides
    @Singleton
    fun provideMediaStoreMediaProvider(@ApplicationContext context: Context): MediaStoreMediaProvider {
        return MediaStoreMediaProvider(context)
    }
}