package com.simplecityapps.shuttle.ui.screens.settings

import com.simplecityapps.shuttle.ui.screens.settings.model.Confirmation
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingItem
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsAction
import com.simplecityapps.shuttle.ui.text.SettingsString
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class SettingsDialogsTest {
    private val unconfirmed = SettingItem.Action(SettingsAction.ExportBackup, SettingsString.PREF_BACKUP_EXPORT_TITLE)

    private val confirmed = SettingItem.Action(
        action = SettingsAction.ClearArtworkCache,
        title = SettingsString.PREF_CLEAR_ARTWORK_TITLE,
        confirmation = Confirmation(
            SettingsString.SETTINGS_DIALOG_TITLE_CLEAR_ARTWORK,
            SettingsString.SETTINGS_DIALOG_MESSAGE_CLEAR_ARTWORK,
            SettingsString.SETTINGS_DIALOG_BUTTON_CLEAR_ARTWORK
        )
    )

    @Test
    fun `opening a choice shows its dialog and picks nothing`() {
        onChoiceDialogEvent(null, ChoiceDialogEvent.Open("theme")) shouldBe ChoiceDialogStep("theme")
    }

    @Test
    fun `selecting an option closes the dialog and reports the choice and index`() {
        onChoiceDialogEvent("theme", ChoiceDialogEvent.Select(1)) shouldBe ChoiceDialogStep(null, ChoicePick("theme", 1))
    }

    @Test
    fun `dismissing a choice closes it without picking`() {
        onChoiceDialogEvent("theme", ChoiceDialogEvent.Dismiss) shouldBe ChoiceDialogStep(null)
    }

    @Test
    fun `selecting with no dialog open picks nothing`() {
        onChoiceDialogEvent(null, ChoiceDialogEvent.Select(1)) shouldBe ChoiceDialogStep(null)
    }

    @Test
    fun `an action with a confirmation waits and runs nothing`() {
        onActionEvent(null, ActionEvent.Tap(confirmed)) shouldBe ActionStep(pending = SettingsAction.ClearArtworkCache)
    }

    @Test
    fun `confirming runs the pending action once`() {
        val step = onActionEvent(SettingsAction.ClearArtworkCache, ActionEvent.Confirm)

        step shouldBe ActionStep(pending = null, run = SettingsAction.ClearArtworkCache)
        onActionEvent(step.pending, ActionEvent.Confirm) shouldBe ActionStep(pending = null)
    }

    @Test
    fun `dismissing a confirmation runs nothing`() {
        onActionEvent(SettingsAction.ClearArtworkCache, ActionEvent.Dismiss) shouldBe ActionStep(pending = null)
    }

    @Test
    fun `an action without a confirmation runs straight away`() {
        onActionEvent(null, ActionEvent.Tap(unconfirmed)) shouldBe ActionStep(pending = null, run = SettingsAction.ExportBackup)
    }

    @Test
    fun `clearing artwork asks first in a destructive dialog`() {
        confirmActionViewState(confirmed) shouldBe ConfirmActionViewState(
            SettingsString.SETTINGS_DIALOG_TITLE_CLEAR_ARTWORK,
            SettingsString.SETTINGS_DIALOG_MESSAGE_CLEAR_ARTWORK,
            SettingsString.SETTINGS_DIALOG_BUTTON_CLEAR_ARTWORK,
            destructive = true
        )
    }

    @Test
    fun `an action with no confirmation has no dialog`() {
        confirmActionViewState(unconfirmed) shouldBe null
    }
}
