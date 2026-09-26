package com.simplecityapps.playback.exoplayer

import androidx.core.math.MathUtils.clamp
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import com.simplecityapps.playback.dsp.equalizer.BandProcessor
import com.simplecityapps.playback.dsp.equalizer.Equalizer
import com.simplecityapps.playback.dsp.equalizer.EqualizerBand
import com.simplecityapps.playback.dsp.equalizer.cascadeAttenuation
import com.simplecityapps.playback.dsp.equalizer.toNyquistBand
import com.simplecityapps.playback.exoplayer.ByteUtils.getInt24
import com.simplecityapps.playback.exoplayer.ByteUtils.putInt24
import java.nio.ByteBuffer
import timber.log.Timber

/**
 * Applies the selected [preset] to 16 and 24 bit PCM.
 *
 * [enabled] and [preset] are set on the main thread and read on the playback thread. Each change
 * publishes one immutable [Settings] snapshot through a volatile field, so the audio thread sees a
 * change whole, and picks it up from its next buffer. The band filters are built from the snapshot
 * on the playback thread and only ever touched there.
 */
class EqualizerAudioProcessor(enabled: Boolean) : BaseAudioProcessor() {
    /** What the audio thread applies. [bands] are copies: the custom preset's bands are edited in place. */
    private class Settings(
        val enabled: Boolean,
        val bands: List<EqualizerBand>
    )

    @Volatile
    private var settings = Settings(enabled, Equalizer.Presets.flat.snapshot())

    /** Set on the main thread. The band gains are captured when it's set; edits made after that apply once it's set again. */
    var preset: Equalizer.Presets.Preset = Equalizer.Presets.flat
        set(value) {
            field = value
            settings = Settings(settings.enabled, value.snapshot())
        }

    /**
     * Passes audio through untouched whatever [enabled] says, while [com.simplecityapps.playback.BitPerfectOutput]
     * sends it to a USB DAC unchanged. Set on the main thread; applies from the next buffer.
     */
    @Volatile
    var bypassed: Boolean = false

    // Maximum allowed gain/cut for each band
    val maxBandGain = 12

    var enabled: Boolean
        get() = settings.enabled
        set(value) {
            settings = Settings(value, settings.bands)

            Timber.v("Equalizer enabled: $value")
        }

    /** The filters for [filteredBands] at the current output format. Playback thread only. */
    private var bandProcessors = emptyList<BandProcessor>()

    /** The bands [bandProcessors] were built from, or null when they need building. Playback thread only. */
    private var filteredBands: List<EqualizerBand>? = null

    /** The format [bandProcessors] were built for. Playback thread only. */
    private var filteredFormat = AudioProcessor.AudioFormat.NOT_SET

    /** Whether the sink queued an end of stream since the last flush. Playback thread only. */
    private var endedSinceFlush = false

    /**
     * Linear pre-attenuation applied to the filtered signal, equal to the inverse of the peak
     * magnitude of the whole band cascade's frequency response (or 1 when that peak doesn't exceed
     * unity).
     *
     * The ten peaking filters overlap, so their gains compound: with every band at +12 dB the
     * cascade peaks near +17.5 dB around 8 kHz, not +12 dB. Without this the equalizer leaves the
     * signal well past full scale and the hard clamp below flattens the waveform tops. Dividing by
     * the measured peak means no steady tone can come out louder than it went in, so the equalizer,
     * last in the chain after ReplayGain and the crossfade, keeps the headroom they left.
     *
     * Recomputed only when the preset or the audio format changes - never per buffer. Playback
     * thread only.
     */
    internal var attenuation: Float = 1f
        private set

    /** The output sample rate the processor is currently configured for, or null before the first [onConfigure]/[onFlush]. */
    val outputSampleRateHz: Int?
        get() = outputAudioFormat.sampleRate.takeIf { it > 0 }

