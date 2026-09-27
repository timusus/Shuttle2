package com.simplecityapps.shuttle.di

import android.content.Context
import androidx.core.content.getSystemService
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.simplecityapps.mediaprovider.AggregateMediaInfoProvider
import com.simplecityapps.playback.AppPlayer
import com.simplecityapps.playback.CallMonitor
import com.simplecityapps.playback.PlaybackFacade
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.di.PlaybackEngineModule
import com.simplecityapps.playback.dsp.equalizer.DefaultEqualizerFrequencyResponse
import com.simplecityapps.playback.dsp.replaygain.ReplayGainAudioProcessor
import com.simplecityapps.playback.dsp.replaygain.ReplayGainMode
import com.simplecityapps.playback.engine.SongUriResolver
import com.simplecityapps.playback.equalizer.EqualizerControl
import com.simplecityapps.playback.equalizer.EqualizerFrequencyResponse
import com.simplecityapps.playback.equalizer.EqualizerPresetStore
import com.simplecityapps.playback.exoplayer.AudioTrackMonitor
import com.simplecityapps.playback.exoplayer.EqualizerAudioProcessor
import com.simplecityapps.playback.exoplayer.ExoPlayerFactory
import com.simplecityapps.playback.exoplayer.MediaInfoMediaResolver
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.persistence.QueueStore
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.playback.settings.PlaybackSettings
import com.simplecityapps.shuttle.di.ApplicationContext
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope

@BindingContainer
@ContributesTo(AppScope::class, replaces = [PlaybackEngineModule::class])
class TestPlaybackEngineModule {

    @SingleIn(AppScope::class)
    @Provides
    fun provideEqualizerAudioProcessor(): EqualizerAudioProcessor = EqualizerAudioProcessor(false)

    @Provides
    fun provideEqualizerControl(equalizer: EqualizerAudioProcessor): EqualizerControl = equalizer

    @Provides
    fun provideEqualizerPresetStore(playbackPreferenceManager: PlaybackPreferenceManager): EqualizerPresetStore = playbackPreferenceManager

    @SingleIn(AppScope::class)
    @Provides
    fun provideReplayGainAudioProcessor(): ReplayGainAudioProcessor = ReplayGainAudioProcessor(ReplayGainMode.Off, 0.0)

    @Provides
    fun provideEqualizerFrequencyResponse(): EqualizerFrequencyResponse = DefaultEqualizerFrequencyResponse()

    @SingleIn(AppScope::class)
    @Provides
    fun provideAggregateMediaInfoProvider(): AggregateMediaInfoProvider = AggregateMediaInfoProvider(mutableSetOf())

    @SingleIn(AppScope::class)
    @Provides
    fun provideAudioTrackMonitor(): AudioTrackMonitor = AudioTrackMonitor()

    @SingleIn(AppScope::class)
    @Provides
    fun provideSongUriResolver(mediaInfoProvider: AggregateMediaInfoProvider): SongUriResolver = SongUriResolver(MediaInfoMediaResolver(mediaInfoProvider))

    @SingleIn(AppScope::class)
    @Provides
    fun provideExoPlayer(
        @ApplicationContext context: Context,
        equalizerAudioProcessor: EqualizerAudioProcessor,
        replayGainAudioProcessor: ReplayGainAudioProcessor,
        audioTrackMonitor: AudioTrackMonitor,
        songUriResolver: SongUriResolver
    ): ExoPlayer = ExoPlayerFactory(context, equalizerAudioProcessor, replayGainAudioProcessor, audioTrackMonitor, songUriResolver).create()

    // No Cast player in tests: the app plays through the ExoPlayer, and attaching Cast does nothing.
    @SingleIn(AppScope::class)
    @Provides
    fun provideAppPlayer(exoPlayer: ExoPlayer): AppPlayer = AppPlayer(exoPlayer, castPlayer = null)

    @Provides
    fun providePlayer(appPlayer: AppPlayer): Player = appPlayer

    @SingleIn(AppScope::class)
    @Provides
    fun providePlaybackOperations(
        @ApplicationContext context: Context,
        queueOperations: QueueOperations,
        player: Player,
        localPlayer: ExoPlayer,
        queueStore: QueueStore,
        playbackSettings: PlaybackSettings,
        @AppCoroutineScope coroutineScope: CoroutineScope
    ): PlaybackOperations = PlaybackFacade(
        queueOperations,
        player,
        localPlayer,
        queueStore,
        playbackSettings.playbackSpeed,
        CallMonitor(context.getSystemService()),
        coroutineScope,
        castQueue = null
    )
}
