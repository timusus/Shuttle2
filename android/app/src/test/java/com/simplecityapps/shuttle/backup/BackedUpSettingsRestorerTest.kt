package com.simplecityapps.shuttle.backup

import com.simplecityapps.playback.dsp.equalizer.Equalizer
import com.simplecityapps.playback.equalizer.KeyValueEqualizerPresetStore
import com.simplecityapps.playback.exoplayer.EqualizerAudioProcessor
import com.simplecityapps.playback.exoplayer.followStoredSettings
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.persistence.KeyValueStore
import com.simplecityapps.shuttle.settings.AppearanceSettings
import com.simplecityapps.shuttle.settings.EqualizerSettings
import com.simplecityapps.shuttle.settings.Setting
import com.simplecityapps.shuttle.settings.SettingsStore
import com.simplecityapps.shuttle.settings.ThemeMode
import com.simplecityapps.shuttle.ui.screens.settings.ShareDebugLogsResult
import com.simplecityapps.shuttle.ui.screens.settings.SettingsEffects
import com.simplecityapps.shuttle.ui.screens.settings.backup.LibraryBackup
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test

/** Restoring a backup's preferences: written off the main thread, their effects run on it, and the equalizer follows. */
class BackedUpSettingsRestorerTest {
    /** Runs on [delegate], noting whether a block of its is running. */
    private class TrackingDispatcher(private val delegate: CoroutineDispatcher) : CoroutineDispatcher() {
        var running = false
            private set

        override fun dispatch(
            context: CoroutineContext,
            block: Runnable
        ) = delegate.dispatch(context) {
            running = true
            try {
                block.run()
            } finally {
                running = false
            }
        }
    }

    private class RecordingEffects(private val onMain: () -> Boolean) : SettingsEffects {
        val changed = mutableListOf<Pair<Setting<*>, Any?>>()
        val offMain = mutableListOf<Setting<*>>()

        override fun <T> onSettingChanged(
            setting: Setting<T>,
            value: T
        ) {
            changed += setting to value
            if (!onMain()) offMain += setting
        }

        override fun rescan() = Unit

        override suspend fun clearArtworkCache() = Unit

        override fun downloadAllArtwork() = Unit

        override suspend fun shareDebugLogs() = ShareDebugLogsResult.Empty
    }

    private val store = InMemoryKeyValueStore()

    private class Fixture(
        val restorer: BackedUpSettingsRestorer,
        val effects: RecordingEffects,
        val writesOffIo: List<Boolean>
    )

    private fun TestScope.fixture(): Fixture {
        val io = TrackingDispatcher(StandardTestDispatcher(testScheduler))
        val main = TrackingDispatcher(StandardTestDispatcher(testScheduler))
        val writesOffIo = mutableListOf<Boolean>()
        val trackedStore = object : KeyValueStore by store {
            override fun edit(block: KeyValueStore.Editor.() -> Unit) {
                writesOffIo += !io.running
                store.edit(block)
            }
        }
        val effects = RecordingEffects(onMain = { main.running })
        return Fixture(BackedUpSettingsRestorer(trackedStore, effects, io, main), effects, writesOffIo)
    }

    private fun backup(vararg settings: Pair<String, JsonPrimitive>) = LibraryBackup(exportedAt = 5, songs = emptyList(), playlists = emptyList(), settings = settings.toMap())

    @Test
    fun `preferences are written on the io dispatcher and a changed setting's effect runs on the main one`() = runTest {
        val fixture = fixture()

        fixture.restorer.restore(backup(AppearanceSettings.Theme.key to JsonPrimitive(ThemeMode.Dark.ordinal.toString()))) shouldBe 1

        AppearanceSettings.Theme.read(store) shouldBe ThemeMode.Dark
        fixture.writesOffIo shouldBe listOf(false)
        fixture.effects.changed shouldContain (AppearanceSettings.Theme to ThemeMode.Dark)
        fixture.effects.offMain.shouldBeEmpty()
    }

    @Test
    fun `a setting the backup leaves as it was has no effect run`() = runTest {
        store.edit { AppearanceSettings.PureBlack.write(this, true) }
        val fixture = fixture()

        fixture.restorer.restore(backup(AppearanceSettings.PureBlack.key to JsonPrimitive(true)))

        fixture.effects.changed.map { it.first } shouldBe emptyList()
    }

    @Test
    fun `a version 1 backup leaves the preferences alone`() = runTest {
        store.edit {
            AppearanceSettings.PureBlack.write(this, true)
            putString("pref_library_tabs_enabled", "Songs")
        }
        val fixture = fixture()
        val v1 = Json { ignoreUnknownKeys = true }.decodeFromString<LibraryBackup>("""{"schemaVersion":1,"exportedAt":5,"songs":[],"playlists":[]}""")

        fixture.restorer.restore(v1) shouldBe 0

        AppearanceSettings.PureBlack.read(store) shouldBe true
        store.getString("pref_library_tabs_enabled", null) shouldBe "Songs"
        fixture.writesOffIo.shouldBeEmpty()
        fixture.effects.changed.shouldBeEmpty()
    }

    @Test
    fun `a restored equalizer reaches the running processor`() = runTest {
        val presetStore = KeyValueEqualizerPresetStore(store)
        val equalizer = EqualizerAudioProcessor(enabled = false).apply {
            followStoredSettings(EqualizerSettings(SettingsStore(store)), presetStore, store, backgroundScope)
        }
        val fixture = fixture()

        fixture.restorer.restore(
            backup(
                EqualizerSettings.Enabled.key to JsonPrimitive(true),
                EqualizerSettings.PreampGain.key to JsonPrimitive(-4f),
                KeyValueEqualizerPresetStore.PresetKey to JsonPrimitive(Equalizer.Presets.bassBoost.name)
            )
        )
        testScheduler.advanceUntilIdle()

        equalizer.enabled shouldBe true
        equalizer.preampGainDb shouldBe -4f
        equalizer.preset shouldBe Equalizer.Presets.bassBoost
    }
}
