package com.simplecityapps.shuttle.ui.screens.settings.equalizer

import com.simplecityapps.playback.equalizer.EqualizerBandGain
import com.simplecityapps.playback.equalizer.EqualizerFrequencyResponse
import com.simplecityapps.shuttle.ui.screens.equalizer.FrequencyResponsePoint
import com.simplecityapps.shuttle.ui.screens.equalizer.MAX_FREQUENCY_HZ
import com.simplecityapps.shuttle.ui.screens.equalizer.MIN_FREQUENCY_HZ
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * Builds the equalizer's plotted frequency response for [bands] at [outputSampleRateHz], between the chart's
 * plotted range: the DSP maths lives in [EqualizerFrequencyResponse] (`:android:playback`), reached through this
 * domain port so the screen never imports it directly.
 */
class ComputeFrequencyResponse
@Inject
constructor(
    private val equalizerFrequencyResponse: EqualizerFrequencyResponse
) {
    operator fun invoke(
        bands: List<EqualizerBandState>,
        outputSampleRateHz: Int?
    ): ImmutableList<FrequencyResponsePoint> = equalizerFrequencyResponse(
        bands = bands.map { EqualizerBandGain(it.frequency, it.gainDb) },
        outputSampleRateHz = outputSampleRateHz,
        minFrequencyHz = MIN_FREQUENCY_HZ,
        maxFrequencyHz = MAX_FREQUENCY_HZ,
        pointCount = 240
    ).map { FrequencyResponsePoint(it.frequencyHz, it.gainDb) }.toImmutableList()
}
