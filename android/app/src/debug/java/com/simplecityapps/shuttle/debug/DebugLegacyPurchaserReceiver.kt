package com.simplecityapps.shuttle.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.simplecityapps.shuttle.di.appGraph
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.trial.EntitlementStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import kotlin.time.Clock

/**
 * Debug-build-only: leaves the stores as a fresh upgrade from 1.0.10 would (#1024). `--ez purchased true|false`
 * sets or clears `app_purchased_date`; the entitlement cache and its settled flag are always cleared. The caller
 * must force-stop and cold-start afterwards: the seed runs once, when the process creates `EntitlementRepository`.
 */
class DebugLegacyPurchaserReceiver : BroadcastReceiver() {
    @ContributesTo(AppScope::class)
    interface Injector {
        fun inject(receiver: DebugLegacyPurchaserReceiver)
    }

    @Inject
    lateinit var generalPreferenceManager: GeneralPreferenceManager

    @Inject
    lateinit var entitlementStore: EntitlementStore

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        context.appGraph<Injector>().inject(this)
        val purchased = intent.getBooleanExtra("purchased", true)
        generalPreferenceManager.appPurchasedDate = if (purchased) Clock.System.now() else null
        entitlementStore.cachedPro = null
        entitlementStore.legacySeedSettled = false
        Log.i(TAG, "SEED_LEGACY_PURCHASER ok: purchased=$purchased")
    }

    companion object {
        private const val TAG = "S2Debug"
    }
}
