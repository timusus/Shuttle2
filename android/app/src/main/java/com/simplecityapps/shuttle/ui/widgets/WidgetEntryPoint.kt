package com.simplecityapps.shuttle.ui.widgets

import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun playbackManager(): PlaybackManager
    fun queueManager(): QueueManager
    fun preferenceManager(): GeneralPreferenceManager
}
