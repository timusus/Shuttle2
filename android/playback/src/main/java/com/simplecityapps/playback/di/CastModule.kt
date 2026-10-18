package com.simplecityapps.playback.di

import android.content.Context
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.RemoteCastPlayer
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.simplecityapps.imageloading.ArtworkImageLoader
import com.simplecityapps.mediaprovider.AggregateMediaInfoProvider
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.AppPlayer
import com.simplecityapps.playback.AudioEffectSessionManager
import com.simplecityapps.playback.chromecast.CastMediaItemConverter
import com.simplecityapps.playback.chromecast.CastQueue
import com.simplecityapps.playback.chromecast.CastService
import com.simplecityapps.playback.chromecast.CastSessionManager
import com.simplecityapps.playback.chromecast.CastStreams
import com.simplecityapps.playback.chromecast.HttpServer
import com.simplecityapps.playback.exoplayer.ExoPlayerFactory
import com.simplecityapps.shuttle.di.ApplicationContext
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@ContributesTo(AppScope::class)
@BindingContainer
class CastModule {
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

    @SingleIn(AppScope::class)
    @Provides
    fun provideCastMediaItemConverter(
        @ApplicationContext context: Context,
        streams: CastStreams
    ): CastMediaItemConverter = CastMediaItemConverter(CastMediaItemConverter.wifiAddress(context), streams, context.getString(com.simplecityapps.core.R.string.unknown))

    @SingleIn(AppScope::class)
    @Provides
    fun provideCastQueue(
        @ApplicationContext context: Context,
        exoPlayer: ExoPlayer,
        converter: CastMediaItemConverter,
        streams: CastStreams
    ): CastQueue = CastQueue(
        exoPlayer,
        converter,
        streams,
        receiverWasRunning = { CastSessionManager.receiverWasRunning(context) },
        receiverPlayedOut = { CastSessionManager.receiverPlayedOut(context) }
    )

    // The player the app plays through: the ExoPlayer, then, once Cast is attached (see CastStarter), a Cast player
    // around it that plays on a Cast receiver while a Cast session is up. The Cast player is built on the main thread,
    // as Cast requires.
    @SingleIn(AppScope::class)
    @Provides
    fun provideAppPlayer(
        @ApplicationContext context: Context,
        exoPlayer: ExoPlayer,
        exoPlayerFactory: ExoPlayerFactory,
        converter: Lazy<CastMediaItemConverter>,
        castQueue: CastQueue,
        castSessionManager: Lazy<CastSessionManager>,
        audioEffectSessionManager: AudioEffectSessionManager
    ): AppPlayer = AppPlayer(exoPlayer) {
        if (castSessionManager.value.start()) {
            CastPlayer.Builder(context)
                .setLocalPlayer(exoPlayer)
                .setRemotePlayer(RemoteCastPlayer.Builder(context).setMediaItemConverter(converter.value).build())
                .setTransferCallback(castQueue)
                .build()
                .also(castQueue::attach)
        } else {
            null
        }
    }.also { player ->
        audioEffectSessionManager.attach(player, exoPlayer)
        exoPlayerFactory.crossfade.followCast(player)
    }

    @Provides
    fun providePlayer(appPlayer: AppPlayer): Player = appPlayer
}
