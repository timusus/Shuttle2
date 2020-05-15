package com.simplecityapps.shuttle.dagger

import com.simplecityapps.ktaglib.KTagLib
import com.simplecityapps.localmediaprovider.local.provider.taglib.FileScanner
import dagger.Module
import dagger.Provides

@Module
class TagLibModule {

    @Provides
    fun provideTagLib(): KTagLib {
        return KTagLib()
    }

    @Provides
    fun provideFileScanner(tagLib: KTagLib): FileScanner {
        return FileScanner(tagLib)
    }
}