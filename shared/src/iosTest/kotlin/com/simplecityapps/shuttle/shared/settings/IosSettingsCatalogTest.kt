package com.simplecityapps.shuttle.shared.settings

import com.simplecityapps.playback.settings.PlaybackSettings
import com.simplecityapps.shuttle.settings.AppearanceSettings
import com.simplecityapps.shuttle.settings.ArtworkSettings
import com.simplecityapps.shuttle.settings.DebugSettings
import com.simplecityapps.shuttle.settings.EqualizerSettings
import com.simplecityapps.shuttle.settings.PrivacySettings
import com.simplecityapps.shuttle.settings.StreamingQuality
import com.simplecityapps.shuttle.settings.StreamingSettings
import com.simplecityapps.shuttle.ui.screens.settings.SettingsUiState
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingItem
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsAction
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsDestination
import io.kotest.matchers.collections.shouldBeEmpty
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
            SettingsDestination.Library
        )
    }

    @Test
    fun theStoredSettingsAreExactlyTheOnesIosReads() {
        catalog.settings.map { it.key } shouldContainExactly listOf(
            PlaybackSettings.RetainShuffleOnNewQueue.key,
            StreamingSettings.UnmeteredQuality.key,
            StreamingSettings.MeteredQuality.key,
            ArtworkSettings.LocalOnly.key
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
            PlaybackSettings.ReplayGain.key,
            PlaybackSettings.PreAmpGain.key,
            EqualizerSettings.Enabled.key,
            ArtworkSettings.WifiOnly.key,
            ArtworkSettings.MediaSessionArtwork.key,
            PrivacySettings.CrashReporting.key,
            PrivacySettings.Analytics.key,
            DebugSettings.FileLogging.key
        )
    }

    @Test
    fun noRowNeedsARouteOrSliderSwiftDoesNotDraw() {
        catalog.items.filter { it is SettingItem.Navigate || it is SettingItem.Slider<*> }.shouldBeEmpty()
    }

    @Test
    fun theStateReadsAnswerInPlainTypes() {
        val shuffle = catalog.playbackAndSound.items.single() as SettingItem.Switch
        val metered = catalog.sources.items.filterIsInstance<SettingItem.Choice<*>>().single { it.setting == StreamingSettings.MeteredQuality }
        val state = SettingsUiState(values = mapOf(shuffle.key to true, metered.key to StreamingQuality.Kbps192))

        state.isOn(shuffle) shouldBe true
        state.selectedIndex(metered) shouldBe 2
        state.isEnabled(metered, catalog) shouldBe true
        SettingsUiState().isOn(shuffle) shouldBe false
    }
}
