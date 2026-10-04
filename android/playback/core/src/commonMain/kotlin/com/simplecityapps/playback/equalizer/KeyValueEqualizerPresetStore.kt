package com.simplecityapps.playback.equalizer

import com.simplecityapps.playback.dsp.equalizer.Equalizer
import com.simplecityapps.playback.dsp.equalizer.EqualizerBand
import com.simplecityapps.shuttle.persistence.KeyValueStore
import com.simplecityapps.shuttle.persistence.putString
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.json.Json

/**
 * The equalizer's preset and the Custom preset's band gains in a [KeyValueStore], under the keys Android has always
 * used (`preset_name`, `custom_preset_bands`): Android's `PlaybackPreferenceManager` delegates to it, and iOS binds it.
 */
class KeyValueEqualizerPresetStore(
    private val store: KeyValueStore
) : EqualizerPresetStore {
    override var preset: Equalizer.Presets.Preset
        set(value) {
            store.putString(PresetKey, value.name)
        }
        get() {
            val name = store.getString(PresetKey, Equalizer.Presets.custom.name)!!
            return Equalizer.Presets.all.firstOrNull { preset -> preset.name == name } ?: Equalizer.Presets.custom
        }

    override var customPresetBands: List<EqualizerBand>?
        set(value) {
            store.putString(CustomPresetBandsKey, json.encodeToString(equalizerBandsSerializer, value))
        }
        get() {
            return store.getString(CustomPresetBandsKey, null)?.let { bands ->
                json.decodeFromString(equalizerBandsSerializer, bands)
            }
        }

    override fun observePreset(): Flow<Equalizer.Presets.Preset> = store.changes(PresetKey).map { preset }.distinctUntilChanged()

    companion object {
        const val PresetKey = "preset_name"
        const val CustomPresetBandsKey = "custom_preset_bands"

        /** Reads and writes the JSON Moshi wrote before (#584): nulls left out, unknown fields ignored. */
        private val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = true
        }

        private val equalizerBandsSerializer = ListSerializer(EqualizerBand.serializer()).nullable
    }
}

/**
 * The equalizer's saved preset, as it starts up: the Custom preset's saved band gains are copied into
 * [Equalizer.Presets.custom] first, since setting a preset captures its gains.
 */
fun EqualizerPresetStore.restorePreset(): Equalizer.Presets.Preset {
    customPresetBands?.forEach { restoredBand ->
        Equalizer.Presets.custom.bands.forEach { customBand ->
            if (customBand.centerFrequency == restoredBand.centerFrequency) {
                customBand.gain = restoredBand.gain
            }
        }
    }
    return preset
}
