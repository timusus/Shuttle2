package com.simplecityapps.shuttle.ui.screens.settings

import com.simplecityapps.shuttle.settings.AppearanceSettings
import com.simplecityapps.shuttle.settings.EqualizerSettings
import com.simplecityapps.shuttle.settings.StreamingSettings
import com.simplecityapps.shuttle.ui.screens.settings.model.AndroidSettingsCatalog
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingItem
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsDestination
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsLink
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsScreen
import com.simplecityapps.shuttle.ui.text.StringKey
import kotlin.math.roundToInt
import kotlin.time.Instant

/** What the Settings root shows: one row per destination, each with the summary of the value it holds. */
data class SettingsRootViewState(val pro: ProSummary, val rows: List<Row>) {
    data class Row(val destination: SettingsDestination, val summary: Summary)

    /** The copy under the Shuttle Music Pro row: none while Play hasn't answered, thanks once owned, else the pitch. */
    enum class ProSummary { None, Owned, Upsell }

    sealed interface Summary {
        data object None : Summary

        /** The label of the option in use, shown as is. */
        data class Option(val label: StringKey) : Summary

        data class Streaming(val quality: StringKey) : Summary

        data class Rescan(val frequency: StringKey) : Summary

        data class EqualizerPreset(val preset: StringKey) : Summary

        data object EqualizerOff : Summary

        data object Privacy : Summary
    }
}

fun settingsRootViewState(uiState: SettingsUiState, pro: SettingsProState): SettingsRootViewState = SettingsRootViewState(
    pro = when (pro) {
        SettingsProState.Neutral -> SettingsRootViewState.ProSummary.None
        SettingsProState.Owned -> SettingsRootViewState.ProSummary.Owned
        SettingsProState.Upsell -> SettingsRootViewState.ProSummary.Upsell
    },
    rows = SettingsDestination.entries.map { destination ->
        SettingsRootViewState.Row(
            destination,
            when (destination) {
                SettingsDestination.Appearance -> optionLabel(destination, uiState) { it.setting.key == AppearanceSettings.Theme.key }
                    ?.let { SettingsRootViewState.Summary.Option(it) }

                SettingsDestination.Sources -> optionLabel(destination, uiState) { it.setting.key == StreamingSettings.MeteredQuality.key }
                    ?.let { SettingsRootViewState.Summary.Streaming(it) }

                SettingsDestination.Library -> optionLabel(destination, uiState) { it.showsLastScan }
                    ?.let { SettingsRootViewState.Summary.Rescan(it) }

                SettingsDestination.PlaybackAndSound -> if (uiState.value(EqualizerSettings.Enabled)) {
                    SettingsRootViewState.Summary.EqualizerPreset(uiState.equalizerSummary)
                } else {
                    SettingsRootViewState.Summary.EqualizerOff
                }

                SettingsDestination.Privacy -> SettingsRootViewState.Summary.Privacy

                else -> null
            } ?: SettingsRootViewState.Summary.None
        )
    }
)

/** The label of the option the [destination]'s choice picked by [which] is on. */
private fun optionLabel(
    destination: SettingsDestination,
    uiState: SettingsUiState,
    which: (SettingItem.Choice<*>) -> Boolean
): StringKey? {
    val choice = AndroidSettingsCatalog.screen(destination).items
        .filterIsInstance<SettingItem.Choice<*>>()
        .firstOrNull(which) ?: return null
    return choice.currentLabel(uiState)
}

private fun <T> SettingItem.Choice<T>.currentLabel(uiState: SettingsUiState): StringKey? {
    val value = uiState.value(setting)
    return options.firstOrNull { it.value == value }?.label
}

