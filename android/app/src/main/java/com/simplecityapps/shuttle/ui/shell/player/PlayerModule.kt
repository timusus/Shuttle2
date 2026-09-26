package com.simplecityapps.shuttle.ui.shell.player

import com.simplecityapps.playback.chromecast.CastSessionManager
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.shuttle.entitlement.EntitledServerStreamPolicy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {
    @Provides
    fun provideCastAvailability(castSessionManager: CastSessionManager): CastAvailability = CastAvailability { castSessionManager.isAvailable }

    @Provides
    fun provideSavedNowPlaying(playbackPreferenceManager: PlaybackPreferenceManager): SavedNowPlaying = SavedNowPlaying { playbackPreferenceManager.nowPlaying }

    @Provides
    fun provideObserveGatedServerSkip(policy: EntitledServerStreamPolicy): ObserveGatedServerSkip = ObserveGatedServerSkip { policy.gatedSongs }
}
