package com.simplecityapps.playback.settings

import com.simplecityapps.playback.dsp.replaygain.ReplayGainMode
import com.simplecityapps.shuttle.settings.Setting
import com.simplecityapps.shuttle.settings.SettingsStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/** Settings > Playback & sound, read by Android and iOS alike. The equalizer's own settings are [com.simplecityapps.shuttle.settings.EqualizerSettings]. */
@SingleIn(AppScope::class)
class PlaybackSettings @Inject constructor(
    store: SettingsStore
) {
    val retainShuffleOnNewQueue = store.preference(RetainShuffleOnNewQueue)
    val usbDacDirectOutput = store.preference(UsbDacDirectOutput)
    val replayGainMode = store.preference(ReplayGain)
    val preAmpGain = store.preference(PreAmpGain)
    val playbackSpeed = store.preference(PlaybackSpeed)
    val crossfadeDurationMs = store.preference(CrossfadeDuration)

    companion object {
        /** Starting a new queue keeps shuffle on instead of turning it off. */
        val RetainShuffleOnNewQueue = Setting.boolean("pref_retain_shuffle_on_new_queue", false)

        /** Direct output to USB DACs through a bit-perfect mixer (Android 14+). */
        val UsbDacDirectOutput = Setting.boolean("pref_bit_perfect_usb", false)

        val ReplayGain = Setting.enumOrdinalInt("replaygain_mode", ReplayGainMode.Off, ReplayGainMode.entries)

        /** In dB, within ±[com.simplecityapps.playback.dsp.replaygain.MAX_REPLAY_GAIN_PREAMP_DB]. */
        val PreAmpGain = Setting.float("preamp_gain", 0f)

        /** The speed chosen in Now Playing, a multiplier; the player owns it, this keeps it across restarts. */
        val PlaybackSpeed = Setting.float("playback_speed", 1f)

        /**
         * How long one song fades into the next, in ms; 0 is off. A proof of concept with no UI yet (#97): a change
         * applies to items prepared after it, see docs/architecture/crossfade.md.
         */
        val CrossfadeDuration = Setting.int("crossfade_duration_ms", 0)
    }
}
