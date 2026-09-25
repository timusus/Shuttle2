package com.simplecityapps.shuttle.appinitializers

import android.app.Application
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.trial.Billing
import com.simplecityapps.trial.EntitlementRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

class EntitlementInitializer
@Inject
constructor(
    private val billing: Billing,
    private val entitlementRepository: EntitlementRepository,
    private val playbackPreferenceManager: PlaybackPreferenceManager,
    @AppCoroutineScope private val coroutineScope: CoroutineScope
) : AppInitializer {
    override fun init(application: Application) {
        Timber.v("Initializing billing")
        billing.start()

        // A user who connected a server before the server trial existed gets their one trial now.
        if (playbackPreferenceManager.mediaProviderTypes.any { it.remote }) {
            coroutineScope.launch { entitlementRepository.startServerTrialIfEligible() }
        }
    }
}
