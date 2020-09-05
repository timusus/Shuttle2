package com.simplecityapps.shuttle.dagger

import com.simplecityapps.localmediaprovider.local.provider.taglib.FileScanner
import dagger.Module
import dagger.Provides

@Module
class TagLibModule {

    @Provides
    fun provideFileScanner(): FileScanner {
        return FileScanner()
    }
}