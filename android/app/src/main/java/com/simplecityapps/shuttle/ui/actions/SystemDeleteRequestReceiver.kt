package com.simplecityapps.shuttle.ui.actions

import android.app.Activity
import android.content.IntentSender
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

/**
 * Launches the system dialogs that confirm deleting MediaStore songs and hands the user's answer back to the deleter's
 * [confirmations]. It lives as long as [activity] rather than any screen, so the answer still arrives after the screen
 * that asked has left composition. The handoff keeps the request in flight, and the activity result registry keeps the
 * launcher's key and the launched token in saved state, so an answer that comes back after a rotation or a
 * destroyed-and-recreated activity completes it. Create it once per activity instance, after `super.onCreate`.
 */
class SystemDeleteRequestReceiver(
    private val activity: ComponentActivity,
    private val confirmations: ConfirmationHandoff<IntentSender>,
) {
    private var launched: Long? = activity.savedStateRegistry.consumeRestoredStateForKey(STATE_KEY)
        ?.takeIf { it.containsKey(TOKEN) }
        ?.getLong(TOKEN)

    // The registry's lifecycle-free overload, so it can be created after the activity has started
    private val launcher = activity.activityResultRegistry.register(
        REGISTRY_KEY,
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        launched?.let { confirmations.deliver(it, result.resultCode == Activity.RESULT_OK) }
        launched = null
    }

    init {
        activity.savedStateRegistry.registerSavedStateProvider(STATE_KEY) {
            Bundle().apply { launched?.let { putLong(TOKEN, it) } }
        }
        activity.lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    launcher.unregister()
                    activity.savedStateRegistry.unregisterSavedStateProvider(STATE_KEY)
                    // A recreated activity still gets the dialog's answer; a finishing one never will
                    if (activity.isFinishing) launched?.let(confirmations::abandon)
                }
            },
        )
        // Only the resumed activity launches, so a request waits while the app is in the background
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                confirmations.requests.collect { request ->
                    if (!confirmations.launch(request)) return@collect
                    launched = request.token
                    try {
                        launcher.launch(IntentSenderRequest.Builder(request.payload).build())
                    } catch (e: IntentSender.SendIntentException) {
                        confirmations.deliver(request.token, false)
                        launched = null
                    }
                }
            }
        }
    }

    private companion object {
        const val REGISTRY_KEY = "system_delete_request"
        const val STATE_KEY = "system_delete_request_state"
        const val TOKEN = "token"
    }
}
