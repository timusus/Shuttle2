package com.simplecityapps.shuttle.dagger

import au.com.simplecityapps.shuttle.imageloading.ArtworkDownloadService
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
abstract class ArtworkServiceModule {

    @ContributesAndroidInjector
    abstract fun bindArtworkService(): ArtworkDownloadService

}