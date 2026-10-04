package com.simplecityapps.playback.equalizer

import com.simplecityapps.playback.dsp.equalizer.Equalizer
import com.simplecityapps.playback.dsp.equalizer.EqualizerBand
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import io.kotest.matchers.shouldBe
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class KeyValueEqualizerPresetStoreTest {
    private val keyValues = InMemoryKeyValueStore()
    private val store = KeyValueEqualizerPresetStore(keyValues)

    private val savedCustomGains = Equalizer.Presets.custom.bands.map { it.gain }

    @AfterTest
    fun restoreTheCustomPreset() {
        Equalizer.Presets.custom.bands.forEachIndexed { index, band -> band.gain = savedCustomGains[index] }
    }

    @Test
    fun nothingSavedIsTheCustomPresetWithNoBands() {
        store.preset shouldBe Equalizer.Presets.custom
        store.customPresetBands shouldBe null
    }

    @Test
    fun thePresetIsSavedByName() {
        store.preset = Equalizer.Presets.bassBoost

        keyValues.getString("preset_name", null) shouldBe "Bass Boost"
        KeyValueEqualizerPresetStore(keyValues).preset shouldBe Equalizer.Presets.bassBoost
    }

    @Test
    fun observingThePresetEmitsItNowAndAgainWhenItChanges() = runTest {
        val seen = mutableListOf<Equalizer.Presets.Preset>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { store.observePreset().toList(seen) }

        store.preset = Equalizer.Presets.bassBoost
        job.cancel()

        seen shouldBe listOf(Equalizer.Presets.custom, Equalizer.Presets.bassBoost)
    }

    @Test
    fun anUnknownPresetNameIsTheCustomPreset() {
        keyValues.edit { putString("preset_name", "Gone") }

        store.preset shouldBe Equalizer.Presets.custom
    }

    @Test
    fun theCustomBandsRoundTripAsTheJsonAndroidAlwaysWrote() {
        keyValues.edit { putString("custom_preset_bands", """[{"centerFrequency":32,"gain":3.5},{"centerFrequency":64,"gain":-2.0,"extra":1}]""") }

        store.customPresetBands!!.map { it.centerFrequency to it.gain } shouldBe listOf(32 to 3.5, 64 to -2.0)

        store.customPresetBands = listOf(EqualizerBand(125, 1.0))
        keyValues.getString("custom_preset_bands", null) shouldBe """[{"centerFrequency":125,"gain":1.0}]"""
    }

    @Test
    fun restoringCopiesTheSavedBandsIntoTheCustomPresetFirst() {
        store.customPresetBands = listOf(EqualizerBand(32, 4.0), EqualizerBand(16_000, -3.0))
        store.preset = Equalizer.Presets.custom

        store.restorePreset() shouldBe Equalizer.Presets.custom
        Equalizer.Presets.custom.bands.first { it.centerFrequency == 32 }.gain shouldBe 4.0
        Equalizer.Presets.custom.bands.first { it.centerFrequency == 16_000 }.gain shouldBe -3.0
    }
}
