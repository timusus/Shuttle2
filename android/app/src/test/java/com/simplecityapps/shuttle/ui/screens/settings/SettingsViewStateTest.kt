package com.simplecityapps.shuttle.ui.screens.settings

import com.simplecityapps.playback.settings.PlaybackSettings
import com.simplecityapps.shuttle.settings.AppearanceSettings
import com.simplecityapps.shuttle.settings.DebugSettings
import com.simplecityapps.shuttle.ui.screens.settings.SettingsDestinationViewState.Row
import com.simplecityapps.shuttle.ui.screens.settings.SettingsDestinationViewState.SliderText
import com.simplecityapps.shuttle.ui.screens.settings.model.AndroidSettingsCatalog
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsAction
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsDestination
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsLink
import com.simplecityapps.shuttle.ui.text.SettingsString
import io.kotest.matchers.shouldBe
import kotlin.time.Instant
import org.junit.Test

class SettingsViewStateTest {
    private fun destination(
        destination: SettingsDestination,
        uiState: SettingsUiState = SettingsUiState(),
        sdkInt: Int = 34,
        versionName: String? = null
    ) = settingsDestinationViewState(AndroidSettingsCatalog.screen(destination), uiState, sdkInt, versionName)

    private fun SettingsDestinationViewState.rows() = groups.flatMap { it.rows }

    private fun SettingsDestinationViewState.row(key: String) = rows().first { it.item.key == key }

    private fun summaryOf(
        destination: SettingsDestination,
        uiState: SettingsUiState = SettingsUiState()
    ) = settingsRootViewState(uiState, SettingsProState.Upsell).rows.first { it.destination == destination }.summary

    private fun navigateTargets(destination: SettingsDestination) = destination(destination).rows()
        .filterIsInstance<Row.Navigate>().associate { it.item.title to it.item.target }

    private fun action(destination: SettingsDestination, title: SettingsString) = destination(destination).rows()
        .filterIsInstance<Row.Action>().first { it.item.title == title }.item

    @Test
    fun `the root has a row per destination`() {
        settingsRootViewState(SettingsUiState(), SettingsProState.Upsell).rows.map { it.destination } shouldBe SettingsDestination.entries
    }

    @Test
    fun `the Pro row pitches Pro until it is owned and says nothing before Play answers`() {
        settingsRootViewState(SettingsUiState(), SettingsProState.Upsell).pro shouldBe SettingsRootViewState.ProSummary.Upsell
        settingsRootViewState(SettingsUiState(), SettingsProState.Owned).pro shouldBe SettingsRootViewState.ProSummary.Owned
        settingsRootViewState(SettingsUiState(), SettingsProState.Neutral).pro shouldBe SettingsRootViewState.ProSummary.None
    }

    @Test
    fun `About links to the changelog and the licences`() {
        navigateTargets(SettingsDestination.About)[SettingsString.PREF_VIEW_CHANGELOG_TITLE] shouldBe SettingsLink.WhatsNew
        navigateTargets(SettingsDestination.About)[SettingsString.PREF_VIEW_LICENSES_TITLE] shouldBe SettingsLink.Licences
    }

    @Test
    fun `Playback links to the equalizer`() {
        navigateTargets(SettingsDestination.PlaybackAndSound)[SettingsString.DSP_EQUALIZER_TITLE] shouldBe SettingsLink.Equalizer
    }

    @Test
    fun `backing up the library needs no confirmation`() {
        val backup = action(SettingsDestination.Library, SettingsString.PREF_BACKUP_EXPORT_TITLE)

        backup.action shouldBe SettingsAction.ExportBackup
        confirmActionViewState(backup) shouldBe null
    }

    @Test
    fun `USB DAC direct output is a switch on its setting`() {
        (destination(SettingsDestination.PlaybackAndSound, sdkInt = 34).row(PlaybackSettings.UsbDacDirectOutput.key) as Row.Switch).item.setting shouldBe
            PlaybackSettings.UsbDacDirectOutput
    }

    @Test
    fun `the root summarises Appearance by the theme in use`() {
        summaryOf(SettingsDestination.Appearance, SettingsScenarios.darkPureBlack) shouldBe
            SettingsRootViewState.Summary.Option(SettingsString.THEME_ENTRY_DARK)
    }

    @Test
    fun `the root shows the equalizer off by default and its preset when on`() {
        summaryOf(SettingsDestination.PlaybackAndSound) shouldBe SettingsRootViewState.Summary.EqualizerOff
        summaryOf(SettingsDestination.PlaybackAndSound, SettingsScenarios.equalizerOn) shouldBe
            SettingsRootViewState.Summary.EqualizerPreset(SettingsScenarios.equalizerOn.equalizerSummary)
    }

    @Test
    fun `the root sums Sources up as the mobile data cap defaulting to 320 kbps`() {
        summaryOf(SettingsDestination.Sources) shouldBe SettingsRootViewState.Summary.Streaming(SettingsString.PREF_STREAMING_QUALITY_320)
    }

    @Test
    fun `the root keeps an explicitly stored Original mobile data cap`() {
        summaryOf(SettingsDestination.Sources, SettingsScenarios.streamingOriginalOnMobileData) shouldBe
            SettingsRootViewState.Summary.Streaming(SettingsString.PREF_STREAMING_QUALITY_ORIGINAL)
    }

    @Test
    fun `the root sums Library up by its rescan frequency`() {
        summaryOf(SettingsDestination.Library, SettingsScenarios.scannedWeekly) shouldBe
            SettingsRootViewState.Summary.Rescan(SettingsString.PREF_RESCAN_FREQUENCY_WEEKLY)
    }

