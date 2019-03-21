package au.com.simplecityapps.shuttle.imageloading.dagger

import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import au.com.simplecityapps.shuttle.imageloading.glide.GlideImageLoader
import com.simplecityapps.shuttle.dagger.AppScope
import dagger.Module
import dagger.Provides

@Module
class ImageLoaderModule {

    @AppScope
    @Provides
    fun provideImageLoader(): ArtworkImageLoader {
        return GlideImageLoader()
    }
}