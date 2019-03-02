package com.simplecityapps.playback.dagger

import com.simplecityapps.playback.PlaybackService
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
abstract class PlaybackServiceModule {

    @ContributesAndroidInjector
    abstract fun bindPlaybackService(): PlaybackService

}