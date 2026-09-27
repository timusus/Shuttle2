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
import com.simplecityapps.shuttle.di.ApplicationContext
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@BindingContainer
@ContributesTo(AppScope::class, replaces = [CastModule::class])
class TestCastModule {

    @SingleIn(AppScope::class)
    @Provides
    fun provideCastService(
        @ApplicationContext context: Context,
        songRepository: SongRepository,
        artworkImageLoader: ArtworkImageLoader,
        streams: CastStreams
    ): CastService = CastService(context, songRepository, artworkImageLoader, streams)

    @SingleIn(AppScope::class)
    @Provides
    fun provideCastStreams(mediaInfoProvider: AggregateMediaInfoProvider): CastStreams = CastStreams(mediaInfoProvider)

    @SingleIn(AppScope::class)
    @Provides
    fun provideHttpServer(
        castService: CastService,
        streams: CastStreams
    ): HttpServer = HttpServer(castService, streams)

    @SingleIn(AppScope::class)
    @Provides
    fun provideCastSessionManager(
        @ApplicationContext context: Context,
        httpServer: HttpServer,
        streams: CastStreams
    ): CastSessionManager = CastSessionManager(context, httpServer, streams)
}
