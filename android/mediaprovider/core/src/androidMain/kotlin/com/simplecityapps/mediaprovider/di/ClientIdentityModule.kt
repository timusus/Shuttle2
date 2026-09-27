package com.simplecityapps.mediaprovider.di

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.mediaprovider.getOrCreateClientId
import com.simplecityapps.shuttle.di.ApplicationContext
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@ContributesTo(AppScope::class)
@BindingContainer
object ClientIdentityModule {
    @Provides
    @SingleIn(AppScope::class)
    fun provideClientIdentity(
        @ApplicationContext context: Context,
        securePreferenceManager: SecurePreferenceManager
    ): ClientIdentity {
        val version = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: PackageManager.NameNotFoundException) {
            null
        } ?: "1.0"

        return ClientIdentity(
            id = securePreferenceManager.getOrCreateClientId(),
            clientName = "Shuttle2.0",
            version = version,
            deviceName = Build.MODEL
        )
    }
}
