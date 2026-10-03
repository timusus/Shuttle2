package com.simplecityapps.shuttle.backup

import com.simplecityapps.playback.equalizer.KeyValueEqualizerPresetStore
import com.simplecityapps.playback.settings.PlaybackSettings
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.persistence.LibraryTab
import com.simplecityapps.shuttle.settings.Accent
import com.simplecityapps.shuttle.settings.AppearanceSettings
import com.simplecityapps.shuttle.settings.EqualizerSettings
import com.simplecityapps.shuttle.settings.ThemeMode
import com.simplecityapps.shuttle.ui.screens.settings.backup.LibraryBackup
import io.kotest.assertions.withClue
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test

class BackedUpSettingsTest {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    @Test
    fun `exporting then restoring brings the preferences back`() {
        val source = InMemoryKeyValueStore()
        source.edit {
            AppearanceSettings.Theme.write(this, ThemeMode.Dark)
            AppearanceSettings.AccentColour.write(this, Accent.entries.last())
            AppearanceSettings.PureBlack.write(this, true)
            PlaybackSettings.PreAmpGain.write(this, 3.5f)
            PlaybackSettings.CrossfadeDuration.write(this, 4000)
            EqualizerSettings.Enabled.write(this, true)
            putString("pref_library_tabs_enabled", "Songs,Albums")
            putBoolean("sleep_timer_play_to_end", true)
        }
        val exported = json.decodeFromString<Map<String, JsonPrimitive>>(json.encodeToString(BackedUpSettings.export(source)))

        val target = InMemoryKeyValueStore()
        BackedUpSettings.restore(target, exported)

        AppearanceSettings.Theme.read(target) shouldBe ThemeMode.Dark
        AppearanceSettings.AccentColour.read(target) shouldBe Accent.entries.last()
        AppearanceSettings.PureBlack.read(target) shouldBe true
        PlaybackSettings.PreAmpGain.read(target) shouldBe 3.5f
        PlaybackSettings.CrossfadeDuration.read(target) shouldBe 4000
        EqualizerSettings.Enabled.read(target) shouldBe true
        target.getString("pref_library_tabs_enabled", null) shouldBe "Songs,Albums"
        target.getBoolean("sleep_timer_play_to_end", false) shouldBe true
    }

    @Test
    fun `secrets and per-device state are never exported`() {
        val excluded = mapOf(
            "credentials and tokens" to listOf("jellyfin_access_token", "jellyfin_address", "jellyfin_username", "jellyfin_pass", "jellyfin_user_id", "plex_token"),
            "billing and trial" to listOf("app_purchased_date", "server_trial_started_at", "cached_pro_seen_at"),
            "SAF URIs and paths" to listOf("scanner_included_folders", "scanner_excluded_folders", "scanner_extra_folders"),
            "onboarding flags" to listOf("onboarding_completed", "changelog_show_on_launch", "last_viewed_changelog_version", "pref_analytics_consent_asked", "pref_analytics_notice_shown"),
            "analytics ids" to listOf("client_id", "user_id"),
            "per-device state" to listOf("queue_ids", "search_recent", "library_tab_current")
        )
        val store = InMemoryKeyValueStore()
        store.edit {
            putBoolean("pref_theme_extra_dark", true)
            excluded.values.flatten().forEach { key -> putString(key, "content://tree/secret") }
        }

        val exported = BackedUpSettings.export(store)

        excluded.forEach { (group, keys) ->
            withClue(group) { keys.forEach { key -> exported shouldNotContainKey key } }
        }
        exported.keys shouldBe setOf("pref_theme_extra_dark")
    }

    @Test
    fun `the equalizer's preset and band gains are backed up`() {
        val source = InMemoryKeyValueStore()
        source.edit {
            putString(KeyValueEqualizerPresetStore.PresetKey, "Bass Boost")
            putString(KeyValueEqualizerPresetStore.CustomPresetBandsKey, "[]")
        }

        val target = InMemoryKeyValueStore()
        BackedUpSettings.restore(target, BackedUpSettings.export(source))

        target.getString(KeyValueEqualizerPresetStore.PresetKey, null) shouldBe "Bass Boost"
        target.getString(KeyValueEqualizerPresetStore.CustomPresetBandsKey, null) shouldBe "[]"
    }

    @Test
    fun `restored library tabs keep only the tabs this version knows, and none at all leaves the default`() {
        val store = InMemoryKeyValueStore()

        BackedUpSettings.restore(
            store,
            mapOf(
                "pref_library_tabs_all" to JsonPrimitive("Podcasts,Albums,,Songs,Albums"),
                "pref_library_tabs_enabled" to JsonPrimitive("")
            )
        )

        store.getString("pref_library_tabs_all", null) shouldBe "Albums,Songs"
        store.contains("pref_library_tabs_enabled") shouldBe false
        GeneralPreferenceManager(store).allLibraryTabs.first() shouldBe LibraryTab.Albums
        GeneralPreferenceManager(store).enabledLibraryTabs shouldBe LibraryTab.defaultEnabled
    }

    @Test
    fun `restoring replaces preferences, resets those the backup lacks, and ignores unknown or mistyped keys`() {
        val store = InMemoryKeyValueStore()
        store.edit {
            putBoolean("pref_theme_extra_dark", true)
            putString("jellyfin_access_token", "keep me")
        }

        BackedUpSettings.restore(
            store,
            mapOf(
                "equalizer_enabled" to JsonPrimitive(true),
                "replaygain_mode" to JsonPrimitive("not a number"),
                "jellyfin_access_token" to JsonPrimitive("overwritten"),
                "some_future_setting" to JsonPrimitive(1)
            )
        )

        EqualizerSettings.Enabled.read(store) shouldBe true
        AppearanceSettings.PureBlack.read(store) shouldBe false
        store.getString("jellyfin_access_token", null) shouldBe "keep me"
        store.contains("some_future_setting") shouldBe false
        store.contains("replaygain_mode") shouldBe false
    }

    @Test
    fun `a version 1 backup without settings still decodes`() {
        val old = """{"schemaVersion":1,"exportedAt":5,"songs":[],"playlists":[],"somethingNew":true}"""

        val backup = json.decodeFromString<LibraryBackup>(old)

        backup.settings shouldBe null
        backup.schemaVersion shouldBe 1
    }

    @Test
    fun `the current version carries its settings through json`() {
        val backup = LibraryBackup(exportedAt = 5, songs = emptyList(), playlists = emptyList(), settings = mapOf("equalizer_enabled" to JsonPrimitive(true)))

        val decoded = json.decodeFromString<LibraryBackup>(json.encodeToString(LibraryBackup.serializer(), backup))

        decoded.schemaVersion shouldBe LibraryBackup.CURRENT_SCHEMA_VERSION
        decoded.settings shouldBe backup.settings
    }
}
