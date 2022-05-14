package com.simplecityapps.trial

import com.squareup.moshi.JsonClass
import java.util.*

@JsonClass(generateAdapter = true)
data class Device(
    val deviceId: String,
    val lastUpdate: Date
)
