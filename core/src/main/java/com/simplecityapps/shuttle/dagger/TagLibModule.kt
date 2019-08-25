package com.simplecityapps.shuttle.dagger

import com.simplecityapps.taglib.ArtworkProvider
import com.simplecityapps.taglib.FileScanner
import dagger.Module
import dagger.Provides

@Module
class TagLibModule {

    @Provides
    fun provideFileScanner(): FileScanner {
        return FileScanner()
    }

    @Provides
    fun provideArtwork(): ArtworkProvider {
        return ArtworkProvider()
    }

}