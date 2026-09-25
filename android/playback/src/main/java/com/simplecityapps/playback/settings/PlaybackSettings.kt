package com.simplecityapps.playback.settings

import com.simplecityapps.playback.dsp.replaygain.ReplayGainMode
import com.simplecityapps.shuttle.settings.Setting
import com.simplecityapps.shuttle.settings.SettingsStore
import javax.inject.Inject
import javax.inject.Singleton

/** Settings > Playback & sound. The equalizer's preset and band gains are editor state, kept in PlaybackPreferenceManager. */
@Singleton
class PlaybackSettings @Inject constructor(
    store: SettingsStore
) {
    val retainShuffleOnNewQueue = store.preference(RetainShuffleOnNewQueue)
    val usbDacDirectOutput = store.preference(UsbDacDirectOutput)
    val equalizerEnabled = store.preference(EqualizerEnabled)
    val replayGainMode = store.preference(ReplayGain)
    val preAmpGain = store.preference(PreAmpGain)
    val playbackSpeed = store.preference(PlaybackSpeed)

    companion object {
        /** Starting a new queue keeps shuffle on instead of turning it off. */
        val RetainShuffleOnNewQueue = Setting.boolean("pref_retain_shuffle_on_new_queue", false)

        /** Direct output to USB DACs through a bit-perfect mixer (Android 14+). */
        val UsbDacDirectOutput = Setting.boolean("pref_bit_perfect_usb", false)

        val EqualizerEnabled = Setting.boolean("equalizer_enabled", false)

        val ReplayGain = Setting.enumOrdinalInt("replaygain_mode", ReplayGainMode.Off, ReplayGainMode.entries)

        /** In dB, within ±[com.simplecityapps.playback.dsp.replaygain.ReplayGainAudioProcessor.maxPreAmpGain]. */
        val PreAmpGain = Setting.float("preamp_gain", 0f)

        /** The speed chosen in Now Playing, a multiplier; the player owns it, this keeps it across restarts. */
        val PlaybackSpeed = Setting.float("playback_speed", 1f)
    }
}
