package com.simplecityapps.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager

/**
 * Manages bit-perfect audio playback for USB DACs on Android 14+
 *
 * Bit-perfect playback bypasses Android's audio mixer (AudioFlinger) to send
 * audio data directly to external USB DACs without resampling, mixing, or processing.
 * This preserves the original audio quality for high-resolution audio files.
 */
class BitPerfectManager(
    private val context: Context,
    private val playbackPreferenceManager: PlaybackPreferenceManager,
    private val playbackWatcher: PlaybackWatcher
) : PlaybackWatcherCallback {

    companion object {
        private const val TAG = "BitPerfectManager"
    }

    private val audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var isActive = false
    private var currentUsbDevice: AudioDeviceInfo? = null

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    Log.d(TAG, "USB device attached")
                    checkAndConfigureBitPerfect()
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    Log.d(TAG, "USB device detached")
                    disableBitPerfect()
                }
                AudioManager.ACTION_HEADSET_PLUG -> {
                    // Also monitor headset plug events which can indicate USB DAC connection
                    val state = intent.getIntExtra("state", -1)
                    if (state == 1) {
                        Log.d(TAG, "Headset plugged in")
                        checkAndConfigureBitPerfect()
                    } else if (state == 0) {
                        Log.d(TAG, "Headset unplugged")
                        disableBitPerfect()
                    }
                }
            }
        }
    }

    init {
        playbackWatcher.addCallback(this)
        registerReceivers()

        // Check if a USB DAC is already connected
        checkAndConfigureBitPerfect()
    }

    private fun registerReceivers() {
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(AudioManager.ACTION_HEADSET_PLUG)
        }
        context.registerReceiver(usbReceiver, filter)
    }

    fun cleanup() {
        context.safelyUnregisterReceiver(usbReceiver)
        disableBitPerfect()
    }

    private fun checkAndConfigureBitPerfect() {
        if (!playbackPreferenceManager.bitPerfectEnabled) {
            Log.d(TAG, "Bit-perfect mode disabled in preferences")
            return
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Log.d(TAG, "Bit-perfect mode requires Android 14+")
            return
        }

        findUsbAudioDevice()?.let { device ->
            configureBitPerfect(device)
        } ?: run {
            Log.d(TAG, "No USB audio device found")
            disableBitPerfect()
        }
    }

    private fun findUsbAudioDevice(): AudioDeviceInfo? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return null
        }

        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return devices.firstOrNull { device ->
            device.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                device.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun configureBitPerfect(device: AudioDeviceInfo) {
        try {
            Log.d(TAG, "Configuring bit-perfect mode for device: ${device.productName}")

            // Query supported mixer attributes for the USB device
            val supportedAttributes = audioManager.getSupportedMixerAttributes(device)

            if (supportedAttributes.isEmpty()) {
                Log.w(TAG, "No supported mixer attributes for this device")
                disableBitPerfect()
                return
            }

            // Find a bit-perfect compatible configuration
            // Prefer high sample rates and bit depths
            val bitPerfectAttribute = supportedAttributes
                .filter { it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT }
                .maxByOrNull { attr ->
                    // Score by sample rate * channel count
                    (attr.format.sampleRate ?: 0) * (attr.format.channelCount ?: 0)
                }

            if (bitPerfectAttribute == null) {
                Log.w(TAG, "Device doesn't support bit-perfect mode")
                disableBitPerfect()
                return
            }

            // Create AudioAttributes for media playback
            val audioAttributes = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            // Set the preferred mixer attributes
            val result = audioManager.setPreferredMixerAttributes(
                audioAttributes,
                device,
                bitPerfectAttribute
            )

            if (result == AudioManager.SUCCESS) {
                isActive = true
                currentUsbDevice = device
                Log.i(
                    TAG,
                    "Bit-perfect mode activated: ${bitPerfectAttribute.format.sampleRate}Hz, " +
                        "${bitPerfectAttribute.format.channelCount}ch, " +
                        "encoding=${bitPerfectAttribute.format.encoding}"
                )

                // Notify listeners that bit-perfect mode is active
                notifyBitPerfectStateChanged(true, device)
            } else {
                Log.e(TAG, "Failed to set preferred mixer attributes")
                disableBitPerfect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring bit-perfect mode", e)
            disableBitPerfect()
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun disableBitPerfect() {
        if (!isActive) {
            return
        }

        try {
            val audioAttributes = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            currentUsbDevice?.let { device ->
                audioManager.clearPreferredMixerAttributes(audioAttributes, device)
                Log.i(TAG, "Bit-perfect mode deactivated")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing preferred mixer attributes", e)
        } finally {
            isActive = false
            currentUsbDevice = null
            notifyBitPerfectStateChanged(false, null)
        }
    }

    private fun notifyBitPerfectStateChanged(active: Boolean, device: AudioDeviceInfo?) {
        // TODO: Implement notification to UI layer
        // This could broadcast an intent or use a callback mechanism
        // to notify the UI that bit-perfect mode is active/inactive
        Log.d(TAG, "Bit-perfect state changed: active=$active, device=${device?.productName}")
    }

    fun isActive(): Boolean = isActive

    fun getCurrentDevice(): AudioDeviceInfo? = currentUsbDevice

    // PlaybackWatcherCallback Implementation

    override fun onPlaybackStateChanged(playbackState: PlaybackState) {
        // When playback starts, re-check and configure if needed
        when (playbackState) {
            is PlaybackState.Playing -> {
                if (!isActive && playbackPreferenceManager.bitPerfectEnabled) {
                    checkAndConfigureBitPerfect()
                }
            }
            else -> {
                // Keep bit-perfect active even when paused, so the configuration
                // is ready when playback resumes
            }
        }
    }

    override fun onPlaybackEnded() {
        // Don't disable bit-perfect when a track ends, keep it active for the next track
    }
}
