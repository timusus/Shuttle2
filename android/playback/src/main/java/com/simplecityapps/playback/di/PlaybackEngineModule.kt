package com.simplecityapps.playback.di

import android.content.Context
import android.os.Build
import com.simplecityapps.mediaprovider.AggregateMediaInfoProvider
import com.simplecityapps.playback.Playback
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.PlaybackWatcher
import com.simplecityapps.playback.AudioEffectSessionManager
import com.simplecityapps.playback.audiofocus.AudioFocusHelper
import com.simplecityapps.playback.audiofocus.AudioFocusHelperApi21
import com.simplecityapps.playback.audiofocus.AudioFocusHelperApi26
import com.simplecityapps.playback.dsp.equalizer.Equalizer
import com.simplecityapps.playback.dsp.replaygain.ReplayGainAudioProcessor
import com.simplecityapps.playback.exoplayer.EqualizerAudioProcessor
import com.simplecityapps.playback.exoplayer.ExoPlayerPlayback
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.queue.QueueWatcher
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
import android.media.AudioManager

@InstallIn(SingletonComponent::class)
@Module
class PlaybackEngineModule {
    @Singleton
    @Provides
    fun provideEqualizer(playbackPreferenceManager: PlaybackPreferenceManager): EqualizerAudioProcessor = EqualizerAudioProcessor(playbackPreferenceManager.equalizerEnabled).apply {
        // Restore current eq
        preset = playbackPreferenceManager.preset

        // Restore custom eq bands
        playbackPreferenceManager.customPresetBands?.forEach { restoredBand ->
            Equalizer.Presets.custom.bands.forEach { customBand ->
                if (customBand.centerFrequency == restoredBand.centerFrequency) {
                    customBand.gain = restoredBand.gain
                }
            }
        }
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

    @Provides
    fun provideExoPlayerPlayback(
        @ApplicationContext context: Context,
        equalizerAudioProcessor: EqualizerAudioProcessor,
        replayGainAudioProcessor: ReplayGainAudioProcessor,
        mediaPathProvider: AggregateMediaInfoProvider
    ): ExoPlayerPlayback = ExoPlayerPlayback(context, equalizerAudioProcessor, replayGainAudioProcessor, mediaPathProvider)

    @Provides
    fun providePlayback(exoPlayerPlayback: ExoPlayerPlayback): Playback = exoPlayerPlayback

    @Singleton
    @Provides
    fun provideAudioFocusHelper(
        @ApplicationContext context: Context,
        playbackWatcher: PlaybackWatcher
    ): AudioFocusHelper {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return AudioFocusHelperApi26(context, playbackWatcher)
        } else {
            return AudioFocusHelperApi21(context, playbackWatcher)
        }
    }

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
    ): PlaybackManager = PlaybackManager(queueManager, playbackWatcher, audioFocusHelper, playbackPreferenceManager, audioEffectSessionManager, coroutineScope, playback, queueWatcher, audioManager)
}
