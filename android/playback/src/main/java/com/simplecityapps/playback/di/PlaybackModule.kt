package com.simplecityapps.playback.di

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.media.AudioManager
import android.util.LruCache
import androidx.core.content.getSystemService
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.simplecityapps.mediaprovider.repository.albums.AlbumRepository
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistRepository
import com.simplecityapps.mediaprovider.repository.genres.GenreRepository
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.AudioEffectSessionManager
import com.simplecityapps.playback.BitPerfectOutput
import com.simplecityapps.playback.NoiseManager
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.PlaybackNotificationManager
import com.simplecityapps.playback.androidauto.MediaIdHelper
import com.simplecityapps.playback.dsp.replaygain.ReplayGainAudioProcessor
import com.simplecityapps.playback.exoplayer.AudioTrackMonitor
import com.simplecityapps.playback.exoplayer.EqualizerAudioProcessor
import com.simplecityapps.playback.mediasession.MediaSessionManager
import com.simplecityapps.playback.mediasession.UriSongResolver
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.playback.sleeptimer.SleepTimer
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
    fun provideQueueManager(preferenceManager: GeneralPreferenceManager): QueueManager = QueueManager(preferenceManager)

    @Provides
    fun provideQueueOperations(queueManager: QueueManager): QueueOperations = queueManager

    @Singleton
    @Provides
    fun providePlaybackPreferenceManager(
        sharedPreferences: SharedPreferences,
        moshi: Moshi
    ): PlaybackPreferenceManager = PlaybackPreferenceManager(sharedPreferences, moshi)

    @Provides
    fun provideMediaIdHelper(
        playlistRepository: PlaylistRepository,
        artistRepository: AlbumArtistRepository,
        albumRepository: AlbumRepository,
        songRepository: SongRepository
    ): MediaIdHelper = MediaIdHelper(playlistRepository, artistRepository, albumRepository, songRepository)

    @Provides
    fun provideUriSongResolver(
        @ApplicationContext context: Context,
        songRepository: SongRepository
    ): UriSongResolver = UriSongResolver(context, songRepository)

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
        mediaIdHelper: MediaIdHelper,
        uriSongResolver: UriSongResolver
    ): MediaSessionManager = MediaSessionManager(
        context,
        appCoroutineScope,
        playbackManager,
        queueManager,
        mediaIdHelper,
        uriSongResolver,
        artistRepository,
        albumRepository,
        songRepository,
        genreRepository,
        artworkImageLoader,
        artworkCache,
        preferenceManager
    )

    @Singleton
    @Provides
    fun provideNoiseManager(
        @ApplicationContext context: Context,
        playbackManager: PlaybackManager,
        @AppCoroutineScope appCoroutineScope: CoroutineScope
    ): NoiseManager = NoiseManager(context, playbackManager, appCoroutineScope)

    @Singleton
    @Provides
    fun provideBitPerfectOutput(
        audioManager: AudioManager?,
        playbackPreferenceManager: PlaybackPreferenceManager,
        audioTrackMonitor: AudioTrackMonitor,
        equalizerAudioProcessor: EqualizerAudioProcessor,
        replayGainAudioProcessor: ReplayGainAudioProcessor,
        @AppCoroutineScope appCoroutineScope: CoroutineScope
    ): BitPerfectOutput = BitPerfectOutput(audioManager, playbackPreferenceManager, audioTrackMonitor, equalizerAudioProcessor, replayGainAudioProcessor, appCoroutineScope)

    @Singleton
    @Provides
    fun providePlaybackNotificationManager(
        @ApplicationContext context: Context,
        playbackManager: PlaybackManager,
        queueManager: QueueManager,
        mediaSessionManager: MediaSessionManager,
        lruCache: LruCache<String, Bitmap>,
        artworkImageLoader: ArtworkImageLoader
    ): PlaybackNotificationManager = PlaybackNotificationManager(
        context,
        context.getSystemService()!!,
        playbackManager,
        queueManager,
        mediaSessionManager,
        lruCache,
        artworkImageLoader
    )

    @Singleton
    @Provides
    fun provideSleepTimer(
        playbackManager: PlaybackManager,
        @AppCoroutineScope appCoroutineScope: CoroutineScope
    ): SleepTimer = SleepTimer(playbackManager, appCoroutineScope)
}
