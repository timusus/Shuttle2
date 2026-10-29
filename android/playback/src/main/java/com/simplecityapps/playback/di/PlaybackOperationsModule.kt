package com.simplecityapps.playback.di

import android.content.Context
import androidx.core.content.getSystemService
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.simplecityapps.playback.CallMonitor
import com.simplecityapps.playback.ForegroundHold
import com.simplecityapps.playback.PlaybackFacade
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.PlaybackService
import com.simplecityapps.playback.PlaybackStateSource
import com.simplecityapps.playback.chromecast.CastQueue
import com.simplecityapps.playback.chromecast.CastSessionManager
import com.simplecityapps.playback.engine.SongUriResolver
import com.simplecityapps.playback.persistence.QueueStore
import com.simplecityapps.playback.queue.QueueFacade
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.playback.settings.PlaybackSettings
import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.di.ApplicationContext
import com.simplecityapps.shuttle.di.MainImmediateDispatcher
import com.simplecityapps.shuttle.persistence.SourceStateStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

@ContributesTo(AppScope::class)
@BindingContainer
class PlaybackOperationsModule {
    /** Shared by the play [CallHold][com.simplecityapps.playback.CallHold] holds and the service it keeps in the foreground. */
    @SingleIn(AppScope::class)
    @Provides
    fun provideForegroundHold(
        @ApplicationContext context: Context
    ): ForegroundHold = ForegroundHold { PlaybackService.start(context) }

    @SingleIn(AppScope::class)
    @Provides
    fun providePlaybackFacade(
        @ApplicationContext context: Context,
        queueOperations: QueueOperations,
        player: Player,
        localPlayer: ExoPlayer,
        queueStore: QueueStore,
        playbackSettings: PlaybackSettings,
        @AppCoroutineScope coroutineScope: CoroutineScope,
        @MainImmediateDispatcher mainImmediateDispatcher: CoroutineDispatcher,
        castQueue: CastQueue,
        songUriResolver: SongUriResolver,
        foregroundHold: ForegroundHold,
        sourceState: SourceStateStore,
        queueFacade: QueueFacade
    ): PlaybackFacade = PlaybackFacade(
        queueOperations,
        player,
        localPlayer,
        queueStore,
        playbackSettings.playbackSpeed,
        CallMonitor(context.getSystemService()),
        coroutineScope,
        mainImmediateDispatcher,
        castQueue,
        castDeviceName = { CastSessionManager.deviceName(context) },
        isReplaceableTranscode = songUriResolver::isReplaceableTranscode,
        foregroundHold = foregroundHold,
        sourceState = sourceState,
        replaceSong = queueFacade::replaceSong
    )

    @Provides
    fun providePlaybackOperations(facade: PlaybackFacade): PlaybackOperations = facade

    @Provides
    fun providePlaybackStateSource(facade: PlaybackFacade): PlaybackStateSource = facade
}
