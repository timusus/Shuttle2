package com.simplecityapps.shuttle.ui.screens.settings

import com.simplecityapps.shuttle.ui.screens.settings.model.SettingItem
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsAction
import com.simplecityapps.shuttle.ui.text.StringKey

/** The option [index] of the choice stored under [key]. */
data class ChoicePick(val key: String, val index: Int)

sealed interface ChoiceDialogEvent {
    data class Open(val key: String) : ChoiceDialogEvent

    data class Select(val index: Int) : ChoiceDialogEvent

    data object Dismiss : ChoiceDialogEvent
}

/** [openKey] is the choice whose dialog is showing; [picked] is set only on the step that chose an option. */
data class ChoiceDialogStep(val openKey: String?, val picked: ChoicePick? = null)

fun onChoiceDialogEvent(openKey: String?, event: ChoiceDialogEvent): ChoiceDialogStep = when (event) {
    is ChoiceDialogEvent.Open -> ChoiceDialogStep(event.key)
    is ChoiceDialogEvent.Select -> ChoiceDialogStep(openKey = null, picked = openKey?.let { ChoicePick(it, event.index) })
    ChoiceDialogEvent.Dismiss -> ChoiceDialogStep(openKey = null)
}

sealed interface ActionEvent {
    data class Tap(val item: SettingItem.Action) : ActionEvent

    data object Confirm : ActionEvent

    data object Dismiss : ActionEvent
}

/** [pending] is the action awaiting confirmation; [run] is set only on the step that should run an action. */
data class ActionStep(val pending: SettingsAction?, val run: SettingsAction? = null)

fun onActionEvent(pending: SettingsAction?, event: ActionEvent): ActionStep = when (event) {
    is ActionEvent.Tap ->
        if (event.item.confirmation != null) ActionStep(pending = event.item.action) else ActionStep(pending = null, run = event.item.action)

    ActionEvent.Confirm -> ActionStep(pending = null, run = pending)

    ActionEvent.Dismiss -> ActionStep(pending = null)
}

data class ChoiceDialogViewState(val title: StringKey, val options: List<StringKey>, val selectedIndex: Int)

fun choiceDialogViewState(choice: SettingItem.Choice<*>, uiState: SettingsUiState): ChoiceDialogViewState {
    val current = uiState.value(choice.setting)
    return ChoiceDialogViewState(choice.title, choice.options.map { it.label }, choice.options.indexOfFirst { it.value == current })
}

data class ConfirmActionViewState(val title: StringKey, val message: StringKey, val confirm: StringKey, val destructive: Boolean)

/** The dialog guarding [item], or null for an action that runs straight away. */
fun confirmActionViewState(item: SettingItem.Action): ConfirmActionViewState? = item.confirmation?.let {
    ConfirmActionViewState(it.title, it.message, it.confirm, destructive = item.action == SettingsAction.ClearArtworkCache)
}
