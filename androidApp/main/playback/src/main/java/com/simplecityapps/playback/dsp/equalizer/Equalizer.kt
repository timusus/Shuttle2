package com.simplecityapps.playback.dsp.equalizer

import androidx.annotation.StringRes
import com.simplecityapps.playback.R

object Equalizer {

    object Presets {

        sealed class Preset(val name: String, @StringRes val nameResId: Int, val bands: List<NyquistBand>) {

            object Flat : Preset(
                "Flat",
                R.string.eq_preset_flat,
                listOf(
                    EqualizerBand(32, 0.0).toNyquistBand(),
                    EqualizerBand(63, 0.0).toNyquistBand(),
                    EqualizerBand(125, 0.0).toNyquistBand(),
                    EqualizerBand(250, 0.0).toNyquistBand(),
                    EqualizerBand(500, 0.0).toNyquistBand(),
                    EqualizerBand(1000, 0.0).toNyquistBand(),
                    EqualizerBand(2000, 0.0).toNyquistBand(),
                    EqualizerBand(4000, 0.0).toNyquistBand(),
                    EqualizerBand(8000, 0.0).toNyquistBand(),
                    EqualizerBand(16000, 0.0).toNyquistBand()
                )
            )

            object Custom : Preset(
                "Custom",
                R.string.eq_preset_custom,
                listOf(
                    EqualizerBand(32, 0.0).toNyquistBand(),
                    EqualizerBand(63, 0.0).toNyquistBand(),
                    EqualizerBand(125, 0.0).toNyquistBand(),
                    EqualizerBand(250, 0.0).toNyquistBand(),
                    EqualizerBand(500, 0.0).toNyquistBand(),
                    EqualizerBand(1000, 0.0).toNyquistBand(),
                    EqualizerBand(2000, 0.0).toNyquistBand(),
                    EqualizerBand(4000, 0.0).toNyquistBand(),
                    EqualizerBand(8000, 0.0).toNyquistBand(),
                    EqualizerBand(16000, 0.0).toNyquistBand()
                )
            )

            object BassBoost : Preset(
                "Bass Boost",
                R.string.eq_preset_bass_boost,
                listOf(
                    EqualizerBand(32, 6.0).toNyquistBand(),
                    EqualizerBand(63, 5.0).toNyquistBand(),
                    EqualizerBand(125, 4.0).toNyquistBand(),
                    EqualizerBand(250, 3.0).toNyquistBand(),
                    EqualizerBand(500, 2.0).toNyquistBand(),
                    EqualizerBand(1000, 0.0).toNyquistBand(),
                    EqualizerBand(2000, 0.0).toNyquistBand(),
                    EqualizerBand(4000, 0.0).toNyquistBand(),
                    EqualizerBand(8000, 0.0).toNyquistBand(),
                    EqualizerBand(16000, 0.0).toNyquistBand()
                )
            )

            object BassReducer : Preset(
                "Bass Reduction",
                R.string.eq_preset_bass_reduce,
                listOf(
                    EqualizerBand(32, -6.0).toNyquistBand(),
                    EqualizerBand(63, -5.0).toNyquistBand(),
                    EqualizerBand(125, -4.0).toNyquistBand(),
                    EqualizerBand(250, -3.0).toNyquistBand(),
                    EqualizerBand(500, -2.0).toNyquistBand(),
                    EqualizerBand(1000, 0.0).toNyquistBand(),
                    EqualizerBand(2000, 0.0).toNyquistBand(),
                    EqualizerBand(4000, 0.0).toNyquistBand(),
                    EqualizerBand(8000, 0.0).toNyquistBand(),
                    EqualizerBand(16000, 0.0).toNyquistBand()
                )
            )

            object VocalBoost : Preset(
                "Vocal Boost",
                R.string.eq_preset_vocal_boost,
                listOf(
                    EqualizerBand(32, -2.0).toNyquistBand(),
                    EqualizerBand(63, -3.0).toNyquistBand(),
                    EqualizerBand(125, -3.0).toNyquistBand(),
                    EqualizerBand(250, 2.0).toNyquistBand(),
                    EqualizerBand(500, 5.0).toNyquistBand(),
                    EqualizerBand(1000, 5.0).toNyquistBand(),
                    EqualizerBand(2000, 4.0).toNyquistBand(),
                    EqualizerBand(4000, 3.0).toNyquistBand(),
                    EqualizerBand(8000, 0.0).toNyquistBand(),
                    EqualizerBand(16000, -2.0).toNyquistBand()
                )
            )

            object VocalReducer : Preset(
                "Vocal Reduction",
                R.string.eq_preset_vocal_Reduce,
                listOf(
                    EqualizerBand(32, 2.0).toNyquistBand(),
                    EqualizerBand(63, 3.0).toNyquistBand(),
                    EqualizerBand(125, 3.0).toNyquistBand(),
                    EqualizerBand(250, -2.0).toNyquistBand(),
                    EqualizerBand(500, -5.0).toNyquistBand(),
                    EqualizerBand(1000, -5.0).toNyquistBand(),
                    EqualizerBand(2000, -4.0).toNyquistBand(),
                    EqualizerBand(4000, -3.0).toNyquistBand(),
                    EqualizerBand(8000, -0.0).toNyquistBand(),
                    EqualizerBand(16000, 2.0).toNyquistBand()
                )
            )

            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as Preset

                if (name != other.name) return false

                return true
            }

            override fun hashCode(): Int {
                return name.hashCode()
            }
        }

        val flat = Preset.Flat
        val custom = Preset.Custom
        val bassBoost = Preset.BassBoost
        val bassReducer = Preset.BassReducer
        val vocalBoost = Preset.VocalBoost
        val vocalReducer = Preset.VocalReducer

        val all = listOf(custom, flat, bassBoost, bassReducer, vocalBoost, vocalReducer)
    }
}