    private fun updateBandProcessors(bands: List<EqualizerBand>) {
        if (outputAudioFormat.channelCount <= 0) {
            return
        }

        filteredBands = bands
        filteredFormat = outputAudioFormat
        bandProcessors =
            bands.map { band ->
                BandProcessor(
                    band.toNyquistBand(),
                    sampleRate = outputAudioFormat.sampleRate,
                    channelCount = outputAudioFormat.channelCount,
                    referenceGain = 0.0
                )
            }

        attenuation = calculateAttenuation(outputAudioFormat.sampleRate)
    }

    /** Delegates to [cascadeAttenuation], shared with the frequency-response chart. */
    private fun calculateAttenuation(sampleRate: Int): Float = cascadeAttenuation(bandProcessors, sampleRate)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        super.onConfigure(inputAudioFormat)

        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_24BIT) {
            throw UnhandledAudioFormatException(inputAudioFormat)
        }

        return inputAudioFormat
    }

    override fun onQueueEndOfStream() {
        endedSinceFlush = true
    }

    /**
     * The output format takes effect here. The sink queues an end of stream before the flush when the audio carries
     * straight on: a gapless join to the next song, or a drain to apply a new speed. A seek or skip flushes without
     * one (and again on its first buffer), so the audio after it is unrelated to what the filters last saw. So the
     * filters keep their history across a flush only when the audio carries on in the format they were built for;
     * otherwise they're rebuilt, which clears it. Restarting them at a join rings audibly (#365).
     */
    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        super.onFlush(streamMetadata)

        if (!endedSinceFlush || outputAudioFormat != filteredFormat) {
            updateBandProcessors(settings.bands)
        }
        endedSinceFlush = false
    }

    override fun onReset() {
        super.onReset()
        filteredBands = null
        filteredFormat = AudioProcessor.AudioFormat.NOT_SET
        endedSinceFlush = false
        bandProcessors = emptyList()
        attenuation = 1f
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val settings = settings
        if (settings.enabled && !bypassed) {
            if (settings.bands !== filteredBands) {
                updateBandProcessors(settings.bands)
            }
            val bandProcessors = bandProcessors
            val size = inputBuffer.remaining()
            val buffer = replaceOutputBuffer(size)
            val preAttenuation = attenuation

            when (outputAudioFormat.encoding) {
                C.ENCODING_PCM_16BIT -> {
                    while (inputBuffer.hasRemaining()) {
                        for (channelIndex in 0 until outputAudioFormat.channelCount) {
                            val sample = inputBuffer.short
                            var targetSample = sample.toFloat()
                            for (band in bandProcessors) {
                                targetSample = band.processSample(targetSample, channelIndex)
                            }
                            targetSample *= preAttenuation
                            buffer.putShort(clamp(targetSample, Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort())
                            if (!inputBuffer.hasRemaining()) {
                                break
                            }
                        }
                    }
                }

                C.ENCODING_PCM_24BIT -> {
                    while (inputBuffer.hasRemaining()) {
                        for (channelIndex in 0 until outputAudioFormat.channelCount) {
                            val sample = inputBuffer.getInt24()
                            var targetSample = sample.toFloat()
                            for (band in bandProcessors) {
                                targetSample = band.processSample(targetSample, channelIndex)
                            }
                            targetSample *= preAttenuation
                            buffer.putInt24(clamp(targetSample, ByteUtils.Int24_MIN_VALUE.toFloat(), ByteUtils.Int24_MAX_VALUE.toFloat()).toInt())
                            if (!inputBuffer.hasRemaining()) {
                                break
                            }
                        }
                    }
                }

                else -> {
                    // No op
                }
            }
            inputBuffer.position(inputBuffer.limit())
            buffer.flip()
        } else {
            val remaining = inputBuffer.remaining()
            if (remaining == 0) {
                return
            }
            replaceOutputBuffer(remaining).put(inputBuffer).flip()
        }
    }
}

private fun Equalizer.Presets.Preset.snapshot(): List<EqualizerBand> = bands.map { band -> EqualizerBand(band.centerFrequency, band.gain) }
