package com.simplecityapps.shuttle.ui.shell.player

import android.content.Context
import android.graphics.Bitmap
import coil3.ImageLoader
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.simplecityapps.playback.chromecast.CastSessionManager
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.shuttle.designsystem.theme.ArtworkSeed
import com.simplecityapps.shuttle.designsystem.theme.SeedColorCache
import com.simplecityapps.shuttle.model.Song
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import timber.log.Timber

/** Extracts seeds from a small Coil bitmap of the song's artwork, cached per album. */
class CoilArtworkSeedSource(
    private val context: Context,
    private val imageLoader: ImageLoader,
    private val cache: SeedColorCache = SeedColorCache(),
) : ArtworkSeedSource {
    override suspend fun seedFor(song: Song): ArtworkSeed = cache.getOrExtract(song.albumGroupKey.toString()) { loadBitmap(song) }

    private suspend fun loadBitmap(song: Song): Bitmap? {
        val request = ImageRequest.Builder(context)
            .data(song)
            .size(SEED_BITMAP_SIZE)
            // Extraction reads the pixels, which a hardware bitmap doesn't allow
            .allowHardware(false)
            .build()
        return when (val result = imageLoader.execute(request)) {
            is SuccessResult -> result.image.toBitmap()

            is ErrorResult -> {
                Timber.v(result.throwable, "No artwork seed for ${song.name}")
                null
            }
        }
    }

    private companion object {
        const val SEED_BITMAP_SIZE = 112
    }
}

@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {
    @Provides
    @Singleton
    fun provideArtworkSeedSource(
        @ApplicationContext context: Context,
        imageLoader: ImageLoader,
    ): ArtworkSeedSource = CoilArtworkSeedSource(context, imageLoader)

    @Provides
    fun provideCastAvailability(castSessionManager: CastSessionManager): CastAvailability = CastAvailability { castSessionManager.isAvailable }

    @Provides
    fun provideSavedNowPlaying(playbackPreferenceManager: PlaybackPreferenceManager): SavedNowPlaying = SavedNowPlaying { playbackPreferenceManager.nowPlaying }
}
