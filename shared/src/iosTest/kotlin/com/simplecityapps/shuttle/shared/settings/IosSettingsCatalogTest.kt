package com.simplecityapps.shuttle.shared.settings

import com.simplecityapps.playback.dsp.replaygain.ReplayGainMode
import com.simplecityapps.playback.settings.PlaybackSettings
import com.simplecityapps.shuttle.settings.AppearanceSettings
import com.simplecityapps.shuttle.settings.ArtworkSettings
import com.simplecityapps.shuttle.settings.DebugSettings
import com.simplecityapps.shuttle.settings.PrivacySettings
import com.simplecityapps.shuttle.settings.StreamingQuality
import com.simplecityapps.shuttle.settings.StreamingSettings
import com.simplecityapps.shuttle.ui.screens.settings.SettingsUiState
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingItem
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsAction
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsDestination
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsLink
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContainAnyOf
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** iOS shows only the settings something on iOS acts on (see [IosSettingsCatalog]'s list of what it leaves out). */
class IosSettingsCatalogTest {
    private val catalog = IosSettingsCatalog

    @Test
    fun theDestinationsAreTheOnesWithRowsIosActsOn() {
        catalog.screens.map { it.destination } shouldContainExactly listOf(
            SettingsDestination.PlaybackAndSound,
            SettingsDestination.Sources,
            SettingsDestination.Library,
            SettingsDestination.Appearance
        )
    }

    @Test
    fun theStoredSettingsAreExactlyTheOnesIosReads() {
        catalog.settings.map { it.key } shouldContainExactly listOf(
            PlaybackSettings.RetainShuffleOnNewQueue.key,
            PlaybackSettings.ReplayGain.key,
            StreamingSettings.UnmeteredQuality.key,
            StreamingSettings.MeteredQuality.key,
            ArtworkSettings.LocalOnly.key,
            AppearanceSettings.ShowHomeOnLaunch.key
        )
    }

    @Test
    fun theOnlyActionIsRescan() {
        catalog.items.filterIsInstance<SettingItem.Action>().map { it.action } shouldContainExactly listOf(SettingsAction.Rescan)
    }

    @Test
    fun androidOnlyAndNotYetBuiltSettingsAreLeftOut() {
        catalog.settings.map { it.key } shouldNotContainAnyOf listOf(
            AppearanceSettings.Theme.key,
            AppearanceSettings.DynamicColour.key,
            AppearanceSettings.AccentColour.key,
            AppearanceSettings.WidgetBackgroundOpacity.key,
            PlaybackSettings.UsbDacDirectOutput.key,
            // One Preamp, the Equalizer's (#645).
            PlaybackSettings.PreAmpGain.key,
            ArtworkSettings.WifiOnly.key,
            ArtworkSettings.MediaSessionArtwork.key,
            PrivacySettings.CrashReporting.key,
            PrivacySettings.Analytics.key,
            DebugSettings.FileLogging.key
        )
    }

    @Test
    fun theOnlyLinkIsTheEqualizerWhichHoldsTheOnlyPreamp() {
        catalog.items.filterIsInstance<SettingItem.Navigate>().map { it.target } shouldContainExactly listOf(SettingsLink.Equalizer)
        catalog.items.filterIsInstance<SettingItem.Slider<*>>() shouldBe emptyList()
    }

    @Test
    fun theStateReadsAnswerInPlainTypes() {
        val shuffle = catalog.playbackAndSound.items.filterIsInstance<SettingItem.Switch>().single()
        val metered = catalog.sources.items.filterIsInstance<SettingItem.Choice<*>>().single { it.setting == StreamingSettings.MeteredQuality }
        val replayGain = catalog.playbackAndSound.items.filterIsInstance<SettingItem.Choice<*>>().single()
        val state = SettingsUiState(
            values = mapOf(
                shuffle.key to true,
                metered.key to StreamingQuality.Kbps192,
                replayGain.key to ReplayGainMode.Album
            )
        )

        state.isOn(shuffle) shouldBe true
        state.selectedIndex(metered) shouldBe 2
        state.isEnabled(metered, catalog) shouldBe true
        state.selectedIndex(replayGain) shouldBe 1
        SettingsUiState().isOn(shuffle) shouldBe false
    }
}
