package com.simplecityapps.shuttle.appinitializers

import android.app.Application
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.settings.PrivacySettings
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class RemoteConfigInitializer
@Inject
constructor(
    private val privacySettings: PrivacySettings,
    private val remoteConfig: FirebaseRemoteConfig,
    @AppCoroutineScope private val coroutineScope: CoroutineScope
) : AppInitializer {
    override fun init(application: Application) {
        if (privacySettings.analytics.value) {
            coroutineScope.launch {
                remoteConfig.fetchAndActivate().await()
            }
        }
    }

    override fun priority(): Int = 1
}
