package com.simplecityapps.shuttle.shared.entitlement

import com.simplecityapps.shuttle.analytics.MonetisationAnalytics
import com.simplecityapps.shuttle.entitlement.Entitlement
import com.simplecityapps.shuttle.settings.Setting
import com.simplecityapps.shuttle.settings.SettingsStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.first

/**
 * Sends `entitlement_resolved` once per install, as Android's `EntitlementRepository` does (#947): when StoreKit has
 * first answered and analytics is capturing. The flag is set only once a backend took the event, so an opt-out that
 * raced in offers it again next launch.
 */
@SingleIn(AppScope::class)
class EntitlementResolvedReporter @Inject constructor(
    private val entitlements: StoreEntitlements,
    private val analytics: MonetisationAnalytics,
    store: SettingsStore
) {
    private val logged = store.preference(Logged)

    /** Suspends until the event is sent, or returns at once if an earlier launch sent it. */
    suspend fun report() {
        val resolved = entitlements.entitlement.first { it !is Entitlement.Unknown }
        if (logged.value) return
        analytics.awaitCapturing()
        if (analytics.entitlementResolved(resolved)) {
            logged.value = true
        }
    }

    companion object {
        val Logged = Setting.boolean("entitlement_resolved_logged", false)
    }
}
