package com.simplecityapps.shuttle.backup

import com.simplecityapps.playback.settings.PlaybackSettings
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.settings.Accent
import com.simplecityapps.shuttle.settings.AppearanceSettings
import com.simplecityapps.shuttle.settings.EqualizerSettings
import com.simplecityapps.shuttle.settings.ThemeMode
import com.simplecityapps.shuttle.ui.screens.settings.backup.LibraryBackup
import io.kotest.matchers.maps.shouldContainKey
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
        val store = InMemoryKeyValueStore()
        store.edit {
            putBoolean("pref_theme_extra_dark", true)
            putString("jellyfin_access_token", "secret")
            putString("plex_token", "secret")
            putString("client_id", "abc")
            putString("pref_scanner_included_folders", "content://tree")
            putBoolean("pref_crash_reporting", false)
            putBoolean("pref_analytics_consent_asked", true)
            putBoolean("source_setup_completed", true)
            putString("queue_ids", "1,2,3")
            putString("search_recent", "abba")
            putLong("app_purchased_date", 1L)
        }

        val exported = BackedUpSettings.export(store)

        exported shouldContainKey "pref_theme_extra_dark"
        exported.keys shouldBe setOf("pref_theme_extra_dark")
        exported shouldNotContainKey "client_id"
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
