package com.simplecityapps.playback.persistence

import android.content.SharedPreferences
import com.simplecityapps.mediaprovider.MediaProvider
import com.simplecityapps.playback.equalizer.Equalizer
import com.simplecityapps.playback.equalizer.EqualizerBand
import com.simplecityapps.playback.exoplayer.ReplayGainAudioProcessor
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.persistence.get
import com.simplecityapps.shuttle.persistence.put
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.lang.reflect.Type


class PlaybackPreferenceManager(
    private val sharedPreferences: SharedPreferences,
    private val moshi: Moshi
) {

    /**
     * A comma separated list of song ids
     */
    var queueIds: String?
        set(value) {
            sharedPreferences.put("queue_ids", value ?: "")
        }
        get() {
            val queueIds = sharedPreferences.get("queue_ids", "")
            return if (queueIds.isEmpty()) null else queueIds
        }

    /**
     * A comma separated list of song ids
     */
    var shuffleQueueIds: String?
        set(value) {
            sharedPreferences.put("shuffle_queue_ids", value ?: "")
        }
        get() {
            val queueIds = sharedPreferences.get("shuffle_queue_ids", "")
            return if (queueIds.isEmpty()) null else queueIds
        }

    var queuePosition: Int?
        set(value) {
            sharedPreferences.put("queue_position", value ?: -1)
        }
        get() {
            val queuePosition = sharedPreferences.get("queue_position", -1)
            return if (queuePosition == -1) null else queuePosition
        }

    var playbackPosition: Int?
        set(value) {
            sharedPreferences.put("playback_position", value ?: -1)
        }
        get() {
            val playbackPosition = sharedPreferences.get("playback_position", -1)
            return if (playbackPosition == -1) null else playbackPosition
        }

    var shuffleMode: QueueManager.ShuffleMode
        set(value) {
            sharedPreferences.put("shuffle_mode", value.ordinal)
        }
        get() {
            return QueueManager.ShuffleMode.init(sharedPreferences.get("shuffle_mode", -1))
        }

    var repeatMode: QueueManager.RepeatMode
        set(value) {
            sharedPreferences.put("repeat_mode", value.ordinal)
        }
        get() {
            return QueueManager.RepeatMode.init(sharedPreferences.get("repeat_mode", -1))
        }

    var mediaProviderTypes: List<MediaProvider.Type>
        set(value) {
            sharedPreferences.put("media_providers", value.map { it.ordinal }.joinToString(","))
        }
        get() {
            return sharedPreferences.get("media_providers", "")
                .split(",")
                .filter { it.isNotEmpty() }
                .map {
                    MediaProvider.Type.init(it.toInt())
                }
        }

    var equalizerEnabled: Boolean
        set(value) {
            sharedPreferences.put("equalizer_enabled", value)
        }
        get() {
            return sharedPreferences.get("equalizer_enabled", false)
        }

    var replayGainMode: ReplayGainAudioProcessor.Mode
        set(value) {
            sharedPreferences.put("replaygain_mode", value.ordinal)
        }
        get() {
            return ReplayGainAudioProcessor.Mode.init(sharedPreferences.get("replaygain_mode", ReplayGainAudioProcessor.Mode.Off.ordinal))
        }

    var preset: Equalizer.Presets.Preset
        set(value) {
            sharedPreferences.put("preset_name", value.name)
        }
        get() {
            val name = sharedPreferences.get("preset_name", Equalizer.Presets.custom.name)
            return Equalizer.Presets.all.firstOrNull { preset -> preset.name == name } ?: Equalizer.Presets.custom
        }

    private val listEqualizerBandType: Type = Types.newParameterizedType(MutableList::class.java, EqualizerBand::class.java)
    private val adapter: JsonAdapter<List<EqualizerBand>> by lazy { moshi.adapter(listEqualizerBandType) }
    var customPresetBands: List<EqualizerBand>?
        set(value) {
            sharedPreferences.put("custom_preset_bands", adapter.toJson(value))
        }
        get() {
            return sharedPreferences.getString("custom_preset_bands", null)?.let { json ->
                adapter.fromJson(json)
            }
        }
}