/** What a destination page shows: its groups of rows that apply on this device, and the About version row. */
data class SettingsDestinationViewState(
    val groups: List<Group>,
    val version: String?
) {
    data class Group(val title: StringKey?, val rows: List<Row>) {
        /** Stable across recompositions: the header, or the first row where there is none. */
        val key: StringKey get() = title ?: rows.first().item.title
    }

    sealed interface Row {
        val item: SettingItem
        val enabled: Boolean

        data class Switch(override val item: SettingItem.Switch, val checked: Boolean, override val enabled: Boolean) : Row

        /** [override] replaces the value with a hint and disables the row; [lastScan] is appended to the label when set. */
        data class Choice(
            override val item: SettingItem.Choice<*>,
            val label: StringKey?,
            val override: StringKey?,
            val lastScan: Instant?,
            override val enabled: Boolean,
            val selectedIndex: Int
        ) : Row

        data class Slider(
            override val item: SettingItem.Slider<*>,
            val value: Float,
            val valueLabel: SliderText,
            override val enabled: Boolean
        ) : Row

        /** [summary] is the equalizer's state on its own row, else the catalog's. */
        data class Navigate(override val item: SettingItem.Navigate, val summary: StringKey?, override val enabled: Boolean) : Row

        data class Action(override val item: SettingItem.Action, override val enabled: Boolean) : Row
    }

    sealed interface SliderText {
        data object None : SliderText

        data class Decibels(val value: Float) : SliderText

        data object Off : SliderText

        data class Seconds(val seconds: Int) : SliderText

        data class Percent(val percent: Int) : SliderText
    }
}

/**
 * Rows below [sdkInt]'s level are left out; a row whose `dependsOn` switch is off is disabled, as is a choice whose
 * `overriddenBy` switch is shown and on. About gets a version row when [versionName] is set.
 */
fun settingsDestinationViewState(
    screen: SettingsScreen,
    uiState: SettingsUiState,
    sdkInt: Int,
    versionName: String?
): SettingsDestinationViewState {
    val shown = screen.groups
        .map { group -> group to group.items.filter { it.minSdk <= sdkInt } }
        .filter { (_, items) -> items.isNotEmpty() }
    val shownKeys = shown.flatMap { (_, items) -> items.mapNotNull { it.key } }.toSet()
    return SettingsDestinationViewState(
        groups = shown.map { (group, items) ->
            SettingsDestinationViewState.Group(group.title, items.map { row(it, uiState, shownKeys) })
        },
        version = versionName?.takeIf { screen.destination == SettingsDestination.About }
    )
}

private fun row(
    item: SettingItem,
    uiState: SettingsUiState,
    shownKeys: Set<String>
): SettingsDestinationViewState.Row {
    val enabled = item.dependsOn?.let { uiState.value(it) } ?: true
    return when (item) {
        is SettingItem.Switch -> SettingsDestinationViewState.Row.Switch(item, uiState.value(item.setting), enabled)

        is SettingItem.Choice<*> -> choiceRow(item, uiState, shownKeys, enabled)

        is SettingItem.Slider<*> -> {
            // A restored preference can sit outside the range; the row shows the clamped position.
            val value = uiState.value(item.setting).toFloat().coerceIn(item.range.start, item.range.endInclusive)
            SettingsDestinationViewState.Row.Slider(item, value, sliderText(item.label, value), enabled)
        }

        is SettingItem.Navigate -> SettingsDestinationViewState.Row.Navigate(
            item,
            if (item.target == SettingsLink.Equalizer) uiState.equalizerSummary else item.summary,
            enabled
        )

        is SettingItem.Action -> SettingsDestinationViewState.Row.Action(item, enabled)
    }
}

private fun <T> choiceRow(
    item: SettingItem.Choice<T>,
    uiState: SettingsUiState,
    shownKeys: Set<String>,
    enabled: Boolean
): SettingsDestinationViewState.Row.Choice {
    val override = item.overriddenBy?.takeIf { it.setting.key in shownKeys && uiState.value(it.setting) }
    val current = uiState.value(item.setting)
    return SettingsDestinationViewState.Row.Choice(
        item = item,
        label = item.currentLabel(uiState),
        override = override?.hint ?: item.disabledHint.takeIf { !enabled },
        lastScan = uiState.lastScanDate.takeIf { item.showsLastScan },
        enabled = enabled && override == null,
        selectedIndex = item.options.indexOfFirst { it.value == current }
    )
}

private fun sliderText(
    label: SettingItem.SliderLabel,
    value: Float
): SettingsDestinationViewState.SliderText = when (label) {
    SettingItem.SliderLabel.None -> SettingsDestinationViewState.SliderText.None

    SettingItem.SliderLabel.DecibelsSigned -> SettingsDestinationViewState.SliderText.Decibels(value)

    SettingItem.SliderLabel.CrossfadeSeconds -> if (value < 500f) {
        SettingsDestinationViewState.SliderText.Off
    } else {
        SettingsDestinationViewState.SliderText.Seconds((value / 1000f).roundToInt())
    }

    SettingItem.SliderLabel.Percent -> SettingsDestinationViewState.SliderText.Percent(value.roundToInt())
}
