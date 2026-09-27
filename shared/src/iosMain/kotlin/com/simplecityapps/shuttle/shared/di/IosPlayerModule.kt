package com.simplecityapps.shuttle.shared.di

import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.settings.PlaybackSettings
import com.simplecityapps.playback.sleeptimer.SleepTimer
import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.ui.shell.player.CastAvailability
import com.simplecityapps.shuttle.ui.shell.player.ObserveGatedServerSkip
import com.simplecityapps.shuttle.ui.shell.player.ReplayGainModeSetting
import com.simplecityapps.shuttle.ui.shell.player.SavedNowPlaying
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.emptyFlow

/**
 * What the shared `PlayerViewModel` needs beyond playback, bound for what iOS has today: no Cast (the iOS app has no
 * Cast sender), the saved song to show until the saved queue is restored (as on Android), and no
 * S2 Pro gating of server songs (entitlements arrive in phase 9). The sleep timer is the shared one, on the player
 * controller. ReplayGain is stored as on Android, but the iOS engine plays at unity gain until it applies it.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object IosPlayerModule {
    @Provides
    fun provideCastAvailability(): CastAvailability = CastAvailability { false }

    @Provides
    fun provideSavedNowPlaying(playbackPreferenceManager: PlaybackPreferenceManager): SavedNowPlaying = SavedNowPlaying { playbackPreferenceManager.nowPlaying }

    @Provides
    fun provideObserveGatedServerSkip(): ObserveGatedServerSkip = ObserveGatedServerSkip { emptyFlow() }

    @Provides
    fun provideReplayGainModeSetting(): ReplayGainModeSetting = ReplayGainModeSetting { PlaybackSettings.ReplayGain }

    @Provides
    @SingleIn(AppScope::class)
    fun provideSleepTimer(
        playbackOperations: PlaybackOperations,
        @AppCoroutineScope appCoroutineScope: CoroutineScope
    ): SleepTimer = SleepTimer(playbackOperations, appCoroutineScope)
}
