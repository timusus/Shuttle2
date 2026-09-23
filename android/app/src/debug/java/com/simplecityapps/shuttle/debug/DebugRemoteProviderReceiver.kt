package com.simplecityapps.shuttle.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.provider.emby.CredentialStore as EmbyCredentialStore
import com.simplecityapps.provider.emby.EmbyMediaProvider
import com.simplecityapps.provider.emby.http.AuthenticatedCredentials as EmbyAuthenticatedCredentials
import com.simplecityapps.provider.jellyfin.CredentialStore as JellyfinCredentialStore
import com.simplecityapps.provider.jellyfin.JellyfinMediaProvider
import com.simplecityapps.provider.jellyfin.http.AuthenticatedCredentials as JellyfinAuthenticatedCredentials
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import timber.log.Timber

/**
 * Debug-build-only: lets `support/scripts/seed-remote-provider.sh` sign the app in to a Jellyfin or
 * Emby server with an existing access token (or API key) via `adb shell am broadcast`, so an
 * emulator run never needs a password typed into the UI. Stores the address and credentials,
 * enables the provider, and marks onboarding done; the script then triggers an import through
 * [DebugMediaImportReceiver].
 *
 * Extras: `provider` (`jellyfin` or `emby`), `address`, `user_id`, `access_token`.
 */
@AndroidEntryPoint
class DebugRemoteProviderReceiver : BroadcastReceiver() {
    @Inject
    lateinit var jellyfinCredentialStore: JellyfinCredentialStore

    @Inject
    lateinit var embyCredentialStore: EmbyCredentialStore

    @Inject
    lateinit var jellyfinMediaProvider: JellyfinMediaProvider

    @Inject
    lateinit var embyMediaProvider: EmbyMediaProvider

    @Inject
    lateinit var mediaImporter: MediaImporter

    @Inject
    lateinit var playbackPreferenceManager: PlaybackPreferenceManager

    @Inject
    lateinit var generalPreferenceManager: GeneralPreferenceManager

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        val address = intent.getStringExtra(EXTRA_ADDRESS)?.trimEnd('/')
        val userId = intent.getStringExtra(EXTRA_USER_ID)
        val accessToken = intent.getStringExtra(EXTRA_ACCESS_TOKEN)
        if (address.isNullOrEmpty() || userId.isNullOrEmpty() || accessToken.isNullOrEmpty()) {
            Timber.e("DebugRemoteProviderReceiver: address, user_id and access_token are all required")
            return
        }

        val type = when (intent.getStringExtra(EXTRA_PROVIDER)) {
            "jellyfin" -> {
                jellyfinCredentialStore.address = address
                jellyfinCredentialStore.authenticatedCredentials = JellyfinAuthenticatedCredentials(accessToken, userId)
                mediaImporter.mediaProviders += jellyfinMediaProvider
                MediaProviderType.Jellyfin
            }

            "emby" -> {
                embyCredentialStore.address = address
                embyCredentialStore.authenticatedCredentials = EmbyAuthenticatedCredentials(accessToken, userId)
                mediaImporter.mediaProviders += embyMediaProvider
                MediaProviderType.Emby
            }

            else -> {
                Timber.e("DebugRemoteProviderReceiver: provider must be jellyfin or emby")
                return
            }
        }

        if (type !in playbackPreferenceManager.mediaProviderTypes) {
            playbackPreferenceManager.mediaProviderTypes += type
        }
        generalPreferenceManager.hasOnboarded = true
        Timber.i("DebugRemoteProviderReceiver: signed in to $type at $address")
    }

    companion object {
        const val ACTION_SEED_REMOTE_PROVIDER = "com.simplecityapps.shuttle.debug.ACTION_SEED_REMOTE_PROVIDER"
        const val EXTRA_PROVIDER = "provider"
        const val EXTRA_ADDRESS = "address"
        const val EXTRA_USER_ID = "user_id"
        const val EXTRA_ACCESS_TOKEN = "access_token"
    }
}
