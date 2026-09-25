package com.simplecityapps.shuttle.di

import android.content.Context
import androidx.core.content.getSystemService
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.simplecityapps.mediaprovider.AggregateMediaInfoProvider
import com.simplecityapps.playback.CallMonitor
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.PlaybackOperations
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
import com.simplecityapps.playback.settings.PlaybackSettings
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

    // No Cast player in tests: the app plays through the ExoPlayer itself.
    @Singleton
    @Provides
    fun providePlayer(exoPlayer: ExoPlayer): Player = exoPlayer

    @Singleton
    @Provides
    fun providePlaybackManager(
        @ApplicationContext context: Context,
        queueManager: QueueManager,
        player: Player,
        localPlayer: ExoPlayer,
        playbackPreferenceManager: PlaybackPreferenceManager,
        playbackSettings: PlaybackSettings,
        @AppCoroutineScope coroutineScope: CoroutineScope
    ): PlaybackManager = PlaybackManager(
        queueManager,
        player,
        localPlayer,
        playbackPreferenceManager,
        playbackSettings.playbackSpeed,
        CallMonitor(context.getSystemService()),
        coroutineScope,
        castQueue = null
    )

    @Provides
    fun providePlaybackOperations(playbackManager: PlaybackManager): PlaybackOperations = playbackManager
}
