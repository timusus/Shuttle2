package com.simplecityapps.shuttle.core.dagger

import com.simplecityapps.playback.queue.PlaybackManager
import com.simplecityapps.playback.queue.QueueManager
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class PlaybackModule {

    @Singleton
    @Provides
    fun provideQueue(): QueueManager {
        return QueueManager()
    }

    @Singleton
    @Provides
    fun providePlaybackManager(queue: QueueManager): PlaybackManager {
        return PlaybackManager(queue)
    }
}