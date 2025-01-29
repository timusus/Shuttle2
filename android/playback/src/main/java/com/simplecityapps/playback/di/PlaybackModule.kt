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
import com.simplecityapps.mediaprovider.repository.albums.AlbumRepository
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistRepository
import com.simplecityapps.mediaprovider.repository.genres.GenreRepository
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.AudioEffectSessionManager
import com.simplecityapps.playback.NoiseManager
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.PlaybackNotificationManager
import com.simplecityapps.playback.PlaybackWatcher
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
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

@InstallIn(SingletonComponent::class)
@Module
class PlaybackModule {
    @Singleton
    @Provides
    fun provideQueueWatcher(): QueueWatcher = QueueWatcher()

    @Singleton
    @Provides
    fun provideQueueManager(
        queueWatcher: QueueWatcher,
        preferenceManager: GeneralPreferenceManager
    ): QueueManager = QueueManager(queueWatcher, preferenceManager)

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
    fun providePlaybackPreferenceManager(
        sharedPreferences: SharedPreferences,
        moshi: Moshi
    ): PlaybackPreferenceManager = PlaybackPreferenceManager(sharedPreferences, moshi)

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

    @Singleton
    @Provides
    fun providePlaybackWatcher(): PlaybackWatcher = PlaybackWatcher()

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

    @Provides
    fun provideMediaIdHelper(
        playlistRepository: PlaylistRepository,
        artistRepository: AlbumArtistRepository,
        albumRepository: AlbumRepository,
        songRepository: SongRepository
    ): MediaIdHelper = MediaIdHelper(playlistRepository, artistRepository, albumRepository, songRepository)

    @Provides
    fun provideAudioManager(
        @ApplicationContext context: Context
    ): AudioManager? = context.getSystemService()

    @Provides
    fun provideAudioEffectSessionManager(
        @ApplicationContext context: Context
    ): AudioEffectSessionManager = AudioEffectSessionManager(context)

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
    ): PlaybackManager = PlaybackManager(queueManager, playbackWatcher, audioFocusHelper, playbackPreferenceManager, audioEffectSessionManager, coroutineScope, playback, queueWatcher, audioManager)

    @Singleton
    @Provides
    fun provideCastService(
        @ApplicationContext context: Context,
        songRepository: SongRepository,
        artworkImageLoader: ArtworkImageLoader
    ): CastService = CastService(context, songRepository, artworkImageLoader)

    @Singleton
    @Provides
    fun provideHttpServer(castService: CastService): HttpServer = HttpServer(castService)

    @Singleton
    @Provides
    fun provideCastSessionManager(
        @ApplicationContext context: Context,
        playbackManager: PlaybackManager,
        httpServer: HttpServer,
        exoPlayerPlayback: ExoPlayerPlayback,
        mediaPathProvider: AggregateMediaInfoProvider
    ): CastSessionManager = CastSessionManager(playbackManager, context, httpServer, exoPlayerPlayback, mediaPathProvider)

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
    ): MediaSessionManager = MediaSessionManager(
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

    @Singleton
    @Provides
    fun provideNoiseManager(
        @ApplicationContext context: Context,
        playbackManager: PlaybackManager,
        playbackWatcher: PlaybackWatcher
    ): NoiseManager = NoiseManager(context, playbackManager, playbackWatcher)

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
    ): PlaybackNotificationManager = PlaybackNotificationManager(
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

    @Singleton
    @Provides
    fun provideSleepTimer(
        playbackManager: PlaybackManager,
        playbackWatcher: PlaybackWatcher
    ): SleepTimer = SleepTimer(playbackManager, playbackWatcher)
}
