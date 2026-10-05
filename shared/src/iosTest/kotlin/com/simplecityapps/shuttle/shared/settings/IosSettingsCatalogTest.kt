package com.simplecityapps.shuttle.shared.settings

import com.simplecityapps.playback.dsp.replaygain.ReplayGainMode
import com.simplecityapps.playback.settings.PlaybackSettings
import com.simplecityapps.shuttle.settings.AppearanceSettings
import com.simplecityapps.shuttle.settings.ArtworkSettings
import com.simplecityapps.shuttle.settings.DebugSettings
import com.simplecityapps.shuttle.settings.DownloadSettings
import com.simplecityapps.shuttle.settings.EqualizerSettings
import com.simplecityapps.shuttle.settings.PrivacySettings
import com.simplecityapps.shuttle.settings.StreamingQuality
import com.simplecityapps.shuttle.settings.StreamingSettings
import com.simplecityapps.shuttle.settings.TranscodeFormat
import com.simplecityapps.shuttle.ui.screens.settings.SettingsUiState
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingItem
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsAction
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsDestination
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsLink
import com.simplecityapps.shuttle.ui.text.StringKey
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
            SettingsDestination.Appearance,
            SettingsDestination.Privacy
        )
    }

    @Test
    fun theStoredSettingsAreExactlyTheOnesIosReads() {
        catalog.settings.map { it.key } shouldContainExactly listOf(
            PlaybackSettings.RetainShuffleOnNewQueue.key,
            EqualizerSettings.Enabled.key,
            PlaybackSettings.ReplayGain.key,
            PlaybackSettings.PreAmpGain.key,
            StreamingSettings.UnmeteredQuality.key,
            StreamingSettings.MeteredQuality.key,
            StreamingSettings.Format.key,
            StreamingSettings.DownloadQuality.key,
            DownloadSettings.WifiOnly.key,
            ArtworkSettings.LocalOnly.key,
            AppearanceSettings.ColourFromArtwork.key,
            AppearanceSettings.ShowHomeOnLaunch.key,
            PrivacySettings.CrashReporting.key,
            PrivacySettings.Analytics.key
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
            ArtworkSettings.WifiOnly.key,
            ArtworkSettings.MediaSessionArtwork.key,
            DebugSettings.FileLogging.key
        )
    }

    @Test
    fun privacyHasTheCrashReportingAndAnalyticsSwitchesWithAndroidsText() {
        val switches = catalog.privacy.items.filterIsInstance<SettingItem.Switch>()
        switches.map { it.setting } shouldContainExactly listOf(PrivacySettings.CrashReporting, PrivacySettings.Analytics)
        switches.map { it.title } shouldContainExactly listOf(StringKey.PREF_CRASH_REPORTING_TITLE, StringKey.PREF_ANALYTICS_TITLE)
        switches.map { it.summary } shouldContainExactly listOf(StringKey.PREF_CRASH_REPORTING_SUBTITLE, StringKey.PREF_ANALYTICS_SUBTITLE)
    }

    @Test
    fun theLinksAreTheEqualizerAndScrobblingAndTheOnlySliderTheReplayGainPreamp() {
        catalog.items.filterIsInstance<SettingItem.Navigate>().map { it.target } shouldContainExactly listOf(SettingsLink.Equalizer, SettingsLink.Scrobbling)
        catalog.items.filterIsInstance<SettingItem.Slider<*>>().map { it.setting } shouldContainExactly listOf(PlaybackSettings.PreAmpGain)
    }

    @Test
    fun sourcesIsOneStreamingAndDownloadsGroupWithTheNetworkQualitiesTheFormatAndTheDownloadQuality() {
        val group = catalog.sources.groups.single()
        group.title shouldBe StringKey.SETTINGS_GROUP_STREAMING_AND_DOWNLOADS
        group.items.map { it.key } shouldContainExactly listOf(
            StreamingSettings.UnmeteredQuality.key,
            StreamingSettings.MeteredQuality.key,
            StreamingSettings.Format.key,
            StreamingSettings.DownloadQuality.key
        )
        val format = group.items.filterIsInstance<SettingItem.Choice<*>>().single { it.setting == StreamingSettings.Format }
        format.options.map { it.value } shouldContainExactly TranscodeFormat.entries
        SettingsUiState(values = mapOf(format.key to TranscodeFormat.Opus)).selectedIndex(format) shouldBe 1
    }

    @Test
    fun theStateReadsAnswerInPlainTypes() {
        val shuffle = catalog.playbackAndSound.items.filterIsInstance<SettingItem.Switch>().single()
        val metered = catalog.sources.items.filterIsInstance<SettingItem.Choice<*>>().single { it.setting == StreamingSettings.MeteredQuality }
        val replayGain = catalog.playbackAndSound.items.filterIsInstance<SettingItem.Choice<*>>().single()
        val preamp = catalog.playbackAndSound.items.filterIsInstance<SettingItem.Slider<*>>().single()
        val state = SettingsUiState(
            values = mapOf(
                shuffle.key to true,
                metered.key to StreamingQuality.Kbps192,
                replayGain.key to ReplayGainMode.Album,
                preamp.key to -2.5f
            )
        )

        state.isOn(shuffle) shouldBe true
        state.selectedIndex(metered) shouldBe 2
        state.isEnabled(metered, catalog) shouldBe true
        state.selectedIndex(replayGain) shouldBe 1
        state.sliderValue(preamp) shouldBe -2.5f
        preamp.minimum shouldBe -12f
        preamp.maximum shouldBe 12f
        SettingsUiState().isOn(shuffle) shouldBe false
        SettingsUiState().sliderValue(preamp) shouldBe 0f
    }

    @Test
    fun scrobblingIsInPlaybackAndSoundAndGoesWhenLastFmIsntConfigured() {
        catalog.playbackAndSound.items.filterIsInstance<SettingItem.Navigate>().map { it.target } shouldContainExactly
            listOf(SettingsLink.Equalizer, SettingsLink.Scrobbling)
        catalog.playbackAndSound.withoutScrobbling().items.filterIsInstance<SettingItem.Navigate>().map { it.target } shouldContainExactly
            listOf(SettingsLink.Equalizer)
    }
}
