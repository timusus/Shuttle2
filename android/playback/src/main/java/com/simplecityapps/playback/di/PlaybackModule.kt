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
import com.simplecityapps.playback.NoiseManager
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.PlaybackNotificationManager
import com.simplecityapps.playback.PlaybackWatcher
import com.simplecityapps.playback.androidauto.MediaIdHelper
import com.simplecityapps.playback.mediasession.MediaSessionManager
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.playback.queue.QueueWatcher
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
    fun provideQueueWatcher(): QueueWatcher = QueueWatcher()

    @Singleton
    @Provides
    fun provideQueueManager(
        queueWatcher: QueueWatcher,
        preferenceManager: GeneralPreferenceManager
    ): QueueManager = QueueManager(queueWatcher, preferenceManager)

    @Provides
    fun provideQueueOperations(queueManager: QueueManager): QueueOperations = queueManager

    @Singleton
    @Provides
    fun providePlaybackPreferenceManager(
        sharedPreferences: SharedPreferences,
        moshi: Moshi
    ): PlaybackPreferenceManager = PlaybackPreferenceManager(sharedPreferences, moshi)

    @Singleton
    @Provides
    fun providePlaybackWatcher(): PlaybackWatcher = PlaybackWatcher()

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
