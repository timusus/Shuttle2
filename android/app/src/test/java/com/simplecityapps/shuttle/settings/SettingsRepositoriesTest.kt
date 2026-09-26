package com.simplecityapps.shuttle.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.simplecityapps.mediaprovider.settings.LibrarySettings
import com.simplecityapps.mediaprovider.worker.ImportFrequency
import com.simplecityapps.playback.dsp.replaygain.ReplayGainMode
import com.simplecityapps.playback.settings.PlaybackSettings
import com.simplecityapps.shuttle.downloads.DownloadSettings
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SettingsRepositoriesTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private lateinit var prefs: SharedPreferences
    private lateinit var store: SettingsStore

    @Before
    fun setUp() {
        prefs = context.defaultSharedPreferences().apply { edit().clear().commit() }
        store = SettingsStore(prefs)
    }

    @Test
    fun `the store opens the file the legacy preference screens wrote`() {
        PreferenceManager.getDefaultSharedPreferences(context).edit(commit = true) { putBoolean("probe", true) }

        context.defaultSharedPreferences().getBoolean("probe", false) shouldBe true
    }

    @Test
    fun `keys and defaults are the ones the app has always used`() {
        keysAndDefaults(allPreferences()) shouldBe mapOf(
            "pref_theme" to ThemeMode.DayNight,
            "pref_theme_accent" to Accent.Default,
            "pref_theme_extra_dark" to false,
            "pref_theme_dynamic_colour" to true,
            "pref_theme_colour_from_artwork" to true,
            "pref_show_home_on_launch" to false,
            "widget_background_opacity" to 100,
            "artwork_wifi_only" to true,
            "artwork_local_only" to false,
            "media_session_artwork" to true,
            "pref_crash_reporting" to true,
            "pref_firebase_analytics" to true,
            "pref_file_logging" to false,
            "pref_retain_shuffle_on_new_queue" to false,
            "pref_bit_perfect_usb" to false,
            "equalizer_enabled" to false,
            "replaygain_mode" to ReplayGainMode.Off,
            "preamp_gain" to 0f,
            "pref_media_rescan_frequency" to ImportFrequency.Never,
            "pref_report_playback" to true,
            "pref_download_wifi_only" to true
        )
    }

    @Test
    fun `an empty store reads every default`() {
        allPreferences().forEach { preference -> preference.value shouldBe preference.default }
    }

    @Test
    fun `values round-trip through the store`() {
        val appearance = AppearanceSettings(store)
        appearance.theme.value = ThemeMode.Dark
        appearance.accent.value = Accent.Amber
        appearance.widgetBackgroundOpacity.value = 40
        val playback = PlaybackSettings(store)
        playback.replayGainMode.value = ReplayGainMode.Album
        playback.preAmpGain.value = -3.5f
        playback.playbackSpeed.value = 1.25f
        val library = LibrarySettings(store)
        library.rescanFrequency.value = ImportFrequency.Weekly
        library.reportPlaybackToServer.value = false

        // Fresh repositories over the same file see the writes
        val reread = SettingsStore(context.defaultSharedPreferences())
        AppearanceSettings(reread).theme.value shouldBe ThemeMode.Dark
        AppearanceSettings(reread).accent.value shouldBe Accent.Amber
        AppearanceSettings(reread).widgetBackgroundOpacity.value shouldBe 40
        PlaybackSettings(reread).replayGainMode.value shouldBe ReplayGainMode.Album
        PlaybackSettings(reread).preAmpGain.value shouldBe -3.5f
        PlaybackSettings(reread).playbackSpeed.value shouldBe 1.25f
        LibrarySettings(reread).rescanFrequency.value shouldBe ImportFrequency.Weekly
        LibrarySettings(reread).reportPlaybackToServer.value shouldBe false
    }

    @Test
    fun `values are stored in the formats the legacy screens wrote`() {
        AppearanceSettings(store).theme.value = ThemeMode.Light
        AppearanceSettings(store).accent.value = Accent.Cyan
        PlaybackSettings(store).replayGainMode.value = ReplayGainMode.Track
        LibrarySettings(store).rescanFrequency.value = ImportFrequency.Daily

        prefs.getString("pref_theme", null) shouldBe "1"
        prefs.getString("pref_theme_accent", null) shouldBe "2"
        prefs.getInt("replaygain_mode", -1) shouldBe 0
        prefs.getString("pref_media_rescan_frequency", null) shouldBe "1"
    }

    @Test
    fun `values the legacy screens wrote read back`() {
        prefs.edit(commit = true) {
            putString("pref_theme", "2")
            putString("pref_theme_accent", "5")
            putInt("replaygain_mode", 1)
            putFloat("preamp_gain", 6f)
            putString("pref_media_rescan_frequency", "2")
            putInt("widget_background_opacity", 55)
        }

        AppearanceSettings(store).theme.value shouldBe ThemeMode.Dark
        AppearanceSettings(store).accent.value shouldBe Accent.Amber
        PlaybackSettings(store).replayGainMode.value shouldBe ReplayGainMode.Album
        PlaybackSettings(store).preAmpGain.value shouldBe 6f
        LibrarySettings(store).rescanFrequency.value shouldBe ImportFrequency.Weekly
        AppearanceSettings(store).widgetBackgroundOpacity.value shouldBe 55
    }

    @Test
    fun `unreadable values fall back to the default`() {
        prefs.edit(commit = true) {
            putString("pref_theme", "9")
            putString("pref_theme_accent", "not a number")
            putString("artwork_wifi_only", "yes")
        }

        AppearanceSettings(store).theme.value shouldBe ThemeMode.DayNight
        AppearanceSettings(store).accent.value shouldBe Accent.Default
        ArtworkSettings(store).wifiOnly.value shouldBe true
    }

    @Test
    fun `reset forgets the stored value`() {
        val privacy = PrivacySettings(store)
        privacy.analytics.value = false

        privacy.analytics.reset()

        privacy.analytics.value shouldBe true
        prefs.contains("pref_firebase_analytics") shouldBe false
    }

    @Test
    fun `the flow emits the current value, then each change`() = runTest(UnconfinedTestDispatcher()) {
        val theme = AppearanceSettings(store).theme
        val emitted = mutableListOf<ThemeMode>()
        val job = launch { theme.flow.take(3).toList(emitted) }

        theme.value = ThemeMode.Dark
        theme.value = ThemeMode.Dark // Unchanged, not re-emitted
        theme.value = ThemeMode.Light
        job.join()

        emitted shouldBe listOf(ThemeMode.DayNight, ThemeMode.Dark, ThemeMode.Light)
    }

    @Test
    fun `the flow ignores other keys`() = runTest(UnconfinedTestDispatcher()) {
        val downloads = DownloadSettings(store)
        val emitted = mutableListOf<Boolean>()
        val job = launch { downloads.wifiOnly.flow.toList(emitted) }

        ArtworkSettings(store).wifiOnly.value = false
        downloads.wifiOnly.value = false
        job.cancel()

        emitted shouldBe listOf(true, false)
    }

    @Test
    fun `stateIn starts from the stored value`() = runTest(UnconfinedTestDispatcher()) {
        DebugSettings(store).fileLogging.value = true

        val state = DebugSettings(store).fileLogging.stateIn(backgroundScope)

        state.value shouldBe true
        state.first() shouldBe true
    }

    private fun allPreferences(): List<Preference<*>> {
        val appearance = AppearanceSettings(store)
        val artwork = ArtworkSettings(store)
        val privacy = PrivacySettings(store)
        val debug = DebugSettings(store)
        val playback = PlaybackSettings(store)
        val library = LibrarySettings(store)
        val downloads = DownloadSettings(store)
        return listOf(
            appearance.theme,
            appearance.accent,
            appearance.pureBlack,
            appearance.dynamicColour,
            appearance.colourFromArtwork,
            appearance.showHomeOnLaunch,
            appearance.widgetBackgroundOpacity,
            artwork.wifiOnly,
            artwork.localOnly,
            artwork.mediaSessionArtwork,
            privacy.crashReporting,
            privacy.analytics,
            debug.fileLogging,
            playback.retainShuffleOnNewQueue,
            playback.usbDacDirectOutput,
            playback.equalizerEnabled,
            playback.replayGainMode,
            playback.preAmpGain,
            library.rescanFrequency,
            library.reportPlaybackToServer,
            downloads.wifiOnly
        )
    }

    private fun keysAndDefaults(preferences: List<Preference<*>>): Map<String, Any?> = preferences.associate { it.key to it.default }
}
