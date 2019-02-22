package au.com.simplecityapps.shuttle.imageloading.dagger

import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import au.com.simplecityapps.shuttle.imageloading.glide.GlideImageLoader
import au.com.simplecityapps.shuttle.imageloading.networking.lastfm.LastFmService
import com.simplecityapps.shuttle.dagger.AppScope
import dagger.Module
import dagger.Provides
import okhttp3.OkHttpClient

@Module
class ImageLoaderModule {

    @AppScope
    @Provides
    fun provideLastFm(okHttpClient: OkHttpClient): LastFmService.LastFm {
        return LastFmService(okHttpClient).lastFm
    }

    @AppScope
    @Provides
    fun provideImageLoader(lastFm: LastFmService.LastFm): ArtworkImageLoader {
        return GlideImageLoader(lastFm)
    }
}