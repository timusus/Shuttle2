package com.simplecityapps.shuttle.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.server.AuthenticatedCredentials
import com.simplecityapps.mediaprovider.server.LoginCredentials
import com.simplecityapps.mediaprovider.server.ServerCredentialStore
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.provider.emby.EmbyMediaProvider
import com.simplecityapps.provider.jellyfin.JellyfinMediaProvider
import com.simplecityapps.provider.plex.PlexMediaProvider
import com.simplecityapps.provider.subsonic.SubsonicAuthenticationManager
import com.simplecityapps.provider.subsonic.SubsonicMediaProvider
import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.di.appGraph
import com.simplecityapps.shuttle.model.MediaProviderType
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Debug-build-only: lets `support/scripts/seed-remote-provider.sh` sign the app in to a Jellyfin,
 * Emby, Plex or Subsonic (Navidrome) server with an existing access token (or API key) via `adb shell am broadcast`, so
 * an emulator run never needs a password typed into the UI. Stores the address and credentials,
 * and enables the provider; the script then triggers an import through
 * [DebugMediaImportReceiver].
 *
 * Extras: `provider` (`jellyfin`, `emby` or `plex`), `address`, `user_id`, `access_token`. `user_id`
 * is required for all three providers even though Plex's [PlexMediaProvider] never reads it back
 * (unlike Jellyfin/Emby, Plex API calls only need the token) -- it's just stored alongside the
 * token for parity with Plex's [ServerCredentialStore.authenticatedCredentials],
 * so the seed script can pass a placeholder instead of looking one up.
 *
 * `subsonic` takes the username as `user_id` and the password as `access_token`: Subsonic has no
 * access token, so the receiver stores them as the saved login, then signs in with `ping` to record
 * what the server supports (its OpenSubsonic extensions).
 */
class DebugRemoteProviderReceiver : BroadcastReceiver() {
    @ContributesTo(AppScope::class)
    interface Injector {
        fun inject(receiver: DebugRemoteProviderReceiver)
    }

    @Inject
    @field:Named("JellyfinCredentialStore")
    lateinit var jellyfinCredentialStore: ServerCredentialStore

    @Inject
    @field:Named("EmbyCredentialStore")
    lateinit var embyCredentialStore: ServerCredentialStore

    @Inject
    @field:Named("PlexCredentialStore")
    lateinit var plexCredentialStore: ServerCredentialStore

    @Inject
    lateinit var jellyfinMediaProvider: JellyfinMediaProvider

    @Inject
    lateinit var embyMediaProvider: EmbyMediaProvider

    @Inject
    lateinit var plexMediaProvider: PlexMediaProvider

    @Inject
    lateinit var subsonicAuthenticationManager: SubsonicAuthenticationManager

    @Inject
    lateinit var subsonicMediaProvider: SubsonicMediaProvider

    @Inject
    @field:AppCoroutineScope
    lateinit var appCoroutineScope: CoroutineScope

    @Inject
    lateinit var mediaImporter: MediaImporter

    @Inject
    lateinit var playbackPreferenceManager: PlaybackPreferenceManager

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        context.appGraph<Injector>().inject(this)
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
                jellyfinCredentialStore.authenticatedCredentials = AuthenticatedCredentials(accessToken, userId)
                mediaImporter.mediaProviders += jellyfinMediaProvider
                MediaProviderType.Jellyfin
            }

            "emby" -> {
                embyCredentialStore.address = address
                embyCredentialStore.authenticatedCredentials = AuthenticatedCredentials(accessToken, userId)
                mediaImporter.mediaProviders += embyMediaProvider
                MediaProviderType.Emby
            }

            "plex" -> {
                plexCredentialStore.address = address
                plexCredentialStore.authenticatedCredentials = AuthenticatedCredentials(accessToken, userId)
                mediaImporter.mediaProviders += plexMediaProvider
                MediaProviderType.Plex
            }

            "subsonic" -> {
                val login = LoginCredentials(username = userId, password = accessToken)
                subsonicAuthenticationManager.setAddress(address)
                subsonicAuthenticationManager.setLoginCredentials(login)
                val pending = goAsync()
                appCoroutineScope.launch {
                    try {
                        subsonicAuthenticationManager.authenticate(address, login)
                            .onSuccess { Timber.i("DebugRemoteProviderReceiver: Subsonic sign-in recorded ${subsonicAuthenticationManager.serverInfo}") }
                            .onFailure { Timber.e(it, "DebugRemoteProviderReceiver: Subsonic sign-in failed") }
                    } finally {
                        pending.finish()
                    }
                }
                mediaImporter.mediaProviders += subsonicMediaProvider
                MediaProviderType.Subsonic
            }

            else -> {
                Timber.e("DebugRemoteProviderReceiver: provider must be jellyfin, emby, plex or subsonic")
                return
            }
        }

        if (type !in playbackPreferenceManager.mediaProviderTypes) {
            playbackPreferenceManager.mediaProviderTypes += type
        }
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
