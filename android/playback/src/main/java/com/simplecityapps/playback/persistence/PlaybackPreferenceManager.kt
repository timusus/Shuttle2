package com.simplecityapps.playback.persistence

import com.simplecityapps.playback.dsp.equalizer.Equalizer
import com.simplecityapps.playback.dsp.equalizer.EqualizerBand
import com.simplecityapps.playback.equalizer.EqualizerPresetStore
import com.simplecityapps.playback.queue.RepeatMode
import com.simplecityapps.playback.queue.ShuffleMode
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.persistence.KeyValueStore
import com.simplecityapps.shuttle.persistence.putBoolean
import com.simplecityapps.shuttle.persistence.putInt
import com.simplecityapps.shuttle.persistence.putString
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.json.Json

class PlaybackPreferenceManager(
    private val store: KeyValueStore
) : EqualizerPresetStore {
    /**
     * A comma separated list of song ids
     */
    var queueIds: String?
        set(value) {
            store.putString("queue_ids", value ?: "")
        }
        get() {
            val queueIds = store.getString("queue_ids", "")!!
            return if (queueIds.isEmpty()) null else queueIds
        }

    /**
     * A comma separated list of song ids
     */
    var shuffleQueueIds: String?
        set(value) {
            store.putString("shuffle_queue_ids", value ?: "")
        }
        get() {
            val queueIds = store.getString("shuffle_queue_ids", "")!!
            return if (queueIds.isEmpty()) null else queueIds
        }

    var queuePosition: Int?
        set(value) {
            store.putInt("queue_position", value ?: -1)
        }
        get() {
            val queuePosition = store.getInt("queue_position", -1)
            return if (queuePosition == -1) null else queuePosition
        }

    /**
     * True when the saved [queuePosition] doesn't name the song that was playing (a file opened from another
     * app, which isn't saved with the queue), so [playbackPosition] isn't its position and a restore starts
     * the song from the beginning.
     */
    var restoreQueuePositionFromStart: Boolean
        set(value) {
            store.putBoolean("restore_queue_position_from_start", value)
        }
        get() {
            return store.getBoolean("restore_queue_position_from_start", false)
        }

    var playbackPosition: Int?
        set(value) {
            store.putInt("playback_position", value ?: -1)
        }
        get() {
            val playbackPosition = store.getInt("playback_position", -1)
            return if (playbackPosition == -1) null else playbackPosition
        }

    var shuffleMode: ShuffleMode
        set(value) {
            store.putInt("shuffle_mode", value.ordinal)
        }
        get() {
            return ShuffleMode.init(store.getInt("shuffle_mode", -1))
        }

    var repeatMode: RepeatMode
        set(value) {
            store.putInt("repeat_mode", value.ordinal)
        }
        get() {
            return RepeatMode.init(store.getInt("repeat_mode", -1))
        }

    var mediaProviderTypes: List<MediaProviderType>
        set(value) {
            store.putString("media_providers", value.map { it.ordinal }.joinToString(","))
        }
        get() {
            // A fresh install scans this device with the S2 scanner; an empty saved value means every source was turned off.
            return store.getString("media_providers", MediaProviderType.Shuttle.ordinal.toString())!!
                .split(",")
                .filter { it.isNotEmpty() }
                .map {
                    MediaProviderType.init(it.toInt())
                }
        }

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

    /**
     * The song the saved queue position names, saved as the position is; null with no saved queue. Read back with the
     * position it resumes from: the saved playback position, or the start when the saved song isn't the one that
     * was playing.
     */
    var nowPlaying: NowPlayingSnapshot?
        set(value) {
            store.putString("now_playing", value?.let { json.encodeToString(NowPlayingSnapshot.serializer(), it) } ?: "")
        }
        get() {
            val saved = store.getString("now_playing", "")!!.ifEmpty { return null }
            val snapshot = runCatching { json.decodeFromString(NowPlayingSnapshot.serializer(), saved) }.getOrNull() ?: return null
            return snapshot.copy(positionMs = if (restoreQueuePositionFromStart) 0 else playbackPosition ?: 0)
        }

    companion object {
        /**
         * Reads and writes the JSON Moshi wrote before (#584): fields in declaration order, nulls left out, unknown
         * fields ignored, enums by name.
         */
        internal val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = true
        }

        private val equalizerBandsSerializer = ListSerializer(EqualizerBand.serializer()).nullable
    }
}
