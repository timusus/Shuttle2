package com.simplecityapps.shuttle.appinitializers

import android.app.Application
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class RemoteConfigInitializer
@Inject
constructor(
    private val preferenceManager: GeneralPreferenceManager,
    private val remoteConfig: FirebaseRemoteConfig,
    @AppCoroutineScope private val coroutineScope: CoroutineScope
) : AppInitializer {
    override fun init(application: Application) {
        if (preferenceManager.firebaseAnalyticsEnabled) {
            coroutineScope.launch {
                remoteConfig.fetchAndActivate().await()
            }
        }
    }

    override fun priority(): Int = 1
}
