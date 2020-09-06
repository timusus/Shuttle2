package com.simplecityapps.playback.dagger

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Build
import android.util.LruCache
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
import com.simplecityapps.playback.equalizer.Equalizer
import com.simplecityapps.playback.local.exoplayer.EqualizerAudioProcessor
import com.simplecityapps.playback.local.exoplayer.ExoPlayerPlayback
import com.simplecityapps.playback.local.mediaplayer.MediaPlayerPlayback
import com.simplecityapps.playback.mediasession.MediaSessionManager
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.queue.QueueWatcher
import com.simplecityapps.playback.sleeptimer.SleepTimer
import com.simplecityapps.shuttle.dagger.AppScope
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.CoroutineScope
import javax.inject.Named

@Module
class PlaybackModule {

    @AppScope
    @Provides
    fun provideQueueWatcher(): QueueWatcher {
        return QueueWatcher()
    }

    @AppScope
    @Provides
    fun provideQueueManager(queueWatcher: QueueWatcher): QueueManager {
        return QueueManager(queueWatcher)
    }

    @AppScope
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

    @AppScope
    @Provides
    fun providePlaybackPreferenceManager(sharedPreferences: SharedPreferences, moshi: Moshi): PlaybackPreferenceManager {
        return PlaybackPreferenceManager(sharedPreferences, moshi)
    }

    @Provides
    fun providePlayback(context: Context, playbackPreferenceManager: PlaybackPreferenceManager, equalizerAudioProcessor: EqualizerAudioProcessor): Playback {
        return if (playbackPreferenceManager.useAndroidMediaPlayer) {
            MediaPlayerPlayback(context)
        } else {
            ExoPlayerPlayback(context, equalizerAudioProcessor)
        }
    }

    @AppScope
    @Provides
    fun providePlaybackWatcher(): PlaybackWatcher {
        return PlaybackWatcher()
    }

    @AppScope
    @Provides
    fun provideAudioFocusHelper(context: Context, playbackWatcher: PlaybackWatcher): AudioFocusHelper {
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

    @AppScope
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

    @AppScope
    @Provides
    fun provideCastService(context: Context, songRepository: SongRepository): CastService {
        return CastService(context, songRepository)
    }

    @AppScope
    @Provides
    fun provideHttpServer(castService: CastService): HttpServer {
        return HttpServer(castService)
    }

    @AppScope
    @Provides
    fun provideCastSessionManager(context: Context, playbackManager: PlaybackManager, httpServer: HttpServer): CastSessionManager {
        return CastSessionManager(playbackManager, context, httpServer)
    }

    @AppScope
    @Provides
    fun provideMediaSessionManager(
        context: Context,
        @Named("AppCoroutineScope") appCoroutineScope: CoroutineScope,
        playbackManager: PlaybackManager,
        queueManager: QueueManager,
        playbackWatcher: PlaybackWatcher,
        queueWatcher: QueueWatcher,
        mediaIdHelper: MediaIdHelper
    ): MediaSessionManager {
        return MediaSessionManager(context, appCoroutineScope, playbackManager, queueManager, mediaIdHelper, playbackWatcher, queueWatcher)
    }

    @AppScope
    @Provides
    fun provideNoiseManager(context: Context, playbackManager: PlaybackManager, playbackWatcher: PlaybackWatcher): NoiseManager {
        return NoiseManager(context, playbackManager, playbackWatcher)
    }

    @AppScope
    @Provides
    fun providePlaybackNotificationManager(
        context: Context,
        playbackManager: PlaybackManager,
        queueManager: QueueManager,
        mediaSessionManager: MediaSessionManager,
        playbackWatcher: PlaybackWatcher,
        queueWatcher: QueueWatcher,
        lruCache: LruCache<String, Bitmap>
    ): PlaybackNotificationManager {
        return PlaybackNotificationManager(
            context,
            context.getSystemService()!!,
            playbackManager,
            queueManager,
            mediaSessionManager,
            playbackWatcher,
            queueWatcher,
            lruCache
        )
    }

    @AppScope
    @Provides
    fun provideSleepTimer(playbackManager: PlaybackManager, playbackWatcher: PlaybackWatcher): SleepTimer {
        return SleepTimer(playbackManager, playbackWatcher)
    }
}