package com.simplecityapps.playback.di

import android.content.Context
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.ExoPlayer
import androidx.tracing.trace
import com.simplecityapps.playback.dsp.crossfade.CrossfadeOutputTap
import com.simplecityapps.playback.dsp.crossfade.crossfadeSkipped
import com.simplecityapps.playback.dsp.replaygain.ReplayGainAudioProcessor
import com.simplecityapps.playback.engine.SongUriResolver
import com.simplecityapps.playback.exoplayer.AudioTrackMonitor
import com.simplecityapps.playback.exoplayer.EqualizerAudioProcessor
import com.simplecityapps.playback.exoplayer.ExoPlayerFactory
import com.simplecityapps.playback.settings.PlaybackSettings
import com.simplecityapps.shuttle.analytics.Analytics
import com.simplecityapps.shuttle.di.ApplicationContext
import com.simplecityapps.shuttle.downloads.di.DownloadCache
import com.simplecityapps.shuttle.streaming.DeliveredFormats
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import java.util.Optional
import okhttp3.OkHttpClient
import timber.log.Timber

@ContributesTo(AppScope::class)
@BindingContainer
class ExoPlayerModule {
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
        deliveredFormats: DeliveredFormats,
        // Only a debug build binds one; release falls back to the default.
        crossfadeOutputTap: Optional<CrossfadeOutputTap> = Optional.empty()
    ): ExoPlayerFactory = ExoPlayerFactory(
        context,
        okHttpClient,
        equalizerAudioProcessor,
        replayGainAudioProcessor,
        audioTrackMonitor,
        songUriResolver,
        downloadCache,
        { playbackSettings.crossfadeDurationMs.value.toLong() },
        { skip ->
            analytics.crossfadeSkipped(skip)
            // The tap is bound in debug builds only: there, say why a transition played without its crossfade.
            if (crossfadeOutputTap.isPresent) Timber.w("Crossfade skipped: ${skip.value}")
        },
        onDownloadPlayed = { path -> deliveredFormats.record(path, null) },
        crossfadeOutputTap = crossfadeOutputTap.orElse(null)
    )

    // The local player: it owns the queue, and plays it when not casting. It lives on the main looper.
    @SingleIn(AppScope::class)
    @Provides
    fun provideExoPlayer(exoPlayerFactory: ExoPlayerFactory): ExoPlayer = trace("S2 build ExoPlayer") { exoPlayerFactory.create() }
}
