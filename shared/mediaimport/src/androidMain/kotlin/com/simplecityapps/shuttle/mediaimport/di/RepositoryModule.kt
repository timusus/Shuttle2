package com.simplecityapps.shuttle.mediaimport.di;

import android.content.Context
import com.simplecityapps.shuttle.mediaimport.MediaProviderFactory
import com.simplecityapps.shuttle.mediaimport.mediaprovider.AndroidMediaProviderFactory
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
    fun provideMediaProviderFactory(@ApplicationContext context: Context): MediaProviderFactory {
        return AndroidMediaProviderFactory(context)
    }
}