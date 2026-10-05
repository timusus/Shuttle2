package com.simplecityapps.playback.di

import android.content.Context
import androidx.core.content.getSystemService
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.RemoteCastPlayer
import androidx.media3.common.Player
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.ExoPlayer
import androidx.tracing.trace
import com.simplecityapps.mediaprovider.AggregateMediaInfoProvider
import com.simplecityapps.mediaprovider.MediaInfoProvider
import com.simplecityapps.mediaprovider.ServerStreamPolicy
import com.simplecityapps.playback.AppPlayer
import com.simplecityapps.playback.AudioEffectSessionManager
import com.simplecityapps.playback.CallMonitor
import com.simplecityapps.playback.PlaybackFacade
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.chromecast.CastMediaItemConverter
import com.simplecityapps.playback.chromecast.CastQueue
import com.simplecityapps.playback.chromecast.CastSessionManager
import com.simplecityapps.playback.chromecast.CastStreams
import com.simplecityapps.playback.dsp.crossfade.crossfadeSkipped
import com.simplecityapps.playback.dsp.equalizer.DefaultEqualizerFrequencyResponse
import com.simplecityapps.playback.dsp.replaygain.ReplayGainAudioProcessor
import com.simplecityapps.playback.engine.SongUriResolver
import com.simplecityapps.playback.equalizer.EqualizerControl
import com.simplecityapps.playback.equalizer.EqualizerFrequencyResponse
import com.simplecityapps.playback.equalizer.EqualizerPresetStore
import com.simplecityapps.playback.equalizer.restorePreset
import com.simplecityapps.playback.exoplayer.AudioTrackMonitor
import com.simplecityapps.playback.exoplayer.EqualizerAudioProcessor
import com.simplecityapps.playback.exoplayer.ExoPlayerFactory
import com.simplecityapps.playback.exoplayer.MediaInfoMediaResolver
import com.simplecityapps.playback.exoplayer.followStoredSettings
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.persistence.QueueStore
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.playback.settings.PlaybackSettings
import com.simplecityapps.shuttle.analytics.Analytics
import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.di.ApplicationContext
import com.simplecityapps.shuttle.downloads.di.DownloadCache
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.persistence.KeyValueStore
import com.simplecityapps.shuttle.settings.EqualizerSettings
import com.simplecityapps.shuttle.streaming.DeliveredFormats
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient

@ContributesTo(AppScope::class)
@BindingContainer
class PlaybackEngineModule {
    @SingleIn(AppScope::class)
    @Provides
    fun provideEqualizer(
        playbackPreferenceManager: PlaybackPreferenceManager,
        equalizerSettings: EqualizerSettings,
        keyValueStore: KeyValueStore,
        @AppCoroutineScope appCoroutineScope: CoroutineScope
    ): EqualizerAudioProcessor = EqualizerAudioProcessor(equalizerSettings.enabled.value, equalizerSettings.preampGain.value).apply {
        preset = playbackPreferenceManager.restorePreset()
        followStoredSettings(equalizerSettings, playbackPreferenceManager, keyValueStore, appCoroutineScope)
    }

    @Provides
    fun provideEqualizerControl(equalizer: EqualizerAudioProcessor): EqualizerControl = equalizer

    @Provides
    fun provideEqualizerPresetStore(playbackPreferenceManager: PlaybackPreferenceManager): EqualizerPresetStore = playbackPreferenceManager

    @SingleIn(AppScope::class)
    @Provides
    fun provideReplayGainAudioProcessor(playbackSettings: PlaybackSettings): ReplayGainAudioProcessor = ReplayGainAudioProcessor(playbackSettings.replayGainMode.value, playbackSettings.preAmpGain.value.toDouble())

    @Provides
    fun provideEqualizerFrequencyResponse(): EqualizerFrequencyResponse = DefaultEqualizerFrequencyResponse()

    @SingleIn(AppScope::class)
    @Provides
    fun provideAggregateMediaInfoProvider(
        // Each remote provider module contributes its own entry (see MediaProviderTypeKey)
        providers: Map<MediaProviderType, @JvmSuppressWildcards MediaInfoProvider>,
        serverStreamPolicy: ServerStreamPolicy
    ): AggregateMediaInfoProvider = AggregateMediaInfoProvider(providers.values, serverStreamPolicy)

    @SingleIn(AppScope::class)
    @Provides
    fun provideAudioTrackMonitor(): AudioTrackMonitor = AudioTrackMonitor()

