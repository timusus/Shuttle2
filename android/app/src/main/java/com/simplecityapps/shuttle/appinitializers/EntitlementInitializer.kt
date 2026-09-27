package com.simplecityapps.shuttle.appinitializers

import android.app.Application
import com.simplecityapps.trial.Billing
import dev.zacsweers.metro.Inject
import timber.log.Timber

class EntitlementInitializer
@Inject
constructor(
    private val billing: Billing
) : AppInitializer {
    override fun init(application: Application) {
        Timber.v("Initializing billing")
        billing.start()
    }
}
