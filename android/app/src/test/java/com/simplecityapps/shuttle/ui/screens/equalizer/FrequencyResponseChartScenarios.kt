package com.simplecityapps.shuttle.ui.screens.equalizer

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

fun sampleFrequencyResponse(): ImmutableList<FrequencyResponsePoint> = listOf(
    FrequencyResponsePoint(frequencyHz = 20f, gainDb = 0f),
    FrequencyResponsePoint(frequencyHz = 1_000f, gainDb = 6f),
    FrequencyResponsePoint(frequencyHz = 20_000f, gainDb = 0f),
).toImmutableList()

fun emptyFrequencyResponse(): ImmutableList<FrequencyResponsePoint> = emptyList<FrequencyResponsePoint>().toImmutableList()
