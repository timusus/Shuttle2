package com.simplecityapps.shuttle.di

import com.simplecityapps.mediaprovider.AggregatePlaybackReporter
import com.simplecityapps.mediaprovider.PlaybackReporter
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.mediaprovider.settings.LibrarySettings
import com.simplecityapps.shuttle.persistence.KeyValueStore
import com.simplecityapps.shuttle.playbackreporting.PendingPlays
import com.simplecityapps.shuttle.playbackreporting.PlaybackReportSender
import com.simplecityapps.shuttle.query.SongQuery
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.plus

@ContributesTo(AppScope::class)
@BindingContainer
class PlaybackReportingModule {
    // Each provider module contributes its reporter to the set.
    @Provides
    @SingleIn(AppScope::class)
    fun provideAggregatePlaybackReporter(reporters: Set<PlaybackReporter>): AggregatePlaybackReporter = AggregatePlaybackReporter(reporters)

    @Provides
    @SingleIn(AppScope::class)
    fun providePlaybackReportSender(
        playbackReporter: AggregatePlaybackReporter,
        store: KeyValueStore,
        songRepository: SongRepository,
        librarySettings: LibrarySettings,
        @AppCoroutineScope appCoroutineScope: CoroutineScope
    ): PlaybackReportSender = PlaybackReportSender(
        reporter = playbackReporter,
        pendingPlays = PendingPlays(store),
        findSongs = { songIds -> songRepository.getSongs(SongQuery.SongIds(songIds)).filterNotNull().firstOrNull().orEmpty() },
        isEnabled = { librarySettings.reportPlaybackToServer.value },
        now = { Clock.System.now() },
        scope = appCoroutineScope + Dispatchers.Default
    )
}
