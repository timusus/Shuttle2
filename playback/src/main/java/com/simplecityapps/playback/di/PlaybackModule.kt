package com.simplecityapps.playback.di

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.media.AudioManager
import android.os.Build
import android.util.LruCache
import androidx.core.content.getSystemService
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.simplecityapps.mediaprovider.AggregateMediaInfoProvider
import com.simplecityapps.mediaprovider.repository.*
import com.simplecityapps.playback.*
import com.simplecityapps.playback.androidauto.MediaIdHelper
import com.simplecityapps.playback.audiofocus.AudioFocusHelper
import com.simplecityapps.playback.audiofocus.AudioFocusHelperApi21
import com.simplecityapps.playback.audiofocus.AudioFocusHelperApi26
import com.simplecityapps.playback.chromecast.CastService
import com.simplecityapps.playback.chromecast.CastSessionManager
import com.simplecityapps.playback.chromecast.HttpServer
import com.simplecityapps.playback.dsp.equalizer.Equalizer
import com.simplecityapps.playback.dsp.replaygain.ReplayGainAudioProcessor
import com.simplecityapps.playback.exoplayer.EqualizerAudioProcessor
import com.simplecityapps.playback.exoplayer.ExoPlayerPlayback
import com.simplecityapps.playback.mediasession.MediaSessionManager
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.queue.QueueWatcher
import com.simplecityapps.playback.sleeptimer.SleepTimer
import com.simplecityapps.provider.emby.EmbyMediaInfoProvider
import com.simplecityapps.provider.jellyfin.JellyfinMediaInfoProvider
import com.simplecityapps.provider.plex.PlexMediaInfoProvider
import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Named
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class PlaybackModule {

    @Singleton
    @Provides
    fun provideQueueWatcher(): QueueWatcher {
        return QueueWatcher()
    }

    @Singleton
    @Provides
    fun provideQueueManager(queueWatcher: QueueWatcher, preferenceManager: GeneralPreferenceManager): QueueManager {
        return QueueManager(queueWatcher, preferenceManager)
    }

    @Singleton
    @Provides
    fun provideEqualizer(playbackPreferenceManager: PlaybackPreferenceManager): EqualizerAudioProcessor {
        return EqualizerAudioProcessor(playbackPreferenceManager.equalizerEnabled).apply {
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
    }

    @Singleton
    @Provides
    fun provideReplayGainAudioProcessor(playbackPreferenceManager: PlaybackPreferenceManager): ReplayGainAudioProcessor {
        return ReplayGainAudioProcessor(playbackPreferenceManager.replayGainMode, playbackPreferenceManager.preAmpGain)
    }

    @Singleton
    @Provides
    fun providePlaybackPreferenceManager(sharedPreferences: SharedPreferences, moshi: Moshi): PlaybackPreferenceManager {
        return PlaybackPreferenceManager(sharedPreferences, moshi)
    }

    @Singleton
    @Provides
    fun provideAggregateMediaPathProvider(
        embyMediaPathProvider: EmbyMediaInfoProvider,
        jellyfinMediaPathProvider: JellyfinMediaInfoProvider,
        plexMediaPathProvider: PlexMediaInfoProvider
    ): AggregateMediaInfoProvider {
        return AggregateMediaInfoProvider(
            mutableSetOf(
                embyMediaPathProvider,
                jellyfinMediaPathProvider,
                plexMediaPathProvider
            )
        )
    }

    @Provides
    fun provideExoPlayerPlayback(
        @ApplicationContext context: Context,
        equalizerAudioProcessor: EqualizerAudioProcessor,
        replayGainAudioProcessor: ReplayGainAudioProcessor,
        mediaPathProvider: AggregateMediaInfoProvider
    ): ExoPlayerPlayback {
        return ExoPlayerPlayback(context, equalizerAudioProcessor, replayGainAudioProcessor, mediaPathProvider)
    }

    @Singleton
    @Provides
    fun providePlaybackWatcher(): PlaybackWatcher {
        return PlaybackWatcher()
    }

    @Singleton
    @Provides
    fun provideAudioFocusHelper(@ApplicationContext context: Context, playbackWatcher: PlaybackWatcher): AudioFocusHelper {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return AudioFocusHelperApi26(context, playbackWatcher)
        } else {
            return AudioFocusHelperApi21(context, playbackWatcher)
        }
    }

    @Provides
    fun provideMediaIdHelper(playlistRepository: PlaylistRepository, artistRepository: AlbumArtistRepository, albumRepository: AlbumRepository, songRepository: SongRepository): MediaIdHelper {
        return MediaIdHelper(playlistRepository, artistRepository, albumRepository, songRepository)
    }

    @Provides
    fun provideAudioManager(@ApplicationContext context: Context): AudioManager? {
        return context.getSystemService()
    }

    @Provides
    fun provideAudioEffectSessionManager(@ApplicationContext context: Context): AudioEffectSessionManager {
        return AudioEffectSessionManager(context)
    }

    @Singleton
    @Provides
    fun providePlaybackManager(
        queueManager: QueueManager,
        playback: ExoPlayerPlayback,
        playbackWatcher: PlaybackWatcher,
        audioFocusHelper: AudioFocusHelper,
        playbackPreferenceManager: PlaybackPreferenceManager,
        audioEffectSessionManager: AudioEffectSessionManager,
        @AppCoroutineScope coroutineScope: CoroutineScope,
        queueWatcher: QueueWatcher,
        audioManager: AudioManager?
    ): PlaybackManager {
        return PlaybackManager(queueManager, playbackWatcher, audioFocusHelper, playbackPreferenceManager, audioEffectSessionManager, coroutineScope, playback, queueWatcher, audioManager)
    }

    @Singleton
    @Provides
    fun provideCastService(@ApplicationContext context: Context, songRepository: SongRepository, artworkImageLoader: ArtworkImageLoader): CastService {
        return CastService(context, songRepository, artworkImageLoader)
    }

    @Singleton
    @Provides
    fun provideHttpServer(castService: CastService): HttpServer {
        return HttpServer(castService)
    }

    @Singleton
    @Provides
    fun provideCastSessionManager(
        @ApplicationContext context: Context,
        playbackManager: PlaybackManager,
        httpServer: HttpServer,
        exoPlayerPlayback: ExoPlayerPlayback,
        mediaPathProvider: AggregateMediaInfoProvider
    ): CastSessionManager {
        return CastSessionManager(playbackManager, context, httpServer, exoPlayerPlayback, mediaPathProvider)
    }

    @Singleton
    @Provides
    fun provideMediaSessionManager(
        @ApplicationContext context: Context,
        @AppCoroutineScope appCoroutineScope: CoroutineScope,
        playbackManager: PlaybackManager,
        queueManager: QueueManager,
        artistRepository: AlbumArtistRepository,
        albumRepository: AlbumRepository,
        songRepository: SongRepository,
        genreRepository: GenreRepository,
        artworkImageLoader: ArtworkImageLoader,
        artworkCache: LruCache<String, Bitmap?>,
        preferenceManager: GeneralPreferenceManager,
        playbackWatcher: PlaybackWatcher,
        queueWatcher: QueueWatcher,
        mediaIdHelper: MediaIdHelper
    ): MediaSessionManager {
        return MediaSessionManager(
            context,
            appCoroutineScope,
            playbackManager,
            queueManager,
            mediaIdHelper,
            artistRepository,
            albumRepository,
            songRepository,
            genreRepository,
            artworkImageLoader,
            artworkCache,
            preferenceManager,
            playbackWatcher,
            queueWatcher
        )
    }

    @Singleton
    @Provides
    fun provideNoiseManager(@ApplicationContext context: Context, playbackManager: PlaybackManager, playbackWatcher: PlaybackWatcher): NoiseManager {
        return NoiseManager(context, playbackManager, playbackWatcher)
    }

    @Singleton
    @Provides
    fun providePlaybackNotificationManager(
        @ApplicationContext context: Context,
        playbackManager: PlaybackManager,
        queueManager: QueueManager,
        mediaSessionManager: MediaSessionManager,
        playbackWatcher: PlaybackWatcher,
        queueWatcher: QueueWatcher,
        lruCache: LruCache<String, Bitmap>,
        artworkImageLoader: ArtworkImageLoader
    ): PlaybackNotificationManager {
        return PlaybackNotificationManager(
            context,
            context.getSystemService()!!,
            playbackManager,
            queueManager,
            mediaSessionManager,
            playbackWatcher,
            queueWatcher,
            lruCache,
            artworkImageLoader
        )
    }

    @Singleton
    @Provides
    fun provideSleepTimer(playbackManager: PlaybackManager, playbackWatcher: PlaybackWatcher): SleepTimer {
        return SleepTimer(playbackManager, playbackWatcher)
    }
}