package com.simplecityapps.shuttle.dagger

import android.content.Context
import com.simplecityapps.localmediaprovider.local.provider.mediastore.MediaStoreMediaProvider
import com.simplecityapps.localmediaprovider.local.provider.taglib.FileScanner
import com.simplecityapps.localmediaprovider.local.provider.taglib.TaglibMediaProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class MediaProviderModule {

    @Provides
    @Singleton
    fun provideFileScanner(): FileScanner {
        return FileScanner()
    }

    @Provides
    @Singleton
    fun provideTagLibSongProvider(@ApplicationContext context: Context, fileScanner: FileScanner): TaglibMediaProvider {
        return TaglibMediaProvider(context, fileScanner)
    }

    @Provides
    @Singleton
    fun provideMediaStoreSongProvider(@ApplicationContext context: Context): MediaStoreMediaProvider {
        return MediaStoreMediaProvider(context)
    }
}