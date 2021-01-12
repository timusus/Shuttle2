package com.simplecityapps.shuttle.dagger

import android.content.Context
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import au.com.simplecityapps.shuttle.imageloading.coil.CoilImageLoader
import com.simplecityapps.mediaprovider.repository.SongRepository
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import dagger.Module
import dagger.Provides
import okhttp3.OkHttpClient

@Module
class ImageLoaderModule {

    @AppScope
    @Provides
    fun provideImageLoader(
        context: Context,
        songRepository: SongRepository,
        okHttpClient: OkHttpClient,
        preferenceManager: GeneralPreferenceManager
    ): ArtworkImageLoader {
        return CoilImageLoader(context, songRepository, okHttpClient, preferenceManager)
    }
}