package com.simplecityapps.playback

import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import com.simplecityapps.playback.dsp.replaygain.ReplayGainAudioProcessor
import com.simplecityapps.playback.exoplayer.AudioTrackMonitor
import com.simplecityapps.playback.exoplayer.EqualizerAudioProcessor
import com.simplecityapps.playback.settings.PlaybackSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Opt-in direct output to USB DACs on Android 14+ (API 34): while the preference is on and a USB audio device is
 * connected, asks the platform to open that device's output with a bit-perfect mixer in the format the player
 * writes, so the stream reaches the DAC at its own sample rate without being resampled or mixed with other
 * sounds, and bypasses the equalizer and ReplayGain so they don't change it either.
 *
 * The player's output is 16-bit PCM at the song's sample rate and channel count: the audio sink converts
 * higher bit depths to 16-bit after the app's processors, so a 24-bit song is not carried bit for bit.
 *
 * Follows the format of the AudioTrack the player actually opened, from [AudioTrackMonitor], and when the
 * preferred mixer changes has the player open a new AudioTrack, since one keeps the output it was opened on.
 * Clears the preference when it's turned off, the device goes away, or the device offers no bit-perfect mixer
 * in the player's format (it then plays through the normal mixer). A no-op below API 34, while the preference
 * is off, and without a USB device.
 */
class BitPerfectOutput(
    private val audioManager: AudioManager?,
    private val playbackSettings: PlaybackSettings,
    private val audioTrackMonitor: AudioTrackMonitor,
    private val equalizerAudioProcessor: EqualizerAudioProcessor,
    private val replayGainAudioProcessor: ReplayGainAudioProcessor,
    appCoroutineScope: CoroutineScope
) {
    /** The device and mixer attributes last set, so they can be cleared. Only touched on the main thread. */
    private var applied: Pair<AudioDeviceInfo, AudioMixerAttributes>? = null

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && audioManager != null) {
            appCoroutineScope.launch(Dispatchers.Main.immediate) {
                targets(audioManager).collect { target -> apply(audioManager, target) }
            }
        }
    }

    /** The device and mixer attributes that should be preferred, or null for none. */
    @OptIn(ExperimentalCoroutinesApi::class)
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun targets(audioManager: AudioManager): Flow<Pair<AudioDeviceInfo, AudioMixerAttributes>?> = playbackSettings.usbDacDirectOutput.flow
        .distinctUntilChanged()
        .flatMapLatest { enabled ->
            if (!enabled) {
                flowOf(null)
            } else {
                combine(usbOutputDevices(audioManager), audioTrackMonitor.format) { devices, output ->
                    val device = devices.firstOrNull()
                    if (device == null || output == null) {
                        null
                    } else {
                        bitPerfectAttributes(audioManager, device, output)?.let { attributes -> device to attributes }
                    }
                }
            }
        }

    /**
     * Prefers [target]'s mixer, or none, bypasses the processors while one is in use, and has the player open a
     * new AudioTrack when that changes what it plays through.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun apply(
        audioManager: AudioManager,
        target: Pair<AudioDeviceInfo, AudioMixerAttributes>?
    ) {
        val current = applied
        if (current?.first?.id == target?.first?.id && current?.second == target?.second) return

        applied = target?.takeIf { (device, attributes) -> setPreferred(audioManager, device, attributes) }
        if (current != null && current.first.id != applied?.first?.id) {
            clearPreferred(audioManager, current.first)
        }

        val bypassed = applied != null
        equalizerAudioProcessor.bypassed = bypassed
        replayGainAudioProcessor.bypassed = bypassed
        if (applied != current) {
            audioTrackMonitor.reopenAudioTrack()
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun setPreferred(
        audioManager: AudioManager,
        device: AudioDeviceInfo,
        attributes: AudioMixerAttributes
    ): Boolean {
        val success =
            try {
                audioManager.setPreferredMixerAttributes(MEDIA_ATTRIBUTES, device, attributes)
            } catch (e: Exception) {
                Timber.e(e, "Failed to set preferred mixer attributes")
                false
            }
        if (success) {
            Timber.i("Bit-perfect output set for ${device.productName}: ${attributes.format}")
        } else {
            Timber.w("Bit-perfect output refused for ${device.productName}: ${attributes.format}")
        }
        return success
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun clearPreferred(
        audioManager: AudioManager,
        device: AudioDeviceInfo
    ) {
        try {
            audioManager.clearPreferredMixerAttributes(MEDIA_ATTRIBUTES, device)
            Timber.i("Bit-perfect output cleared for ${device.productName}")
        } catch (e: Exception) {
            // The device may already be gone, which clears it anyway
            Timber.w(e, "Failed to clear preferred mixer attributes")
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun bitPerfectAttributes(
        audioManager: AudioManager,
        device: AudioDeviceInfo,
        output: OutputFormat
    ): AudioMixerAttributes? {
        val supported = audioManager.getSupportedMixerAttributes(device)
        val formats = supported.map { attributes -> attributes.toMixerFormat() }
        val selected = selectBitPerfectFormat(formats, output)
        if (selected == null) {
            Timber.i("${device.productName} offers no bit-perfect mixer for $output")
            return null
        }
        return supported[formats.indexOf(selected)]
    }

    /** The connected USB audio outputs, updated as devices come and go. */
    private fun usbOutputDevices(audioManager: AudioManager): Flow<List<AudioDeviceInfo>> = callbackFlow {
        fun publish() {
            trySend(audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter { device -> device.type in USB_DEVICE_TYPES })
        }
        val callback =
            object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = publish()

                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = publish()
            }
        // Also reports the devices already connected
        audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        publish()
        awaitClose { audioManager.unregisterAudioDeviceCallback(callback) }
    }.distinctUntilChangedBy { devices -> devices.map { device -> device.id } }

    private companion object {
        val USB_DEVICE_TYPES = setOf(AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET)

        /** Only USAGE_MEDIA is supported, and it's what the player plays with. */
        val MEDIA_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()

        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        fun AudioMixerAttributes.toMixerFormat() = MixerFormat(
            sampleRate = format.sampleRate,
            channelCount = format.channelCount,
            encoding = format.encoding,
            isBitPerfect = mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT
        )
    }
}
