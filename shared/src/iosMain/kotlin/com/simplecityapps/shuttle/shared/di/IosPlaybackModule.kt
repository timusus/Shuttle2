package com.simplecityapps.shuttle.shared.di

import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.mediaprovider.server.StreamProfile
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.dsp.equalizer.DefaultEqualizerFrequencyResponse
import com.simplecityapps.playback.equalizer.EqualizerControl
import com.simplecityapps.playback.equalizer.EqualizerFrequencyResponse
import com.simplecityapps.playback.equalizer.EqualizerPresetStore
import com.simplecityapps.playback.equalizer.KeyValueEqualizerPresetStore
import com.simplecityapps.playback.equalizer.restorePreset
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.persistence.resumePosition
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.playback.settings.PlaybackSettings
import com.simplecityapps.provider.emby.EmbyStreamUrlProvider
import com.simplecityapps.provider.jellyfin.JellyfinStreamUrlProvider
import com.simplecityapps.shuttle.di.AppSupervisorJob
import com.simplecityapps.shuttle.persistence.KeyValueStore
import com.simplecityapps.shuttle.settings.EqualizerSettings
import com.simplecityapps.shuttle.shared.playback.IosAudioPlayer
import com.simplecityapps.shuttle.shared.playback.IosEqualizer
import com.simplecityapps.shuttle.shared.playback.IosPlaybackStore
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
 * FFmpeg build plays, for the Jellyfin and Emby stream URLs. The equalizer ([IosEqualizer]) designs the engine's
 * filters with the shared maths, and each resolved stream carries its ReplayGain. The queue, position, modes and speed
 * are kept across launches in Android's prefs ([PlaybackPreferenceManager]) by an [IosPlaybackStore] started with the
 * controller.
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
        emby: EmbyStreamUrlProvider,
        playbackSettings: PlaybackSettings
    ): IosStreamResolver {
        val replayGainMode = playbackSettings.replayGainMode
        val preAmpGain = playbackSettings.preAmpGain
        return SongStreamResolver(listOf(jellyfin, emby), { replayGainMode.value }, { preAmpGain.value })
    }

    @Provides
    @SingleIn(AppScope::class)
    fun providePlaybackPreferenceManager(store: KeyValueStore): PlaybackPreferenceManager = PlaybackPreferenceManager(store)

    @Provides
    fun provideEqualizerPresetStore(store: KeyValueStore): EqualizerPresetStore = KeyValueEqualizerPresetStore(store)

    /** Built with the saved equalizer, which it hands the engine straight away. */
    @Provides
    @SingleIn(AppScope::class)
    fun provideEqualizer(
        player: IosAudioPlayer,
        presetStore: EqualizerPresetStore,
        equalizerSettings: EqualizerSettings
    ): IosEqualizer = IosEqualizer(
        player = player,
        enabled = equalizerSettings.enabled.value,
        preset = presetStore.restorePreset(),
        preampGainDb = equalizerSettings.preampGain.value
    )

    @Provides
    fun provideEqualizerControl(equalizer: IosEqualizer): EqualizerControl = equalizer

    @Provides
    fun provideEqualizerFrequencyResponse(): EqualizerFrequencyResponse = DefaultEqualizerFrequencyResponse()

    /**
     * On the main thread, as the controller needs (`immediate`, so a call made there runs straight away), under the
     * app's supervisor job and exception handler. Built with its [IosPlaybackStore], which restores the saved queue,
     * modes, position and speed and saves them from then on; the queue counts as restored once that's done.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun providePlayerController(
        player: IosAudioPlayer,
        // Built with the player, so the saved equalizer reaches the engine before anything plays
        @Suppress("UNUSED_PARAMETER") equalizer: IosEqualizer,
        resolver: IosStreamResolver,
        @AppSupervisorJob job: Job,
        exceptionHandler: CoroutineExceptionHandler,
        playbackSettings: PlaybackSettings,
        playbackPreferenceManager: PlaybackPreferenceManager,
        songRepository: SongRepository,
        random: Random
    ): IosPlayerController {
        val retainShuffle = playbackSettings.retainShuffleOnNewQueue
        val scope = CoroutineScope(job + Dispatchers.Main.immediate + exceptionHandler)
        val controller = IosPlayerController(
            player = player,
            resolver = resolver,
            scope = scope,
            retainShuffleOnNewQueue = { retainShuffle.value },
            random = random,
            resumePosition = playbackPreferenceManager::resumePosition
        )
        IosPlaybackStore(controller, playbackPreferenceManager, playbackSettings.playbackSpeed, songRepository, scope).start()
        return controller
    }

    @Provides
    fun providePlaybackOperations(controller: IosPlayerController): PlaybackOperations = controller

    @Provides
    fun provideQueueOperations(controller: IosPlayerController): QueueOperations = controller.queueOperations
}
