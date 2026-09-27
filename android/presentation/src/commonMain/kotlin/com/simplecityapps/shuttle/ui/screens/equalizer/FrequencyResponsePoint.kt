package com.simplecityapps.shuttle.ui.screens.equalizer

/** One point of an equalizer's frequency-response curve. */
data class FrequencyResponsePoint(val frequencyHz: Float, val gainDb: Float)

/** The plotted frequency range, shared with [com.simplecityapps.shuttle.ui.screens.settings.equalizer.ComputeFrequencyResponse]. */
const val MIN_FREQUENCY_HZ = 20f
const val MAX_FREQUENCY_HZ = 20_500f
