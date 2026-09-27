package com.simplecityapps.shuttle.ui.screens.settings.model

import com.simplecityapps.shuttle.settings.StreamingQuality
import com.simplecityapps.shuttle.ui.text.StringKey

/** The streaming quality choices, best first, as both platforms' catalogs offer them for metered and unmetered networks. */
val StreamingQualityOptions: List<ChoiceOption<StreamingQuality>> = listOf(
    ChoiceOption(StreamingQuality.Original, StringKey.PREF_STREAMING_QUALITY_ORIGINAL),
    ChoiceOption(StreamingQuality.Kbps320, StringKey.PREF_STREAMING_QUALITY_320),
    ChoiceOption(StreamingQuality.Kbps192, StringKey.PREF_STREAMING_QUALITY_192),
    ChoiceOption(StreamingQuality.Kbps128, StringKey.PREF_STREAMING_QUALITY_128)
)
