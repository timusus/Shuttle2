package com.simplecityapps.shuttle.shared.di

import com.simplecityapps.mediaprovider.server.StreamProfile
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.provider.emby.EmbyStreamUrlProvider
import com.simplecityapps.provider.jellyfin.JellyfinStreamUrlProvider
import com.simplecityapps.shuttle.di.AppSupervisorJob
import com.simplecityapps.shuttle.settings.Setting
import com.simplecityapps.shuttle.settings.SettingsStore
import com.simplecityapps.shuttle.shared.playback.IosAudioPlayer
import com.simplecityapps.shuttle.shared.playback.IosPlayerController
import com.simplecityapps.shuttle.shared.playback.IosStreamResolver
import com.simplecityapps.shuttle.shared.playback.SongStreamResolver
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlin.random.Random
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job

/**
 * iOS playback: the [IosPlayerController] over the Swift engine the graph's factory is given, as the app's
 * [PlaybackOperations] and [QueueOperations], so the shared use cases resolve unchanged, and the [StreamProfile] its
 * FFmpeg build plays, for the Jellyfin and Emby stream URLs.
 */
@ContributesTo(AppScope::class)
@BindingContainer
class IosPlaybackModule {
    @Provides
    fun provideStreamProfile(): StreamProfile = StreamProfile.Ios

    @Provides
    @SingleIn(AppScope::class)
    fun provideStreamResolver(
        jellyfin: JellyfinStreamUrlProvider,
        emby: EmbyStreamUrlProvider
    ): IosStreamResolver = SongStreamResolver(listOf(jellyfin, emby))

    /**
     * On the main thread, as the controller needs (`immediate`, so a call made there runs straight away), under the
     * app's supervisor job and exception handler.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun providePlayerController(
        player: IosAudioPlayer,
        resolver: IosStreamResolver,
        @AppSupervisorJob job: Job,
        exceptionHandler: CoroutineExceptionHandler,
        settingsStore: SettingsStore,
        random: Random
    ): IosPlayerController {
        val retainShuffle = settingsStore.preference(RetainShuffleOnNewQueue)
        return IosPlayerController(
            player = player,
            resolver = resolver,
            scope = CoroutineScope(job + Dispatchers.Main.immediate + exceptionHandler),
            retainShuffleOnNewQueue = { retainShuffle.value },
            random = random
        )
    }

    @Provides
    fun providePlaybackOperations(controller: IosPlayerController): PlaybackOperations = controller

    @Provides
    fun provideQueueOperations(controller: IosPlayerController): QueueOperations = controller.queueOperations

    private companion object {
        /** Android's `PlaybackSettings.RetainShuffleOnNewQueue`, which lives in the Android-only playback module. */
        val RetainShuffleOnNewQueue = Setting.boolean("pref_retain_shuffle_on_new_queue", false)
    }
}
