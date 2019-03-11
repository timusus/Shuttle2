package com.simplecityapps.playback.dagger

import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.core.content.getSystemService
import com.simplecityapps.playback.Playback
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.PlaybackNotificationManager
import com.simplecityapps.playback.audiofocus.AudioFocusHelper
import com.simplecityapps.playback.audiofocus.AudioFocusHelperApi21
import com.simplecityapps.playback.audiofocus.AudioFocusHelperApi26
import com.simplecityapps.playback.local.mediaplayer.MediaPlayerPlayback
import com.simplecityapps.playback.mediasession.MediaSessionManager
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.queue.QueueManager
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
    fun provideQueueManager(): QueueManager {
        return QueueManager()
    }

    @Provides
    fun providePlayback(queueManager: QueueManager): Playback {
        return MediaPlayerPlayback(queueManager)
    }

    @Singleton
    @Provides
    fun provideAudioFocusHelper(): AudioFocusHelper {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return AudioFocusHelperApi26(context)
        } else {
            return AudioFocusHelperApi21(context)
        }
    }

    @Singleton
    @Provides
    fun providePlaybackManager(queue: QueueManager, playback: Playback, audioFocusHelper: AudioFocusHelper): PlaybackManager {
        return PlaybackManager(queue, playback, audioFocusHelper)
    }

    @Singleton
    @Provides
    fun provideMediaSessionManager(playbackManager: PlaybackManager, queueManager: QueueManager): MediaSessionManager {
        return MediaSessionManager(context, playbackManager, queueManager)
    }

    @Singleton
    @Provides
    fun providePlaybackNotificationManager(playbackManager: PlaybackManager, queueManager: QueueManager, mediaSessionManager: MediaSessionManager): PlaybackNotificationManager {
        return PlaybackNotificationManager(context, context.getSystemService<NotificationManager>()!!, playbackManager, queueManager, mediaSessionManager)
    }

    @Singleton
    @Provides
    fun providePlaybackPreferenceManager(): PlaybackPreferenceManager {
        return PlaybackPreferenceManager(sharedPreferences)
    }
}