package com.simplecityapps.shuttle.di

import android.content.Context
import com.simplecityapps.imageloading.ArtworkImageLoader
import com.simplecityapps.mediaprovider.AggregateMediaInfoProvider
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.chromecast.CastService
import com.simplecityapps.playback.chromecast.CastSessionManager
import com.simplecityapps.playback.chromecast.CastStreams
import com.simplecityapps.playback.chromecast.HttpServer
import com.simplecityapps.playback.di.CastModule
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
        artworkImageLoader: ArtworkImageLoader,
        streams: CastStreams
    ): CastService = CastService(context, songRepository, artworkImageLoader, streams)

    @Singleton
    @Provides
    fun provideCastStreams(mediaInfoProvider: AggregateMediaInfoProvider): CastStreams = CastStreams(mediaInfoProvider)

    @Singleton
    @Provides
    fun provideHttpServer(
        castService: CastService,
        streams: CastStreams
    ): HttpServer = HttpServer(castService, streams)

    @Singleton
    @Provides
    fun provideCastSessionManager(
        @ApplicationContext context: Context,
        httpServer: HttpServer,
        streams: CastStreams
    ): CastSessionManager = CastSessionManager(context, httpServer, streams)
}
