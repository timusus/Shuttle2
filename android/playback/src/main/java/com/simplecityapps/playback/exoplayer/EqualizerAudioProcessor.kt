package com.simplecityapps.playback.exoplayer

import androidx.core.math.MathUtils.clamp
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import com.simplecityapps.playback.dsp.equalizer.BandProcessor
import com.simplecityapps.playback.dsp.equalizer.Equalizer
import com.simplecityapps.playback.dsp.equalizer.EqualizerBand
import com.simplecityapps.playback.dsp.equalizer.toNyquistBand
import com.simplecityapps.playback.exoplayer.ByteUtils.getInt24
import com.simplecityapps.playback.exoplayer.ByteUtils.putInt24
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.pow
import timber.log.Timber

/** Lowest frequency considered when measuring the cascade's peak gain. Below this is inaudible. */
private const val ANALYSIS_MIN_FREQUENCY = 20.0

/** Number of log-spaced points between [ANALYSIS_MIN_FREQUENCY] and Nyquist used to find the peak. */
private const val ANALYSIS_POINT_COUNT = 512

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

    /**
     * Linear pre-attenuation applied to the filtered signal, equal to the inverse of the peak
     * magnitude of the whole band cascade's frequency response (or 1 when that peak doesn't exceed
     * unity).
     *
     * The ten peaking filters overlap, so their gains compound: with every band at +12 dB the
     * cascade peaks near +17.5 dB around 8 kHz, not +12 dB. Without this the equalizer leaves the
     * signal well past full scale and the hard clamp below flattens the waveform tops. Dividing by
     * the measured peak means no steady tone can come out louder than it went in, so the
     * ReplayGain stage that follows keeps the headroom it started with.
     *
     * Recomputed only when the preset or the audio format changes - never per buffer. Playback
     * thread only.
     */
    internal var attenuation: Float = 1f
        private set

    private fun updateBandProcessors(bands: List<EqualizerBand>) {
        if (outputAudioFormat.channelCount <= 0) {
            return
        }

        filteredBands = bands
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

    /**
     * Sweeps a log-spaced frequency grid, multiplying every band's magnitude response together to
     * find the cascade's peak gain, and returns its inverse when it exceeds unity. A flat or
     * cut-only preset can never exceed unity, so it returns 1 and the signal stays untouched.
     */
    private fun calculateAttenuation(sampleRate: Int): Float {
        val nyquist = sampleRate / 2.0
        if (bandProcessors.isEmpty() || nyquist <= ANALYSIS_MIN_FREQUENCY) {
            return 1f
        }

        var peak = 0.0
        val span = nyquist / ANALYSIS_MIN_FREQUENCY
        for (index in 0 until ANALYSIS_POINT_COUNT) {
            val frequency = ANALYSIS_MIN_FREQUENCY * span.pow(index.toDouble() / (ANALYSIS_POINT_COUNT - 1))
            val omega = 2.0 * PI * frequency / sampleRate
            var magnitude = 1.0
            for (bandProcessor in bandProcessors) {
                magnitude *= bandProcessor.magnitudeAt(omega)
            }
            if (magnitude > peak) {
                peak = magnitude
            }
        }

        return if (peak.isFinite() && peak > 1.0) (1.0 / peak).toFloat() else 1f
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        super.onConfigure(inputAudioFormat)

        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_24BIT) {
            throw UnhandledAudioFormatException(inputAudioFormat)
        }

        return inputAudioFormat
    }

    /** The output format takes effect here, so the filters are rebuilt for it (which also clears their history). */
    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        super.onFlush(streamMetadata)

        Timber.v("onFlush() called")
        updateBandProcessors(settings.bands)
    }

    override fun onReset() {
        super.onReset()
        filteredBands = null
        bandProcessors = emptyList()
        attenuation = 1f
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val settings = settings
        if (settings.enabled) {
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
