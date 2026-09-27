package com.simplecityapps.shuttle.ui.shell.player

import com.simplecityapps.playback.chromecast.CastSessionManager
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides

@BindingContainer
@ContributesTo(AppScope::class)
object PlayerModule {
    @Provides
    fun provideCastAvailability(castSessionManager: CastSessionManager): CastAvailability = CastAvailability { castSessionManager.isAvailable }

    @Provides
    fun provideSavedNowPlaying(playbackPreferenceManager: PlaybackPreferenceManager): SavedNowPlaying = SavedNowPlaying { playbackPreferenceManager.nowPlaying }
}
