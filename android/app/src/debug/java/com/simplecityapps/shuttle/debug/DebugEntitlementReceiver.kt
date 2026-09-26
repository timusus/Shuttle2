package com.simplecityapps.shuttle.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.simplecityapps.trial.DebugEntitlementOverride
import com.simplecityapps.trial.EntitlementRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Debug-build-only: overrides the resolved entitlement so paywall UI (the add-server disclosure, the
 * gated-skip snackbar, the trial chip) can be exercised on the emulator without a real purchase or
 * trial. Debug builds otherwise always resolve Pro. `--es state free|trial|pro|real` (`real` clears the
 * override). Replies on logcat tag [TAG]; `support/scripts/s2-debug.sh` wraps the broadcast syntax.
 */
@AndroidEntryPoint
class DebugEntitlementReceiver : BroadcastReceiver() {
    @Inject
    lateinit var entitlementRepository: EntitlementRepository

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        val state = intent.getStringExtra("state")
        val override = when (state?.lowercase()) {
            "free" -> DebugEntitlementOverride.Free

            "trial" -> DebugEntitlementOverride.Trial

            "pro" -> DebugEntitlementOverride.Pro

            "real" -> DebugEntitlementOverride.None

            else -> {
                Log.e(TAG, "SET_ENTITLEMENT error: missing or unknown --es state free|trial|pro|real")
                return
            }
        }
        entitlementRepository.setDebugOverride(override)
        Log.i(TAG, "SET_ENTITLEMENT ok: $state")
    }

    companion object {
        private const val TAG = "S2Debug"
        const val ACTION_SET_ENTITLEMENT = "com.simplecityapps.shuttle.debug.SET_ENTITLEMENT"
    }
}
