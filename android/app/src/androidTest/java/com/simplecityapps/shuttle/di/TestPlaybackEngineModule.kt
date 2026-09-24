package com.simplecityapps.shuttle.di

import android.content.Context
import android.media.AudioManager
import com.simplecityapps.mediaprovider.AggregateMediaInfoProvider
import com.simplecityapps.playback.AudioEffectSessionManager
import com.simplecityapps.playback.Playback
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.ProgressTicker
import com.simplecityapps.playback.audiofocus.AudioFocusHelper
import com.simplecityapps.playback.di.PlaybackEngineModule
import com.simplecityapps.playback.dsp.replaygain.ReplayGainAudioProcessor
import com.simplecityapps.playback.dsp.replaygain.ReplayGainMode
import com.simplecityapps.playback.exoplayer.AudioTrackMonitor
import com.simplecityapps.playback.exoplayer.EqualizerAudioProcessor
import com.simplecityapps.playback.exoplayer.ExoPlayerFactory
import com.simplecityapps.playback.exoplayer.ExoPlayerPlayback
import com.simplecityapps.playback.exoplayer.MediaInfoMediaResolver
import com.simplecityapps.playback.exoplayer.PlayerFactory
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.fake.FakeAudioFocusHelper
import com.simplecityapps.shuttle.fake.FakePlayback
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [PlaybackEngineModule::class]
)
class TestPlaybackEngineModule {

    @Singleton
    @Provides
    fun providePlayback(): Playback = FakePlayback()

    @Singleton
    @Provides
    fun provideAudioFocusHelper(): AudioFocusHelper = FakeAudioFocusHelper()

    @Singleton
    @Provides
    fun provideEqualizerAudioProcessor(): EqualizerAudioProcessor = EqualizerAudioProcessor(false)

    @Singleton
    @Provides
    fun provideReplayGainAudioProcessor(): ReplayGainAudioProcessor = ReplayGainAudioProcessor(ReplayGainMode.Off, 0.0)

    @Singleton
    @Provides
    fun provideAggregateMediaInfoProvider(): AggregateMediaInfoProvider = AggregateMediaInfoProvider(mutableSetOf())

    @Singleton
    @Provides
    fun provideAudioTrackMonitor(): AudioTrackMonitor = AudioTrackMonitor()

    @Provides
    fun providePlayerFactory(
        @ApplicationContext context: Context,
        equalizerAudioProcessor: EqualizerAudioProcessor,
        replayGainAudioProcessor: ReplayGainAudioProcessor,
        audioTrackMonitor: AudioTrackMonitor
    ): PlayerFactory = ExoPlayerFactory(context, equalizerAudioProcessor, replayGainAudioProcessor, audioTrackMonitor)

    // One instance: PlaybackManager starts on it and CastSessionManager switches back to it when a
    // Cast session ends, so its settings stay with a single owner.
    @Singleton
    @Provides
    fun provideExoPlayerPlayback(
        playerFactory: PlayerFactory,
        mediaInfoProvider: AggregateMediaInfoProvider
    ): ExoPlayerPlayback = ExoPlayerPlayback(playerFactory, MediaInfoMediaResolver(mediaInfoProvider))

    @Singleton
    @Provides
    fun providePlaybackManager(
        queueManager: QueueManager,
        playback: Playback,
        audioFocusHelper: AudioFocusHelper,
        playbackPreferenceManager: PlaybackPreferenceManager,
        audioEffectSessionManager: AudioEffectSessionManager,
        @AppCoroutineScope coroutineScope: CoroutineScope,
        audioManager: AudioManager?
    ): PlaybackManager = PlaybackManager(
        queueManager,
        audioFocusHelper,
        playbackPreferenceManager,
        audioEffectSessionManager,
        coroutineScope,
        ProgressTicker(coroutineScope),
        playback,
        audioManager
    )

    @Provides
    fun providePlaybackOperations(playbackManager: PlaybackManager): PlaybackOperations = playbackManager
}
