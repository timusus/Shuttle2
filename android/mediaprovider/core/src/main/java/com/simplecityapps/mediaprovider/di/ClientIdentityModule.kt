package com.simplecityapps.mediaprovider.di

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.mediaprovider.getOrCreateClientId
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
object ClientIdentityModule {
    @Provides
    @Singleton
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
