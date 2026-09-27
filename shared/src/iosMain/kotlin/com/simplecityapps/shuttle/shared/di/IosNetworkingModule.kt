package com.simplecityapps.shuttle.shared.di

import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.mediaprovider.MeteredNetwork
import com.simplecityapps.mediaprovider.getOrCreateClientId
import com.simplecityapps.mediaprovider.server.ServerCredentialStore
import com.simplecityapps.networking.NetworkConnectivity
import com.simplecityapps.networking.createHttpClient
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import com.simplecityapps.shuttle.platform.AppVersion
import com.simplecityapps.shuttle.shared.platform.NetworkPathMonitor
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import platform.UIKit.UIDevice

/**
 * What the Jellyfin and Emby providers need from the platform (docs/architecture/ios-port/phase-3-network.md): a Darwin
 * [HttpClient] and a Keychain-backed [ServerCredentialStore] each, under the names their containers ask for, and
 * the [ClientIdentity] the servers list this device under.
 */
@ContributesTo(AppScope::class)
@BindingContainer
abstract class IosNetworkingModule {
    @Binds
    abstract fun bindNetworkConnectivity(monitor: NetworkPathMonitor): NetworkConnectivity

    @Binds
    abstract fun bindMeteredNetwork(monitor: NetworkPathMonitor): MeteredNetwork

    companion object {
        @Provides
        @SingleIn(AppScope::class)
        @Named("JellyfinHttpClient")
        fun provideJellyfinHttpClient(connectivity: NetworkConnectivity): HttpClient = createHttpClient(connectivity = connectivity)

        @Provides
        @SingleIn(AppScope::class)
        @Named("JellyfinCredentialStore")
        fun provideJellyfinCredentialStore(securePreferenceManager: SecurePreferenceManager): ServerCredentialStore = ServerCredentialStore(securePreferenceManager, prefix = "jellyfin")

        @Provides
        @SingleIn(AppScope::class)
        @Named("EmbyHttpClient")
        fun provideEmbyHttpClient(connectivity: NetworkConnectivity): HttpClient = createHttpClient(connectivity = connectivity)

        @Provides
        @SingleIn(AppScope::class)
        @Named("EmbyCredentialStore")
        fun provideEmbyCredentialStore(securePreferenceManager: SecurePreferenceManager): ServerCredentialStore = ServerCredentialStore(securePreferenceManager, prefix = "emby")

        /** The client name Android sends too, so a server groups both apps' sessions under S2. */
        @Provides
        @SingleIn(AppScope::class)
        fun provideClientIdentity(
            securePreferenceManager: SecurePreferenceManager,
            appVersion: AppVersion
        ): ClientIdentity = ClientIdentity(
            id = securePreferenceManager.getOrCreateClientId(),
            clientName = "Shuttle2.0",
            version = appVersion.name(),
            deviceName = UIDevice.currentDevice.model
        )
    }
}
