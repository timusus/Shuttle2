package com.simplecityapps.playback.persistence

import android.content.SharedPreferences
import com.simplecityapps.get
import com.simplecityapps.put
import timber.log.Timber

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

    var queuePosition: Int?
        set(value) {
            Timber.v("Storing queue position: $value")
            sharedPreferences.put("queue_position", value ?: -1)
        }
        get() {
            val queuePosition = sharedPreferences.get("queue_position", -1)
            return if (queuePosition == -1) null else queuePosition
        }

    var playbackPosition: Int?
        set(value) {
            Timber.v("Storing playback position: $value")
            sharedPreferences.put("playback_position", value ?: -1)
        }
        get() {
            val playbackPosition = sharedPreferences.get("playback_position", -1)
            return if (playbackPosition == -1) null else playbackPosition
        }
}