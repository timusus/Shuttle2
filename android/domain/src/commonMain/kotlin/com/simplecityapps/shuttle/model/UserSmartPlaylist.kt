package com.simplecityapps.shuttle.model

import com.simplecityapps.shuttle.smartplaylist.SmartRules
import kotlin.time.Instant

/** A smart playlist the user defined, as opposed to the built-in [SmartPlaylist]s. */
data class UserSmartPlaylist(
    val id: Long,
    val name: String,
    val rules: SmartRules,
    val createdAt: Instant,
)
