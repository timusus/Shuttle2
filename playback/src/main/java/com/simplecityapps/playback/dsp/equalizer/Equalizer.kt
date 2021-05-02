package com.simplecityapps.playback.dsp.equalizer

object Equalizer {

    object Presets {

        sealed class Preset(val name: String, val bands: List<NyquistBand>) {

            object Flat : Preset(
                "Flat", listOf(
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
                "Custom", listOf(
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
                "Bass Boost", listOf(
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
                "Bass Reduction", listOf(
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
                "Vocal Boost", listOf(
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
                "Vocal Reduction", listOf(
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