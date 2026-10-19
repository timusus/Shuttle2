package com.simplecityapps.shuttle.ui.screens.settings

import com.simplecityapps.shuttle.ui.screens.settings.model.SettingItem
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsAction
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsDestination
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsLink

/** What the Settings root and its pages ask of their host. */
sealed interface SettingsEvent {
    data object NavigateUp : SettingsEvent

    data object OpenPro : SettingsEvent

    data class OpenDestination(val destination: SettingsDestination) : SettingsEvent

    data class ChangeSwitch(val item: SettingItem.Switch, val checked: Boolean) : SettingsEvent

    data class SelectChoice(val item: SettingItem.Choice<*>, val index: Int) : SettingsEvent

    data class ChangeSlider(val item: SettingItem.Slider<*>, val value: Float) : SettingsEvent

    data class RunAction(val action: SettingsAction) : SettingsEvent

    data class OpenLink(val link: SettingsLink) : SettingsEvent
}

/** The host's handlers; a page passes no-ops for the events it can't send. */
class SettingsCallbacks(
    val onNavigateUp: () -> Unit = {},
    val onOpenPro: () -> Unit = {},
    val onOpenDestination: (SettingsDestination) -> Unit = {},
    val onSwitchChange: (SettingItem.Switch, Boolean) -> Unit = { _, _ -> },
    val onChoiceSelect: (SettingItem.Choice<*>, Int) -> Unit = { _, _ -> },
    val onSliderChange: (SettingItem.Slider<*>, Float) -> Unit = { _, _ -> },
    val onAction: (SettingsAction) -> Unit = {},
    val onOpenLink: (SettingsLink) -> Unit = {}
) {
    fun onEvent(event: SettingsEvent) = when (event) {
        SettingsEvent.NavigateUp -> onNavigateUp()
        SettingsEvent.OpenPro -> onOpenPro()
        is SettingsEvent.OpenDestination -> onOpenDestination(event.destination)
        is SettingsEvent.ChangeSwitch -> onSwitchChange(event.item, event.checked)
        is SettingsEvent.SelectChoice -> onChoiceSelect(event.item, event.index)
        is SettingsEvent.ChangeSlider -> onSliderChange(event.item, event.value)
        is SettingsEvent.RunAction -> onAction(event.action)
        is SettingsEvent.OpenLink -> onOpenLink(event.link)
    }
}

fun SettingsRootViewState.Row.tapEvent(): SettingsEvent = SettingsEvent.OpenDestination(destination)

fun SettingsDestinationViewState.Row.Navigate.tapEvent(): SettingsEvent = SettingsEvent.OpenLink(item.target)

/** The event an action step runs, if any; an action needing confirmation runs on the Confirm step instead. */
fun ActionStep.event(): SettingsEvent? = run?.let(SettingsEvent::RunAction)

fun ChoiceDialogStep.event(choice: SettingItem.Choice<*>): SettingsEvent? = picked?.let { SettingsEvent.SelectChoice(choice, it.index) }
