package com.simplecityapps.shuttle.ui.screens.settings.model

import com.simplecityapps.shuttle.settings.Accent
import com.simplecityapps.shuttle.settings.AppearanceSettings
import com.simplecityapps.shuttle.settings.ThemeMode
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.junit.Test

class SettingsCatalogTest {
    /** Everything the catalog accounts for: rows' keys and the legacy screens each destination replaces. */
    private val mappedKeys: Set<String> =
        SettingsCatalog.items.mapNotNull { it.key }.toSet() + SettingsDestination.entries.flatMap { it.legacyKeys }

    @Test
    fun `every key in the legacy preference XMLs is mapped or dropped on purpose`() {
        val unaccounted = legacyXmlKeys - mappedKeys - SettingsCatalog.droppedKeys.keys

        unaccounted.shouldBeEmpty()
    }

    @Test
    fun `nothing is both mapped and dropped`() {
        (mappedKeys intersect SettingsCatalog.droppedKeys.keys).shouldBeEmpty()
    }

    @Test
    fun `no key appears on two rows`() {
        val keys = SettingsCatalog.items.mapNotNull { it.key }

        keys.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.shouldBeEmpty()
    }

    @Test
    fun `every destination has one screen`() {
        SettingsCatalog.screens.map { it.destination } shouldBe SettingsDestination.entries
    }

    @Test
    fun `choices offer every value and include the default`() {
        SettingsCatalog.items.filterIsInstance<SettingItem.Choice<*>>().forEach { choice ->
            val values = choice.options.map { it.value }
            values.distinct().size shouldBe values.size
            values.contains(choice.setting.default) shouldBe true
        }
        choiceValues(AppearanceSettings.Theme.key) shouldContainExactlyInAnyOrder ThemeMode.entries
        choiceValues(AppearanceSettings.AccentColour.key) shouldContainExactlyInAnyOrder Accent.entries
    }

    @Test
    fun `sliders' ranges hold their defaults`() {
        SettingsCatalog.items.filterIsInstance<SettingItem.Slider<*>>().forEach { slider ->
            (slider.setting.default.toFloat() in slider.range) shouldBe true
        }
    }

    @Test
    fun `dependencies point at switches in the catalog`() {
        val switches = SettingsCatalog.items.filterIsInstance<SettingItem.Switch>().map { it.setting }
        SettingsCatalog.items.mapNotNull { it.dependsOn }.forEach { dependency ->
            switches.contains(dependency) shouldBe true
        }
    }

    private fun choiceValues(key: String): List<Any?> = SettingsCatalog.items
        .filterIsInstance<SettingItem.Choice<*>>()
        .single { it.key == key }
        .options
        .map { it.value }

    companion object {
        /** Every key in res/xml/preferences*.xml when the Compose settings work started. */
        val legacyXmlKeys = listOf(
            "pref_screen_display",
            "pref_screen_playback",
            "pref_screen_media",
            "pref_screen_artwork",
            "pref_screen_widget",
            "pref_screen_app_info",
            "pref_screen_privacy",
            "pref_screen_debug",
            "changelog_show",
            "changelog_show_on_launch",
            "licenses_show",
            "artwork_wifi_only",
            "artwork_local_only",
            "pref_clear_artwork",
            "pref_download_artwork",
            "media_session_artwork",
            "pref_file_logging",
            "pref_copy_debug_logs",
            "pref_media_provider",
            "pref_report_playback",
            "pref_media_rescan",
            "pref_media_rescan_frequency",
            "pref_excluded",
            "pref_retain_shuffle_on_new_queue",
            "pref_bit_perfect_usb",
            "pref_crash_reporting",
            "pref_firebase_analytics",
            "widget_background_opacity"
        )
    }
}
