package com.simplecityapps.shuttle.dagger

import android.content.Context
import com.simplecityapps.localmediaprovider.local.provider.mediastore.MediaStoreMediaProvider
import com.simplecityapps.localmediaprovider.local.provider.taglib.FileScanner
import com.simplecityapps.localmediaprovider.local.provider.taglib.TaglibMediaProvider
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
    fun provideTagLibSongProvider(context: Context, fileScanner: FileScanner): TaglibMediaProvider {
        return TaglibMediaProvider(context, fileScanner)
    }

    @Provides
    @AppScope
    fun provideMediaStoreSongProvider(context: Context): MediaStoreMediaProvider {
        return MediaStoreMediaProvider(context)
    }
}