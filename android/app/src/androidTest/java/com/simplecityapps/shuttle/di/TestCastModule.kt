package com.simplecityapps.shuttle.di

import android.content.Context
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.simplecityapps.mediaprovider.AggregateMediaInfoProvider
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.chromecast.CastService
import com.simplecityapps.playback.chromecast.CastSessionManager
import com.simplecityapps.playback.chromecast.HttpServer
import com.simplecityapps.playback.di.CastModule
import com.simplecityapps.playback.exoplayer.ExoPlayerPlayback
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [CastModule::class]
)
class TestCastModule {

    @Singleton
    @Provides
    fun provideCastService(
        @ApplicationContext context: Context,
        songRepository: SongRepository,
        artworkImageLoader: ArtworkImageLoader
    ): CastService = CastService(context, songRepository, artworkImageLoader)

    @Singleton
    @Provides
    fun provideHttpServer(castService: CastService): HttpServer = HttpServer(castService)

    @Singleton
    @Provides
    fun provideCastSessionManager(
        @ApplicationContext context: Context,
        playbackManager: PlaybackOperations,
        httpServer: HttpServer,
        exoPlayerPlayback: ExoPlayerPlayback,
        mediaInfoProvider: AggregateMediaInfoProvider
    ): CastSessionManager = CastSessionManager(playbackManager, context, httpServer, exoPlayerPlayback, mediaInfoProvider)
}