    @SingleIn(AppScope::class)
    @Provides
    fun provideSongUriResolver(mediaInfoProvider: AggregateMediaInfoProvider): SongUriResolver = SongUriResolver(MediaInfoMediaResolver(mediaInfoProvider))

    @SingleIn(AppScope::class)
    @Provides
    fun provideExoPlayerFactory(
        @ApplicationContext context: Context,
        equalizerAudioProcessor: EqualizerAudioProcessor,
        replayGainAudioProcessor: ReplayGainAudioProcessor,
        audioTrackMonitor: AudioTrackMonitor,
        songUriResolver: SongUriResolver,
        @DownloadCache downloadCache: Cache,
        playbackSettings: PlaybackSettings,
        analytics: Analytics,
        okHttpClient: OkHttpClient,
        deliveredFormats: DeliveredFormats
    ): ExoPlayerFactory = ExoPlayerFactory(
        context,
        okHttpClient,
        equalizerAudioProcessor,
        replayGainAudioProcessor,
        audioTrackMonitor,
        songUriResolver,
        downloadCache,
        { playbackSettings.crossfadeDurationMs.value.toLong() },
        analytics::crossfadeSkipped,
        onDownloadPlayed = { path -> deliveredFormats.record(path, null) }
    )

    // The local player: it owns the queue, and plays it when not casting. It lives on the main looper.
    @SingleIn(AppScope::class)
    @Provides
    fun provideExoPlayer(exoPlayerFactory: ExoPlayerFactory): ExoPlayer = trace("S2 build ExoPlayer") { exoPlayerFactory.create() }

    @SingleIn(AppScope::class)
    @Provides
    fun provideCastMediaItemConverter(
        @ApplicationContext context: Context,
        streams: CastStreams
    ): CastMediaItemConverter = CastMediaItemConverter(CastMediaItemConverter.wifiAddress(context), streams, context.getString(com.simplecityapps.core.R.string.unknown))

    @SingleIn(AppScope::class)
    @Provides
    fun provideCastQueue(
        @ApplicationContext context: Context,
        exoPlayer: ExoPlayer,
        converter: CastMediaItemConverter,
        streams: CastStreams
    ): CastQueue = CastQueue(
        exoPlayer,
        converter,
        streams,
        receiverWasRunning = { CastSessionManager.receiverWasRunning(context) },
        receiverPlayedOut = { CastSessionManager.receiverPlayedOut(context) }
    )

    // The player the app plays through: the ExoPlayer, then, once Cast is attached (see CastStarter), a Cast player
    // around it that plays on a Cast receiver while a Cast session is up. The Cast player is built on the main thread,
    // as Cast requires.
    @SingleIn(AppScope::class)
    @Provides
    fun provideAppPlayer(
        @ApplicationContext context: Context,
        exoPlayer: ExoPlayer,
        exoPlayerFactory: ExoPlayerFactory,
        converter: Lazy<CastMediaItemConverter>,
        castQueue: CastQueue,
        castSessionManager: Lazy<CastSessionManager>,
        audioEffectSessionManager: AudioEffectSessionManager
    ): AppPlayer = AppPlayer(exoPlayer) {
        if (castSessionManager.value.start()) {
            CastPlayer.Builder(context)
                .setLocalPlayer(exoPlayer)
                .setRemotePlayer(RemoteCastPlayer.Builder(context).setMediaItemConverter(converter.value).build())
                .setTransferCallback(castQueue)
                .build()
                .also(castQueue::attach)
        } else {
            null
        }
    }.also { player ->
        audioEffectSessionManager.attach(player, exoPlayer)
        exoPlayerFactory.crossfade.followCast(player)
    }

    @Provides
    fun providePlayer(appPlayer: AppPlayer): Player = appPlayer

    @SingleIn(AppScope::class)
    @Provides
    fun providePlaybackOperations(
        @ApplicationContext context: Context,
        queueOperations: QueueOperations,
        player: Player,
        localPlayer: ExoPlayer,
        queueStore: QueueStore,
        playbackSettings: PlaybackSettings,
        @AppCoroutineScope coroutineScope: CoroutineScope,
        castQueue: CastQueue
    ): PlaybackOperations = PlaybackFacade(
        queueOperations,
        player,
        localPlayer,
        queueStore,
        playbackSettings.playbackSpeed,
        CallMonitor(context.getSystemService()),
        coroutineScope,
        castQueue,
        castDeviceName = { CastSessionManager.deviceName(context) }
    )
}
