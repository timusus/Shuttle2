package com.simplecityapps.playback.dagger

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.core.content.getSystemService
import com.simplecityapps.mediaprovider.repository.AlbumArtistRepository
import com.simplecityapps.mediaprovider.repository.AlbumRepository
import com.simplecityapps.mediaprovider.repository.PlaylistRepository
import com.simplecityapps.mediaprovider.repository.SongRepository
import com.simplecityapps.playback.*
import com.simplecityapps.playback.androidauto.MediaIdHelper
import com.simplecityapps.playback.audiofocus.AudioFocusHelper
import com.simplecityapps.playback.audiofocus.AudioFocusHelperApi21
import com.simplecityapps.playback.audiofocus.AudioFocusHelperApi26
import com.simplecityapps.playback.chromecast.CastService
import com.simplecityapps.playback.chromecast.CastSessionManager
import com.simplecityapps.playback.chromecast.HttpServer
import com.simplecityapps.playback.local.mediaplayer.MediaPlayerPlayback
import com.simplecityapps.playback.mediasession.MediaSessionManager
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.queue.QueueWatcher
import com.simplecityapps.playback.sleeptimer.SleepTimer
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class PlaybackModule(
    private val context: Context,
    private val sharedPreferences: SharedPreferences
) {

    @Singleton
    @Provides
    fun provideQueueWatcher(): QueueWatcher {
        return QueueWatcher()
    }

    @Singleton
    @Provides
    fun provideQueueManager(queueWatcher: QueueWatcher): QueueManager {
        return QueueManager(queueWatcher)
    }

    @Provides
    fun providePlayback(): Playback {
        return MediaPlayerPlayback(context)
    }

    @Singleton
    @Provides
    fun providePlaybackWatcher(): PlaybackWatcher {
        return PlaybackWatcher()
    }

    @Singleton
    @Provides
    fun provideAudioFocusHelper(playbackWatcher: PlaybackWatcher): AudioFocusHelper {
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

    @Singleton
    @Provides
    fun providePlaybackPreferenceManager(): PlaybackPreferenceManager {
        return PlaybackPreferenceManager(sharedPreferences)
    }

    @Singleton
    @Provides
    fun providePlaybackManager(
        queueManager: QueueManager,
        playback: Playback,
        playbackWatcher: PlaybackWatcher,
        audioFocusHelper: AudioFocusHelper,
        playbackPreferenceManager: PlaybackPreferenceManager,
        queueWatcher: QueueWatcher
    ): PlaybackManager {
        return PlaybackManager(queueManager, playback, playbackWatcher, audioFocusHelper, playbackPreferenceManager, queueWatcher)
    }

    @Singleton
    @Provides
    fun provideCastService(songRepository: SongRepository): CastService {
        return CastService(context, songRepository)
    }

    @Singleton
    @Provides
    fun provideHttpServer(castService: CastService): HttpServer {
        return HttpServer(castService)
    }

    @Singleton
    @Provides
    fun provideCastSessionManager(playbackManager: PlaybackManager, httpServer: HttpServer): CastSessionManager {
        return CastSessionManager(playbackManager, context, httpServer)
    }

    @Singleton
    @Provides
    fun provideMediaSessionManager(
        playbackManager: PlaybackManager,
        queueManager: QueueManager,
        playbackWatcher: PlaybackWatcher,
        queueWatcher: QueueWatcher,
        mediaIdHelper: MediaIdHelper
    ): MediaSessionManager {
        return MediaSessionManager(context, playbackManager, queueManager, mediaIdHelper, playbackWatcher, queueWatcher)
    }

    @Singleton
    @Provides
    fun provideNoiseManager(playbackManager: PlaybackManager, playbackWatcher: PlaybackWatcher): NoiseManager {
        return NoiseManager(context, playbackManager, playbackWatcher)
    }

    @Singleton
    @Provides
    fun providePlaybackNotificationManager(
        playbackManager: PlaybackManager,
        queueManager: QueueManager,
        mediaSessionManager: MediaSessionManager,
        playbackWatcher: PlaybackWatcher,
        queueWatcher: QueueWatcher
    ): PlaybackNotificationManager {
        return PlaybackNotificationManager(
            context,
            context.getSystemService()!!,
            playbackManager,
            queueManager,
            mediaSessionManager,
            playbackWatcher,
            queueWatcher
        )
    }

    @Singleton
    @Provides
    fun provideSleepTimer(playbackManager: PlaybackManager, playbackWatcher: PlaybackWatcher): SleepTimer {
        return SleepTimer(playbackManager, playbackWatcher)
    }
}