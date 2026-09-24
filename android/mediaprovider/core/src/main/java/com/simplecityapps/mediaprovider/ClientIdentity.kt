package com.simplecityapps.mediaprovider

import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import java.util.UUID

/**
 * One identity per install, sent to Jellyfin, Emby and Plex so a server's session/device list
 * shows a stable client across launches instead of a new device every time (#338).
 */
data class ClientIdentity(
    val id: String,
    val clientName: String,
    val version: String,
    val deviceName: String
)

/** Generates a UUID on first call and persists it, so later calls against the same store return the same id. */
fun SecurePreferenceManager.getOrCreateClientId(): String = clientId ?: UUID.randomUUID().toString().also { clientId = it }
