package com.simplecityapps.playback.dsp.crossfade

import androidx.media3.common.audio.AudioProcessor

/**
 * A debug build's window on what the [CrossfadeMixer] outputs: [processor] runs straight after the mixer and must pass
 * its audio through unchanged. Release builds bind none.
 */
interface CrossfadeOutputTap {
    val processor: AudioProcessor
}
