package com.simplecityapps.shuttle.di

import android.content.SharedPreferences
import com.simplecityapps.mediaprovider.AggregatePlaybackReporter
import com.simplecityapps.mediaprovider.PlaybackReporter
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.mediaprovider.settings.LibrarySettings
import com.simplecityapps.shuttle.playbackreporting.PendingPlays
import com.simplecityapps.shuttle.playbackreporting.PlaybackReportSender
import com.simplecityapps.shuttle.query.SongQuery
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.plus

@InstallIn(SingletonComponent::class)
@Module
class PlaybackReportingModule {
    // Each provider module contributes its reporter to the set.
    @Provides
    @Singleton
    fun provideAggregatePlaybackReporter(reporters: Set<@JvmSuppressWildcards PlaybackReporter>): AggregatePlaybackReporter = AggregatePlaybackReporter(reporters)

    @Provides
    @Singleton
    fun providePlaybackReportSender(
        playbackReporter: AggregatePlaybackReporter,
        sharedPreferences: SharedPreferences,
        songRepository: SongRepository,
        librarySettings: LibrarySettings,
        @AppCoroutineScope appCoroutineScope: CoroutineScope
    ): PlaybackReportSender = PlaybackReportSender(
        reporter = playbackReporter,
        pendingPlays = PendingPlays(sharedPreferences),
        findSongs = { songIds -> songRepository.getSongs(SongQuery.SongIds(songIds)).filterNotNull().firstOrNull().orEmpty() },
        isEnabled = { librarySettings.reportPlaybackToServer.value },
        now = { Clock.System.now() },
        scope = appCoroutineScope + Dispatchers.IO
    )
}
