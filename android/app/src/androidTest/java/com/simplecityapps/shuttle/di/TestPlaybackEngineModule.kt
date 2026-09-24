package com.simplecityapps.shuttle.di

import android.content.Context
import android.media.AudioManager
import androidx.media3.exoplayer.ExoPlayer
import com.simplecityapps.mediaprovider.AggregateMediaInfoProvider
import com.simplecityapps.playback.AudioEffectSessionManager
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.audiofocus.AudioFocusHelper
import com.simplecityapps.playback.di.PlaybackEngineModule
import com.simplecityapps.playback.dsp.replaygain.ReplayGainAudioProcessor
import com.simplecityapps.playback.dsp.replaygain.ReplayGainMode
import com.simplecityapps.playback.engine.SongUriResolver
import com.simplecityapps.playback.exoplayer.AudioTrackMonitor
import com.simplecityapps.playback.exoplayer.EqualizerAudioProcessor
import com.simplecityapps.playback.exoplayer.ExoPlayerFactory
import com.simplecityapps.playback.exoplayer.MediaInfoMediaResolver
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.fake.FakeAudioFocusHelper
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

    @Singleton
    @Provides
    fun provideSongUriResolver(mediaInfoProvider: AggregateMediaInfoProvider): SongUriResolver = SongUriResolver(MediaInfoMediaResolver(mediaInfoProvider))

    @Singleton
    @Provides
    fun provideExoPlayer(
        @ApplicationContext context: Context,
        equalizerAudioProcessor: EqualizerAudioProcessor,
        replayGainAudioProcessor: ReplayGainAudioProcessor,
        audioTrackMonitor: AudioTrackMonitor,
        songUriResolver: SongUriResolver
    ): ExoPlayer = ExoPlayerFactory(context, equalizerAudioProcessor, replayGainAudioProcessor, audioTrackMonitor, songUriResolver).create()

    @Singleton
    @Provides
    fun providePlaybackManager(
        queueManager: QueueManager,
        player: ExoPlayer,
        audioFocusHelper: AudioFocusHelper,
        playbackPreferenceManager: PlaybackPreferenceManager,
        audioEffectSessionManager: AudioEffectSessionManager,
        @AppCoroutineScope coroutineScope: CoroutineScope,
        audioManager: AudioManager?
    ): PlaybackManager = PlaybackManager(
        queueManager,
        player,
        audioFocusHelper,
        playbackPreferenceManager,
        audioEffectSessionManager,
        coroutineScope,
        audioManager
    )

    @Provides
    fun providePlaybackOperations(playbackManager: PlaybackManager): PlaybackOperations = playbackManager
}
