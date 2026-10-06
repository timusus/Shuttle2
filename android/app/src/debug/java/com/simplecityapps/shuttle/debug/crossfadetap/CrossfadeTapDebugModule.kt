package com.simplecityapps.shuttle.debug.crossfadetap

import androidx.media3.common.audio.AudioProcessor
import com.simplecityapps.playback.dsp.crossfade.CrossfadeOutputTap
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import java.util.Optional

/** Binds the debug-only WAV tap on the crossfade mixer's output; release has no binding, so the playback module's default (none) applies. */
@BindingContainer
@ContributesTo(AppScope::class)
class CrossfadeTapDebugModule {
    @SingleIn(AppScope::class)
    @Provides
    fun provideWavTap(): WavTapAudioProcessor = WavTapAudioProcessor()

    @Provides
    fun provideCrossfadeOutputTap(tap: WavTapAudioProcessor): Optional<CrossfadeOutputTap> = Optional.of(
        object : CrossfadeOutputTap {
            override val processor: AudioProcessor = tap
        }
    )
}
