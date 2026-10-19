package com.simplecityapps.shuttle.ui.screens.settings

import android.content.res.Resources
import com.simplecityapps.shuttle.ui.screens.settings.SettingsDestinationViewState.Row
import com.simplecityapps.shuttle.ui.screens.settings.SettingsDestinationViewState.SliderText
import com.simplecityapps.shuttle.ui.screens.settings.SettingsRootViewState.ProSummary
import com.simplecityapps.shuttle.ui.screens.settings.SettingsRootViewState.Summary
import com.simplecityapps.shuttle.ui.screens.settings.model.AndroidSettingsCatalog
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsDestination
import com.simplecityapps.shuttle.ui.text.SettingsString
import com.simplecityapps.shuttle.ui.text.StringKey
import com.simplecityapps.shuttle.ui.text.resId
import io.kotest.matchers.shouldBe
import java.util.Locale
import java.util.TimeZone
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** The English copy behind each view-state value, read from the app's real resources without composing a screen. */
@RunWith(RobolectricTestRunner::class)
class SettingsTextTest {
    private val resources: Resources = RuntimeEnvironment.getApplication().resources

    private fun text(key: StringKey) = resources.getString(key.resId)

    private fun summary(summary: Summary) = rootSummaryText(summary, resources)

    private fun choice(
        destination: SettingsDestination,
        uiState: SettingsUiState,
        title: SettingsString
    ) = settingsDestinationViewState(AndroidSettingsCatalog.screen(destination), uiState, 34, null).groups
        .flatMap { it.rows }.filterIsInstance<Row.Choice>().first { it.item.title == title }

    @Test
    fun `the Pro row copy follows ownership`() {
        proSummaryText(ProSummary.Owned, resources) shouldBe "Thank you for your support"
        proSummaryText(ProSummary.Upsell, resources) shouldBe "Servers, Android Auto, batch tag editing and ReplayGain"
        proSummaryText(ProSummary.None, resources) shouldBe null
    }

    @Test
    fun `root summaries read as the settings they stand for`() {
        summary(Summary.Option(SettingsString.THEME_ENTRY_DARK)) shouldBe "Dark"
        summary(Summary.EqualizerOff) shouldBe "Equalizer off"
        summary(Summary.EqualizerPreset(SettingsScenarios.equalizerOn.equalizerSummary)) shouldBe "Equalizer · Bass boost"
        summary(Summary.Streaming(SettingsString.PREF_STREAMING_QUALITY_320)) shouldBe "320 kbps on mobile data"
        summary(Summary.Streaming(SettingsString.PREF_STREAMING_QUALITY_ORIGINAL)) shouldBe "Original on mobile data"
        summary(Summary.Rescan(SettingsString.PREF_RESCAN_FREQUENCY_WEEKLY)) shouldBe "Rescan: Weekly"
        summary(Summary.None) shouldBe null
    }

    @Test
    fun `the Transcode format row reads Opus and the group its title`() {
        val destination = settingsDestinationViewState(AndroidSettingsCatalog.screen(SettingsDestination.Sources), SettingsScenarios.streamingCappedOnMobileData, 34, null)

        text(destination.groups.mapNotNull { it.title }.single()) shouldBe "Streaming & downloads"
        text(choice(SettingsDestination.Sources, SettingsScenarios.streamingCappedOnMobileData, SettingsString.PREF_TRANSCODE_FORMAT_TITLE).label!!) shouldBe "Opus"
    }

    @Test
    fun `each Sources row has its title`() {
        val titles = settingsDestinationViewState(AndroidSettingsCatalog.screen(SettingsDestination.Sources), SettingsScenarios.streamingCappedOnMobileData, 34, null).groups
            .flatMap { it.rows }.map { it.item.title }

        titles.map(::text).containsAll(
            listOf("Report playback to server", "On Wi-Fi", "On mobile data", "Transcode format", "Download quality")
        ) shouldBe true
    }

    @Test
    fun `the streaming rows read their stored qualities`() {
        val labels = settingsDestinationViewState(AndroidSettingsCatalog.screen(SettingsDestination.Sources), SettingsScenarios.streamingCappedOnMobileData, 34, null).groups
            .flatMap { it.rows }.filterIsInstance<Row.Choice>().map { text(it.label!!) }

        labels shouldBe listOf("Original", "128 kbps", "Opus", "192 kbps")
    }

    @Test
    fun `the rescan value adds the last scan date`() {
        val rescan = choice(SettingsDestination.Library, SettingsScenarios.scannedWeekly, SettingsString.PREF_RESCAN_FREQUENCY_TITLE)

        choiceValueText(rescan, resources, Locale.US, TimeZone.getTimeZone("UTC")) shouldBe "Weekly (Last scan 1/1/70, 12:00 AM)"
    }

    @Test
    fun `the crossfade slider reads Off then its seconds`() {
        sliderValueText(SliderText.Off, resources, Locale.US) shouldBe "Off"
        sliderValueText(SliderText.Seconds(6), resources, Locale.US) shouldBe "6 s"
        sliderValueText(SliderText.Seconds(12), resources, Locale.US) shouldBe "12 s"
        sliderValueText(SliderText.None, resources, Locale.US) shouldBe null
    }

    @Test
    fun `the crossfade row is titled Crossfade`() {
        text(SettingsString.PREF_CROSSFADE_TITLE) shouldBe "Crossfade"
    }
}
