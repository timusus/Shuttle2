package com.simplecityapps.mediaprovider

import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import kotlin.uuid.Uuid

/** Stable per install, so a server's device list doesn't gain an entry every launch. */
data class ClientIdentity(
    val id: String,
    val clientName: String,
    val version: String,
    val deviceName: String
)

/** Generates a UUID on first call and persists it, so later calls against the same store return the same id. */
fun SecurePreferenceManager.getOrCreateClientId(): String = clientId ?: Uuid.random().toString().also { clientId = it }
