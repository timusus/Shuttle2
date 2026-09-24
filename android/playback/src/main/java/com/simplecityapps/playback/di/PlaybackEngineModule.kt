package com.simplecityapps.playback.di

import android.content.Context
import android.media.AudioManager
import android.os.Build
import androidx.media3.exoplayer.ExoPlayer
import com.simplecityapps.mediaprovider.AggregateMediaInfoProvider
import com.simplecityapps.playback.AudioEffectSessionManager
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.audiofocus.AudioFocusHelper
import com.simplecityapps.playback.audiofocus.AudioFocusHelperApi21
import com.simplecityapps.playback.audiofocus.AudioFocusHelperApi26
import com.simplecityapps.playback.dsp.equalizer.Equalizer
import com.simplecityapps.playback.dsp.replaygain.ReplayGainAudioProcessor
import com.simplecityapps.playback.engine.SongUriResolver
import com.simplecityapps.playback.exoplayer.AudioTrackMonitor
import com.simplecityapps.playback.exoplayer.EqualizerAudioProcessor
import com.simplecityapps.playback.exoplayer.ExoPlayerFactory
import com.simplecityapps.playback.exoplayer.MediaInfoMediaResolver
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.provider.emby.EmbyMediaInfoProvider
import com.simplecityapps.provider.jellyfin.JellyfinMediaInfoProvider
import com.simplecityapps.provider.plex.PlexMediaInfoProvider
import com.simplecityapps.shuttle.di.AppCoroutineScope
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
    fun provideEqualizer(playbackPreferenceManager: PlaybackPreferenceManager): EqualizerAudioProcessor = EqualizerAudioProcessor(playbackPreferenceManager.equalizerEnabled).apply {
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
    fun provideReplayGainAudioProcessor(playbackPreferenceManager: PlaybackPreferenceManager): ReplayGainAudioProcessor = ReplayGainAudioProcessor(playbackPreferenceManager.replayGainMode, playbackPreferenceManager.preAmpGain)

    @Singleton
    @Provides
    fun provideAggregateMediaPathProvider(
        embyMediaPathProvider: EmbyMediaInfoProvider,
        jellyfinMediaPathProvider: JellyfinMediaInfoProvider,
        plexMediaPathProvider: PlexMediaInfoProvider
    ): AggregateMediaInfoProvider = AggregateMediaInfoProvider(
        mutableSetOf(
            embyMediaPathProvider,
            jellyfinMediaPathProvider,
            plexMediaPathProvider
        )
    )

    @Singleton
    @Provides
    fun provideAudioTrackMonitor(): AudioTrackMonitor = AudioTrackMonitor()

    @Singleton
    @Provides
    fun provideSongUriResolver(mediaInfoProvider: AggregateMediaInfoProvider): SongUriResolver = SongUriResolver(MediaInfoMediaResolver(mediaInfoProvider))

    // The one player: it owns the queue and plays it. It lives on the main looper.
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
    fun provideAudioFocusHelper(
        @ApplicationContext context: Context
    ): AudioFocusHelper {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return AudioFocusHelperApi26(context)
        } else {
            return AudioFocusHelperApi21(context)
        }
    }

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
    ): PlaybackManager = PlaybackManager(queueManager, player, audioFocusHelper, playbackPreferenceManager, audioEffectSessionManager, coroutineScope, audioManager)

    @Provides
    fun providePlaybackOperations(playbackManager: PlaybackManager): PlaybackOperations = playbackManager
}
