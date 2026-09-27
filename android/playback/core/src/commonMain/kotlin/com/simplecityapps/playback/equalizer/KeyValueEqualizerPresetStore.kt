package com.simplecityapps.playback.equalizer

import com.simplecityapps.playback.dsp.equalizer.Equalizer
import com.simplecityapps.playback.dsp.equalizer.EqualizerBand
import com.simplecityapps.shuttle.persistence.KeyValueStore
import com.simplecityapps.shuttle.persistence.putString
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
            store.putString("preset_name", value.name)
        }
        get() {
            val name = store.getString("preset_name", Equalizer.Presets.custom.name)!!
            return Equalizer.Presets.all.firstOrNull { preset -> preset.name == name } ?: Equalizer.Presets.custom
        }

    override var customPresetBands: List<EqualizerBand>?
        set(value) {
            store.putString("custom_preset_bands", json.encodeToString(equalizerBandsSerializer, value))
        }
        get() {
            return store.getString("custom_preset_bands", null)?.let { bands ->
                json.decodeFromString(equalizerBandsSerializer, bands)
            }
        }

    private companion object {
        /** Reads and writes the JSON Moshi wrote before (#584): nulls left out, unknown fields ignored. */
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = true
        }

        val equalizerBandsSerializer = ListSerializer(EqualizerBand.serializer()).nullable
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
