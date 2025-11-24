package com.simplecityapps.provider.emby.http

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PlaybackStartInfo(
    @Json(name = "ItemId")
    val itemId: String,
    @Json(name = "SessionId")
    val sessionId: String,
    @Json(name = "CanSeek")
    val canSeek: Boolean,
    @Json(name = "IsPaused")
    val isPaused: Boolean,
    @Json(name = "IsMuted")
    val isMuted: Boolean,
    @Json(name = "VolumeLevel")
    val volumeLevel: Int,
    @Json(name = "PlayMethod")
    val playMethod: String,
    @Json(name = "QueueableMediaTypes")
    val queueableMediaTypes: String
)

@JsonClass(generateAdapter = true)
data class PlaybackProgressInfo(
    @Json(name = "ItemId")
    val itemId: String,
    @Json(name = "SessionId")
    val sessionId: String,
    @Json(name = "PositionTicks")
    val positionTicks: Long,
    @Json(name = "CanSeek")
    val canSeek: Boolean,
    @Json(name = "IsPaused")
    val isPaused: Boolean,
    @Json(name = "IsMuted")
    val isMuted: Boolean,
    @Json(name = "VolumeLevel")
    val volumeLevel: Int,
    @Json(name = "PlayMethod")
    val playMethod: String
)

@JsonClass(generateAdapter = true)
data class PlaybackStopInfo(
    @Json(name = "ItemId")
    val itemId: String,
    @Json(name = "SessionId")
    val sessionId: String,
    @Json(name = "PositionTicks")
    val positionTicks: Long
)
