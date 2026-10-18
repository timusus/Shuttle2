package com.simplecityapps.shuttle.ui.screens.sources.servers

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The sign-in's wait for the local-network permission: asks the screen for it, and calls [onGranted] once it's allowed. */
internal class LocalNetworkGate(
    private val localNetworkAccess: LocalNetworkAccess,
    private val onGranted: () -> Unit,
) {
    private val _requested = MutableStateFlow(false)

    /** True while the sign-in waits for the screen to ask the user for the permission. */
    val requested: StateFlow<Boolean> = _requested
    private var result: CompletableDeferred<Boolean>? = null
    private var refused = false

    /**
     * The screen's answer to [requested]: whether the user allowed local-network access.
     * Whatever waited on it carries on, or fails when it was refused. An answer with no request outstanding (a dialog
     * redelivering its result after the screen was recreated) is ignored.
     */
    fun onResult(granted: Boolean) {
        val pending = result ?: return
        result = null
        _requested.value = false
        pending.complete(granted)
    }

    /**
     * True once the user has allowed [needsRequest]'s access, asking the screen first when it must; false if refused.
     * A refusal is remembered until [reset], so nothing asks again meanwhile.
     */
    suspend fun await(needsRequest: Boolean): Boolean {
        if (!needsRequest) return true
        if (refused) return false
        val pending = result ?: CompletableDeferred<Boolean>().also {
            result = it
            _requested.value = true
        }
        val granted = pending.await()
        if (granted) onGranted() else refused = true
        return granted
    }

    /** Forgets a refusal, so the next [await] asks again. */
    fun reset() {
        refused = false
    }
}
