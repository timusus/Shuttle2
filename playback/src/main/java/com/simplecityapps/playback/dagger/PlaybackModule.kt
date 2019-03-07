package com.simplecityapps.playback.dagger

import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService
import com.simplecityapps.playback.Playback
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.PlaybackNotificationManager
import com.simplecityapps.playback.local.MediaPlayerPlayback
import com.simplecityapps.playback.queue.QueueManager
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class PlaybackModule(val context: Context) {

    @Singleton
    @Provides
    fun provideQueue(): QueueManager {
        return QueueManager()
    }

    @Provides
    fun providePlayback(queueManager: QueueManager): Playback {
        return MediaPlayerPlayback(queueManager)
    }

    @Singleton
    @Provides
    fun providePlaybackManager(queue: QueueManager, playback: Playback): PlaybackManager {
        return PlaybackManager(context, queue, playback)
    }

    @Singleton
    @Provides
    fun providePlaybackNotificationManager(playbackManager: PlaybackManager, queueManager: QueueManager): PlaybackNotificationManager {
        return PlaybackNotificationManager(context, context.getSystemService<NotificationManager>()!!, playbackManager, queueManager)
    }
}