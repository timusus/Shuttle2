package com.simplecityapps.shuttle.ui.screens.settings

import android.content.res.Resources
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.text.SettingsString
import com.simplecityapps.shuttle.ui.text.UiText
import com.simplecityapps.shuttle.ui.text.getString
import com.simplecityapps.shuttle.ui.text.resId
import java.util.Locale
import java.util.TimeZone

// The copy each view-state value stands for. Taking Resources, locale and zone keeps these testable without a composition.

fun proSummaryText(summary: SettingsRootViewState.ProSummary, resources: Resources): String? = when (summary) {
    SettingsRootViewState.ProSummary.None -> null
    SettingsRootViewState.ProSummary.Owned -> resources.getString(R.string.paywall_settings_summary_pro)
    SettingsRootViewState.ProSummary.Upsell -> resources.getString(R.string.paywall_settings_summary)
}

fun rootSummaryText(summary: SettingsRootViewState.Summary, resources: Resources): String? = when (summary) {
    SettingsRootViewState.Summary.None -> null
    is SettingsRootViewState.Summary.Option -> resources.getString(summary.label.resId)
    is SettingsRootViewState.Summary.Streaming -> resources.getString(R.string.settings_sources_summary_streaming, resources.getString(summary.quality.resId))
    is SettingsRootViewState.Summary.Rescan -> resources.getString(R.string.settings_library_summary_rescan, resources.getString(summary.frequency.resId))
    is SettingsRootViewState.Summary.EqualizerPreset -> resources.getString(R.string.settings_playback_summary_equalizer_preset, resources.getString(summary.preset.resId))
    SettingsRootViewState.Summary.EqualizerOff -> resources.getString(R.string.settings_playback_summary_equalizer_off)
    SettingsRootViewState.Summary.Privacy -> resources.getString(R.string.settings_privacy_summary)
}

fun choiceValueText(
    row: SettingsDestinationViewState.Row.Choice,
    resources: Resources,
    locale: Locale,
    timeZone: TimeZone
): String {
    val label = row.label?.let { resources.getString(it.resId) }.orEmpty()
    val lastScan = row.lastScan ?: return label
    return "$label ${resources.getString(R.string.pref_last_scan_date, formatLastScan(lastScan, locale, timeZone))}"
}

fun sliderValueText(
    text: SettingsDestinationViewState.SliderText,
    resources: Resources,
    locale: Locale
): String? = when (text) {
    SettingsDestinationViewState.SliderText.None -> null
    is SettingsDestinationViewState.SliderText.Decibels -> formatDecibels(text.value, locale)
    SettingsDestinationViewState.SliderText.Off -> resources.getString(SettingsString.DSP_REPLAY_GAIN_OFF.resId)
    is SettingsDestinationViewState.SliderText.Seconds -> resources.getString(UiText.Resource(SettingsString.SETTINGS_CROSSFADE_SECONDS, listOf(text.seconds)))
    is SettingsDestinationViewState.SliderText.Percent -> formatPercent(text.percent)
}
