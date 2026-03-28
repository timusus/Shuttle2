package com.simplecityapps.shuttle.di

import android.content.Context
import android.media.AudioManager
import com.simplecityapps.mediaprovider.AggregateMediaInfoProvider
import com.simplecityapps.playback.AudioEffectSessionManager
import com.simplecityapps.playback.Playback
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.PlaybackWatcher
import com.simplecityapps.playback.audiofocus.AudioFocusHelper
import com.simplecityapps.playback.di.PlaybackEngineModule
import com.simplecityapps.playback.dsp.replaygain.ReplayGainAudioProcessor
import com.simplecityapps.playback.dsp.replaygain.ReplayGainMode
import com.simplecityapps.playback.exoplayer.EqualizerAudioProcessor
import com.simplecityapps.playback.exoplayer.ExoPlayerPlayback
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.queue.QueueWatcher
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

    @Provides
    fun provideExoPlayerPlayback(
        @ApplicationContext context: Context,
        equalizerAudioProcessor: EqualizerAudioProcessor,
        replayGainAudioProcessor: ReplayGainAudioProcessor,
        mediaInfoProvider: AggregateMediaInfoProvider
    ): ExoPlayerPlayback = ExoPlayerPlayback(context, equalizerAudioProcessor, replayGainAudioProcessor, mediaInfoProvider)

    @Singleton
    @Provides
    fun providePlaybackManager(
        queueManager: QueueManager,
        playback: Playback,
        playbackWatcher: PlaybackWatcher,
        audioFocusHelper: AudioFocusHelper,
        playbackPreferenceManager: PlaybackPreferenceManager,
        audioEffectSessionManager: AudioEffectSessionManager,
        @AppCoroutineScope coroutineScope: CoroutineScope,
        queueWatcher: QueueWatcher,
        audioManager: AudioManager?
    ): PlaybackManager = PlaybackManager(
        queueManager,
        playbackWatcher,
        audioFocusHelper,
        playbackPreferenceManager,
        audioEffectSessionManager,
        coroutineScope,
        playback,
        queueWatcher,
        audioManager
    )
}
