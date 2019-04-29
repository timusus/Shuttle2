package com.simplecityapps.playback.persistence

import android.content.SharedPreferences
import com.simplecityapps.get
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.put

class PlaybackPreferenceManager(private val sharedPreferences: SharedPreferences) {

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
        set (value) {
            sharedPreferences.put("shuffle_mode", value.ordinal)
        }
        get() {
            return QueueManager.ShuffleMode.init(sharedPreferences.get("shuffle_mode", -1))
        }

    var repeatMode: QueueManager.RepeatMode
        set (value) {
            sharedPreferences.put("repeat_mode", value.ordinal)
        }
        get() {
            return QueueManager.RepeatMode.init(sharedPreferences.get("repeat_mode", -1))
        }
}