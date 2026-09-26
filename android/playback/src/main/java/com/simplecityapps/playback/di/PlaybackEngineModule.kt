package com.simplecityapps.playback.di

import android.content.Context
import androidx.core.content.getSystemService
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.RemoteCastPlayer
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.tracing.trace
import com.simplecityapps.mediaprovider.AggregateMediaInfoProvider
import com.simplecityapps.mediaprovider.ServerStreamPolicy
import com.simplecityapps.playback.AppPlayer
import com.simplecityapps.playback.AudioEffectSessionManager
import com.simplecityapps.playback.CallMonitor
import com.simplecityapps.playback.PlaybackFacade
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.chromecast.CastMediaItemConverter
import com.simplecityapps.playback.chromecast.CastQueue
import com.simplecityapps.playback.chromecast.CastSessionManager
import com.simplecityapps.playback.chromecast.CastStreams
import com.simplecityapps.playback.dsp.equalizer.Equalizer
import com.simplecityapps.playback.dsp.replaygain.ReplayGainAudioProcessor
import com.simplecityapps.playback.engine.SongUriResolver
import com.simplecityapps.playback.exoplayer.AudioTrackMonitor
import com.simplecityapps.playback.exoplayer.EqualizerAudioProcessor
import com.simplecityapps.playback.exoplayer.ExoPlayerFactory
import com.simplecityapps.playback.exoplayer.MediaInfoMediaResolver
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.settings.PlaybackSettings
import com.simplecityapps.provider.emby.EmbyMediaInfoProvider
import com.simplecityapps.provider.jellyfin.JellyfinMediaInfoProvider
import com.simplecityapps.provider.plex.PlexMediaInfoProvider
import com.simplecityapps.shuttle.di.AppCoroutineScope
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

@InstallIn(SingletonComponent::class)
@Module
class PlaybackEngineModule {
    @Singleton
    @Provides
    fun provideEqualizer(
        playbackPreferenceManager: PlaybackPreferenceManager,
        playbackSettings: PlaybackSettings
    ): EqualizerAudioProcessor = EqualizerAudioProcessor(playbackSettings.equalizerEnabled.value).apply {
        // Restore custom eq bands first: setting the preset captures its band gains
        playbackPreferenceManager.customPresetBands?.forEach { restoredBand ->
            Equalizer.Presets.custom.bands.forEach { customBand ->
                if (customBand.centerFrequency == restoredBand.centerFrequency) {
                    customBand.gain = restoredBand.gain
                }
            }
        }

        // Restore current eq
        preset = playbackPreferenceManager.preset
    }

    @Singleton
    @Provides
    fun provideReplayGainAudioProcessor(playbackSettings: PlaybackSettings): ReplayGainAudioProcessor = ReplayGainAudioProcessor(playbackSettings.replayGainMode.value, playbackSettings.preAmpGain.value.toDouble())

    @Singleton
    @Provides
    fun provideAggregateMediaPathProvider(
        embyMediaPathProvider: EmbyMediaInfoProvider,
        jellyfinMediaPathProvider: JellyfinMediaInfoProvider,
        plexMediaPathProvider: PlexMediaInfoProvider,
        serverStreamPolicy: ServerStreamPolicy
    ): AggregateMediaInfoProvider = AggregateMediaInfoProvider(
        mutableSetOf(
            embyMediaPathProvider,
            jellyfinMediaPathProvider,
            plexMediaPathProvider
        ),
        serverStreamPolicy
    )

    @Singleton
    @Provides
    fun provideAudioTrackMonitor(): AudioTrackMonitor = AudioTrackMonitor()

    @Singleton
    @Provides
    fun provideSongUriResolver(mediaInfoProvider: AggregateMediaInfoProvider): SongUriResolver = SongUriResolver(MediaInfoMediaResolver(mediaInfoProvider))

    // The local player: it owns the queue, and plays it when not casting. It lives on the main looper.
    @Singleton
    @Provides
    fun provideExoPlayer(
        @ApplicationContext context: Context,
        equalizerAudioProcessor: EqualizerAudioProcessor,
        replayGainAudioProcessor: ReplayGainAudioProcessor,
        audioTrackMonitor: AudioTrackMonitor,
        songUriResolver: SongUriResolver
    ): ExoPlayer = trace("S2 build ExoPlayer") {
        ExoPlayerFactory(context, equalizerAudioProcessor, replayGainAudioProcessor, audioTrackMonitor, songUriResolver).create()
    }

    @Singleton
    @Provides
    fun provideCastMediaItemConverter(
        @ApplicationContext context: Context,
        streams: CastStreams
    ): CastMediaItemConverter = CastMediaItemConverter(CastMediaItemConverter.wifiAddress(context), streams, context.getString(com.simplecityapps.core.R.string.unknown))

    @Singleton
    @Provides
    fun provideCastQueue(
        @ApplicationContext context: Context,
        exoPlayer: ExoPlayer,
        converter: CastMediaItemConverter,
        streams: CastStreams
    ): CastQueue = CastQueue(exoPlayer, converter, streams) { CastSessionManager.receiverPlayedOut(context) }

    // The player the app plays through: the ExoPlayer, then, once Cast is attached (see CastStarter), a Cast player
    // around it that plays on a Cast receiver while a Cast session is up. The Cast player is built on the main thread,
    // as Cast requires.
    @Singleton
    @Provides
    fun provideAppPlayer(
        @ApplicationContext context: Context,
        exoPlayer: ExoPlayer,
        converter: Lazy<CastMediaItemConverter>,
        castQueue: CastQueue,
        castSessionManager: Lazy<CastSessionManager>,
        audioEffectSessionManager: AudioEffectSessionManager
    ): AppPlayer = AppPlayer(exoPlayer) {
        if (castSessionManager.get().start()) {
            CastPlayer.Builder(context)
                .setLocalPlayer(exoPlayer)
                .setRemotePlayer(RemoteCastPlayer.Builder(context).setMediaItemConverter(converter.get()).build())
                .setTransferCallback(castQueue)
                .build()
                .also(castQueue::attach)
        } else {
            null
        }
    }.also { player -> audioEffectSessionManager.attach(player, exoPlayer) }

    @Provides
    fun providePlayer(appPlayer: AppPlayer): Player = appPlayer

    @Singleton
    @Provides
    fun providePlaybackOperations(
        @ApplicationContext context: Context,
        queueManager: QueueManager,
        player: Player,
        localPlayer: ExoPlayer,
        playbackPreferenceManager: PlaybackPreferenceManager,
        playbackSettings: PlaybackSettings,
        @AppCoroutineScope coroutineScope: CoroutineScope,
        castQueue: CastQueue
    ): PlaybackOperations = PlaybackFacade(
        queueManager,
        player,
        localPlayer,
        playbackPreferenceManager,
        playbackSettings.playbackSpeed,
        CallMonitor(context.getSystemService()),
        coroutineScope,
        castQueue
    )
}
