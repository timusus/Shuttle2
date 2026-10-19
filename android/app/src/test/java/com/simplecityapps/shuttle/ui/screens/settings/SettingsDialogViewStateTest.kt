package com.simplecityapps.shuttle.ui.screens.settings

import com.simplecityapps.shuttle.settings.AppearanceSettings
import com.simplecityapps.shuttle.settings.StreamingSettings
import com.simplecityapps.shuttle.ui.screens.settings.SettingsDestinationViewState.Row
import com.simplecityapps.shuttle.ui.screens.settings.model.AndroidSettingsCatalog
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingItem
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsAction
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsDestination
import com.simplecityapps.shuttle.ui.text.SettingsString
import io.kotest.matchers.shouldBe
import java.util.Locale
import java.util.TimeZone
import kotlin.time.Instant
import org.junit.Test

class SettingsDialogViewStateTest {
    private fun choice(destination: SettingsDestination, key: String) = AndroidSettingsCatalog.screen(destination).items
        .filterIsInstance<SettingItem.Choice<*>>().first { it.key == key }

    private fun action(action: SettingsAction) = AndroidSettingsCatalog.screen(SettingsDestination.Library).items
        .plus(AndroidSettingsCatalog.screen(SettingsDestination.About).items)
        .filterIsInstance<SettingItem.Action>().first { it.action == action }

    @Test
    fun `the theme dialog lists the themes with the one in use selected`() {
        val dialog = choiceDialogViewState(
            choice(SettingsDestination.Appearance, AppearanceSettings.Theme.key),
            SettingsScenarios.darkPureBlack
        )

        dialog.options shouldBe listOf(SettingsString.THEME_ENTRY_DAY_NIGHT, SettingsString.THEME_ENTRY_LIGHT, SettingsString.THEME_ENTRY_DARK)
        dialog.selectedIndex shouldBe 2
    }

    @Test
    fun `picking the second theme option is Light`() {
        choiceDialogViewState(choice(SettingsDestination.Appearance, AppearanceSettings.Theme.key), SettingsUiState())
            .options[1] shouldBe SettingsString.THEME_ENTRY_LIGHT
    }

    @Test
    fun `the accent dialog offers Orange third`() {
        choiceDialogViewState(choice(SettingsDestination.Appearance, AppearanceSettings.AccentColour.key), SettingsUiState())
            .options[2] shouldBe SettingsString.THEME_ACCENT_ENTRY_ORANGE
    }

    @Test
    fun `the mobile data dialog offers 128 kbps last and selects the cap in use`() {
        val dialog = choiceDialogViewState(
            choice(SettingsDestination.Sources, StreamingSettings.MeteredQuality.key),
            SettingsScenarios.streamingCappedOnMobileData
        )

        dialog.options[3] shouldBe SettingsString.PREF_STREAMING_QUALITY_128
        dialog.selectedIndex shouldBe 3
    }

    @Test
    fun `the format dialog offers MP3 last`() {
        choiceDialogViewState(choice(SettingsDestination.Sources, StreamingSettings.Format.key), SettingsScenarios.streamingCappedOnMobileData)
            .options[3] shouldBe SettingsString.PREF_TRANSCODE_FORMAT_MP3
    }

    @Test
    fun `the download quality dialog selects 192 kbps`() {
        val dialog = choiceDialogViewState(
            choice(SettingsDestination.Sources, StreamingSettings.DownloadQuality.key),
            SettingsScenarios.streamingCappedOnMobileData
        )

        dialog.options[dialog.selectedIndex] shouldBe SettingsString.PREF_STREAMING_QUALITY_192
    }

    @Test
    fun `a dialog row index matches the row the destination shows`() {
        val row = destinationRow(SettingsScenarios.streamingCappedOnMobileData, StreamingSettings.MeteredQuality.key)

        row.selectedIndex shouldBe 3
    }

    @Test
    fun `clearing the artwork cache is a destructive confirmation`() {
        confirmActionViewState(action(SettingsAction.ClearArtworkCache)) shouldBe ConfirmActionViewState(
            SettingsString.SETTINGS_DIALOG_TITLE_CLEAR_ARTWORK,
            SettingsString.SETTINGS_DIALOG_MESSAGE_CLEAR_ARTWORK,
            SettingsString.SETTINGS_DIALOG_BUTTON_CLEAR_ARTWORK,
            destructive = true
        )
    }

    @Test
    fun `restoring a backup and downloading artwork confirm without being destructive`() {
        confirmActionViewState(action(SettingsAction.ImportBackup)) shouldBe ConfirmActionViewState(
            SettingsString.SETTINGS_DIALOG_TITLE_RESTORE_BACKUP,
            SettingsString.SETTINGS_DIALOG_MESSAGE_RESTORE_BACKUP,
            SettingsString.SETTINGS_DIALOG_BUTTON_RESTORE_BACKUP,
            destructive = false
        )
        confirmActionViewState(action(SettingsAction.DownloadAllArtwork)) shouldBe ConfirmActionViewState(
            SettingsString.SETTINGS_DIALOG_TITLE_DOWNLOAD_ARTWORK,
            SettingsString.SETTINGS_DIALOG_MESSAGE_DOWNLOAD_ARTWORK,
            SettingsString.SETTINGS_DIALOG_BUTTON_DOWNLOAD_ARTWORK,
            destructive = false
        )
    }

    @Test
    fun `decibels carry a sign and one decimal`() {
        formatDecibels(1.5f, Locale.US) shouldBe "+1.5 dB"
        formatDecibels(-3f, Locale.US) shouldBe "-3.0 dB"
    }

    @Test
    fun `decibels use the locale's decimal separator`() {
        formatDecibels(1.5f, Locale.GERMANY) shouldBe "+1,5 dB"
    }

    @Test
    fun `percent is the number and a sign`() {
        formatPercent(80) shouldBe "80%"
    }

    @Test
    fun `the last scan reads as a short date in the given time zone`() {
        formatLastScan(Instant.fromEpochMilliseconds(0), Locale.US, TimeZone.getTimeZone("UTC")).startsWith("1/1/70") shouldBe true
    }

    private fun destinationRow(uiState: SettingsUiState, key: String) = settingsDestinationViewState(
        AndroidSettingsCatalog.screen(SettingsDestination.Sources),
        uiState,
        sdkInt = 34,
        versionName = null
    ).groups.flatMap { it.rows }.first { it.item.key == key } as Row.Choice
}
