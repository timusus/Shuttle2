package com.simplecityapps.playback.dsp.equalizer

object Equalizer {

    object Presets {

        sealed class Preset(val name: String, val bands: List<EqualizerBand>) {

            object Flat : Preset(
                "Flat", listOf(
                    EqualizerBand(32, 0),
                    EqualizerBand(63, 0),
                    EqualizerBand(125, 0),
                    EqualizerBand(250, 0),
                    EqualizerBand(500, 0),
                    EqualizerBand(1000, 0),
                    EqualizerBand(2000, 0),
                    EqualizerBand(4000, 0),
                    EqualizerBand(8000, 0),
                    EqualizerBand(16000, 0)
                )
            )

            object Custom : Preset(
                "Custom", listOf(
                    EqualizerBand(32, 0),
                    EqualizerBand(63, 0),
                    EqualizerBand(125, 0),
                    EqualizerBand(250, 0),
                    EqualizerBand(500, 0),
                    EqualizerBand(1000, 0),
                    EqualizerBand(2000, 0),
                    EqualizerBand(4000, 0),
                    EqualizerBand(8000, 0),
                    EqualizerBand(16000, 0)
                )
            )

            object BassBoost : Preset(
                "Bass Boost", listOf(
                    EqualizerBand(32, 6),
                    EqualizerBand(63, 5),
                    EqualizerBand(125, 4),
                    EqualizerBand(250, 3),
                    EqualizerBand(500, 2),
                    EqualizerBand(1000, 0),
                    EqualizerBand(2000, 0),
                    EqualizerBand(4000, 0),
                    EqualizerBand(8000, 0),
                    EqualizerBand(16000, 0)
                )
            )

            object BassReducer : Preset(
                "Bass Reduction", listOf(
                    EqualizerBand(32, -6),
                    EqualizerBand(63, -5),
                    EqualizerBand(125, -4),
                    EqualizerBand(250, -3),
                    EqualizerBand(500, -2),
                    EqualizerBand(1000, 0),
                    EqualizerBand(2000, 0),
                    EqualizerBand(4000, 0),
                    EqualizerBand(8000, 0),
                    EqualizerBand(16000, 0)
                )
            )

            object VocalBoost : Preset(
                "Vocal Boost", listOf(
                    EqualizerBand(32, -2),
                    EqualizerBand(63, -3),
                    EqualizerBand(125, -3),
                    EqualizerBand(250, 2),
                    EqualizerBand(500, 5),
                    EqualizerBand(1000, 5),
                    EqualizerBand(2000, 4),
                    EqualizerBand(4000, 3),
                    EqualizerBand(8000, 0),
                    EqualizerBand(16000, -2)
                )
            )

            object VocalReducer : Preset(
                "Vocal Reduction", listOf(
                    EqualizerBand(32, 2),
                    EqualizerBand(63, 3),
                    EqualizerBand(125, 3),
                    EqualizerBand(250, -2),
                    EqualizerBand(500, -5),
                    EqualizerBand(1000, -5),
                    EqualizerBand(2000, -4),
                    EqualizerBand(4000, -3),
                    EqualizerBand(8000, -0),
                    EqualizerBand(16000, 2)
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