    @Test
    fun `the root gives Privacy its own summary and About none`() {
        summaryOf(SettingsDestination.Privacy) shouldBe SettingsRootViewState.Summary.Privacy
        summaryOf(SettingsDestination.About) shouldBe SettingsRootViewState.Summary.None
    }

    @Test
    fun `rows newer than the device are left out`() {
        destination(SettingsDestination.Appearance, sdkInt = 30).rows().none { it.item.key == AppearanceSettings.DynamicColour.key } shouldBe true
        destination(SettingsDestination.Appearance, sdkInt = 31).rows().any { it.item.key == AppearanceSettings.DynamicColour.key } shouldBe true
    }

    @Test
    fun `USB DAC direct output appears only from Android 14`() {
        destination(SettingsDestination.PlaybackAndSound, sdkInt = 33).rows()
            .none { it.item.key == PlaybackSettings.UsbDacDirectOutput.key } shouldBe true
        destination(SettingsDestination.PlaybackAndSound, sdkInt = 34).rows()
            .any { it.item.key == PlaybackSettings.UsbDacDirectOutput.key } shouldBe true
    }

    @Test
    fun `the accent is disabled with a hint while dynamic colour is on`() {
        val accent = destination(SettingsDestination.Appearance, SettingsScenarios.dynamicColourOn).row(AppearanceSettings.AccentColour.key) as Row.Choice

        accent.enabled shouldBe false
        accent.override shouldBe SettingsString.PREF_THEME_ACCENT_DYNAMIC_COLOUR_HINT
    }

    @Test
    fun `the accent can be picked with dynamic colour off`() {
        val accent = destination(SettingsDestination.Appearance, SettingsScenarios.dynamicColourOff).row(AppearanceSettings.AccentColour.key) as Row.Choice

        accent.enabled shouldBe true
        accent.override shouldBe null
    }

    @Test
    fun `below Android 12 dynamic colour can't take the accent over`() {
        val accent = destination(SettingsDestination.Appearance, SettingsScenarios.dynamicColourOn, sdkInt = 30)
            .row(AppearanceSettings.AccentColour.key) as Row.Choice

        accent.enabled shouldBe true
        accent.override shouldBe null
    }

    @Test
    fun `sharing logs follows debug logging`() {
        destination(SettingsDestination.About, SettingsScenarios.fileLoggingOff).rows()
            .first { it.item.title == SettingsString.PREF_SHARE_DEBUG_LOGS_TITLE }.enabled shouldBe false
        destination(SettingsDestination.About, SettingsScenarios.fileLoggingOn).rows()
            .first { it.item.title == SettingsString.PREF_SHARE_DEBUG_LOGS_TITLE }.enabled shouldBe true
        (destination(SettingsDestination.About, SettingsScenarios.fileLoggingOn).row(DebugSettings.FileLogging.key) as Row.Switch).checked shouldBe true
    }

    @Test
    fun `the rescan choice carries the last scan date`() {
        val rescan = destination(SettingsDestination.Library, SettingsScenarios.scannedWeekly).rows()
            .filterIsInstance<Row.Choice>().first { it.item.showsLastScan }

        rescan.label shouldBe SettingsString.PREF_RESCAN_FREQUENCY_WEEKLY
        rescan.lastScan shouldBe Instant.fromEpochMilliseconds(0)
    }

    @Test
    fun `other choices carry no last scan date`() {
        destination(SettingsDestination.Appearance, SettingsScenarios.scannedWeekly).rows()
            .filterIsInstance<Row.Choice>().all { it.lastScan == null } shouldBe true
    }

    @Test
    fun `crossfade reads Off at zero`() {
        crossfade(0).valueLabel shouldBe SliderText.Off
    }

    @Test
    fun `crossfade shows its length in seconds`() {
        crossfade(6000).valueLabel shouldBe SliderText.Seconds(6)
    }

    @Test
    fun `crossfade clamps a stored length the slider can't show`() {
        val row = crossfade(15_000)

        row.value shouldBe 12_000f
        row.valueLabel shouldBe SliderText.Seconds(12)
    }

    private fun crossfade(millis: Int) = destination(
        SettingsDestination.PlaybackAndSound,
        SettingsUiState(values = mapOf(PlaybackSettings.CrossfadeDuration.key to millis))
    ).row(PlaybackSettings.CrossfadeDuration.key) as Row.Slider

    @Test
    fun `only About shows the version`() {
        destination(SettingsDestination.About, versionName = "2026.09.25").version shouldBe "2026.09.25"
        destination(SettingsDestination.Library, versionName = "2026.09.25").version shouldBe null
        destination(SettingsDestination.About).version shouldBe null
    }

    @Test
    fun `the Equalizer row shows the preset while it is on`() {
        val navigate = destination(SettingsDestination.PlaybackAndSound, SettingsScenarios.equalizerOn).rows()
            .filterIsInstance<Row.Navigate>().first { it.item.target == SettingsLink.Equalizer }

        navigate.summary shouldBe SettingsScenarios.equalizerOn.equalizerSummary
    }

    @Test
    fun `the media provider link stays hidden until the sources redesign`() {
        destination(SettingsDestination.Sources).rows().filterIsInstance<Row.Navigate>().map { it.item.target } shouldBe listOf(SettingsLink.Downloads)
    }

    @Test
    fun `sources reads the streaming qualities off the stored values`() {
        val labels = destination(SettingsDestination.Sources, SettingsScenarios.streamingCappedOnMobileData).rows()
            .filterIsInstance<Row.Choice>().map { it.label }

        labels.first() shouldBe SettingsString.PREF_STREAMING_QUALITY_ORIGINAL
        labels[1] shouldBe SettingsString.PREF_STREAMING_QUALITY_128
        labels.last() shouldBe SettingsString.PREF_STREAMING_QUALITY_192
    }
}
