package com.simplecityapps.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.shuttle.model.Song
import java.io.File
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Opt-in bit-perfect output to USB DACs on Android 14+ (API 34): while the preference is on and a USB audio
 * device is connected, asks the platform to open that device's output with a bit-perfect mixer in the
 * current song's format, so it reaches the DAC without being resampled or mixed with other sounds.
 *
 * Follows the current song from [QueueOperations.queueStateFlow], so the mixer format changes with it, and
 * clears the preference when it's turned off, the device goes away, or the song's format isn't one the device
 * offers bit-perfect (it then plays through the normal mixer). A no-op below API 34, while the preference is
 * off, and without a USB device. Remote songs are left alone, since a server may transcode them to a format
 * other than the one their metadata describes.
 */
class BitPerfectOutput(
    private val context: Context,
    private val audioManager: AudioManager?,
    private val playbackPreferenceManager: PlaybackPreferenceManager,
    private val queueManager: QueueOperations,
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
    private fun targets(audioManager: AudioManager): Flow<Pair<AudioDeviceInfo, AudioMixerAttributes>?> = playbackPreferenceManager.bitPerfectEnabledFlow()
        .distinctUntilChanged()
        .flatMapLatest { enabled ->
            if (!enabled) {
                flowOf(null)
            } else {
                combine(
                    usbOutputDevices(audioManager),
                    queueManager.queueStateFlow.map { queueState -> queueState.currentItem?.song }.distinctUntilChangedBy { song -> song?.id to song?.path }
                ) { devices, song -> devices.firstOrNull() to song }
                    .mapLatest { (device, song) ->
                        if (device == null || song == null || song.mediaProvider.remote) {
                            null
                        } else {
                            outputFormatOf(song)?.let { output -> bitPerfectAttributes(audioManager, device, output) }?.let { attributes -> device to attributes }
                        }
                    }
            }
        }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun apply(
        audioManager: AudioManager,
        target: Pair<AudioDeviceInfo, AudioMixerAttributes>?
    ) {
        val current = applied
        if (current?.first?.id == target?.first?.id && current?.second == target?.second) return

        if (current != null && current.first.id != target?.first?.id) {
            try {
                audioManager.clearPreferredMixerAttributes(MEDIA_ATTRIBUTES, current.first)
                Timber.i("Bit-perfect output cleared for ${current.first.productName}")
            } catch (e: Exception) {
                // The device may already be gone, which clears it anyway
                Timber.w(e, "Failed to clear preferred mixer attributes")
            }
        }
        applied = null

        target ?: return
        val (device, attributes) = target
        val success =
            try {
                audioManager.setPreferredMixerAttributes(MEDIA_ATTRIBUTES, device, attributes)
            } catch (e: Exception) {
                Timber.e(e, "Failed to set preferred mixer attributes")
                false
            }
        if (success) {
            applied = target
            Timber.i("Bit-perfect output set for ${device.productName}: ${attributes.format}")
        } else {
            Timber.w("Bit-perfect output refused for ${device.productName}: ${attributes.format}")
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

    /** The song's output format from its metadata, or read from the file when the library doesn't have it. */
    private suspend fun outputFormatOf(song: Song): OutputFormat? = OutputFormat.of(song.sampleRate, song.channelCount)
        ?: withContext(Dispatchers.IO) {
            val extractor = MediaExtractor()
            try {
                if (song.path.startsWith("/")) {
                    extractor.setDataSource(File(song.path).path)
                } else {
                    extractor.setDataSource(context, Uri.parse(song.path), null)
                }
                (0 until extractor.trackCount)
                    .map { index -> extractor.getTrackFormat(index) }
                    .firstOrNull { format -> format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                    ?.let { format -> OutputFormat.of(format.getInteger(MediaFormat.KEY_SAMPLE_RATE), format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)) }
            } catch (e: Exception) {
                Timber.w(e, "Failed to read the audio format of ${song.path}")
                null
            } finally {
                extractor.release()
            }
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
