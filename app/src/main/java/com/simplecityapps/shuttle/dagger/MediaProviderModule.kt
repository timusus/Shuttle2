package com.simplecityapps.shuttle.dagger

import android.content.Context
import com.simplecityapps.localmediaprovider.local.provider.mediastore.MediaStoreSongProvider
import com.simplecityapps.localmediaprovider.local.provider.taglib.FileScanner
import com.simplecityapps.localmediaprovider.local.provider.taglib.TaglibSongProvider
import dagger.Module
import dagger.Provides

@Module
class MediaProviderModule {

    @Provides
    @AppScope
    fun provideFileScanner(): FileScanner {
        return FileScanner()
    }

    @Provides
    @AppScope
    fun provideTagLibSongProvider(context: Context, fileScanner: FileScanner): TaglibSongProvider {
        return TaglibSongProvider(context, fileScanner)
    }

    @Provides
    @AppScope
    fun provideMediaStoreSongProvider(context: Context): MediaStoreSongProvider {
        return MediaStoreSongProvider(context)
    }
}