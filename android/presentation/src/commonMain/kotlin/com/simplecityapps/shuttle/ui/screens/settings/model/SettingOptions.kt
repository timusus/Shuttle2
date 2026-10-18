package com.simplecityapps.shuttle.ui.screens.settings.model

import com.simplecityapps.shuttle.settings.StreamingQuality
import com.simplecityapps.shuttle.settings.TranscodeFormat
import com.simplecityapps.shuttle.ui.text.SettingsString

/**
 * The streaming quality choices, best first, as both platforms' catalogs offer them for metered and unmetered networks
 * and for downloads.
 */
val StreamingQualityOptions: List<ChoiceOption<StreamingQuality>> = listOf(
    ChoiceOption(StreamingQuality.Original, SettingsString.PREF_STREAMING_QUALITY_ORIGINAL),
    ChoiceOption(StreamingQuality.Kbps320, SettingsString.PREF_STREAMING_QUALITY_320),
    ChoiceOption(StreamingQuality.Kbps192, SettingsString.PREF_STREAMING_QUALITY_192),
    ChoiceOption(StreamingQuality.Kbps128, SettingsString.PREF_STREAMING_QUALITY_128)
)

/** The transcode format choices, Automatic (each platform's own) first. */
val TranscodeFormatOptions: List<ChoiceOption<TranscodeFormat>> = listOf(
    ChoiceOption(TranscodeFormat.Auto, SettingsString.PREF_TRANSCODE_FORMAT_AUTO),
    ChoiceOption(TranscodeFormat.Opus, SettingsString.PREF_TRANSCODE_FORMAT_OPUS),
    ChoiceOption(TranscodeFormat.Aac, SettingsString.PREF_TRANSCODE_FORMAT_AAC),
    ChoiceOption(TranscodeFormat.Mp3, SettingsString.PREF_TRANSCODE_FORMAT_MP3)
)
