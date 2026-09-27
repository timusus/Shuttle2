package com.simplecityapps.mediaprovider.server

import com.simplecityapps.shuttle.persistence.SecurePreferenceManager

/**
 * A media server's address and credentials, kept in [SecurePreferenceManager] under `<prefix>_*` keys: `jellyfin`,
 * `emby` and `plex` read the keys each provider has always written, so existing sign-ins carry over. [addressKey]
 * is there for Plex, which has always stored its address as `plex_host`.
 */
class ServerCredentialStore(
    private val securePreferenceManager: SecurePreferenceManager,
    prefix: String,
    private val addressKey: String = "${prefix}_address"
) {
    private val userNameKey = "${prefix}_username"
    private val passwordKey = "${prefix}_pass"
    private val accessTokenKey = "${prefix}_access_token"
    private val userIdKey = "${prefix}_user_id"
    private val canDownloadKey = "${prefix}_can_download"

    /** The saved sign-in, for re-authenticating once the session expires. Never holds an [LoginCredentials.authCode]. */
    var loginCredentials: LoginCredentials?
        get() {
            val userName = securePreferenceManager.getString(userNameKey) ?: return null
            val password = securePreferenceManager.getString(passwordKey) ?: return null
            return LoginCredentials(userName, password)
        }
        set(value) {
            securePreferenceManager.putString(userNameKey, value?.username)
            securePreferenceManager.putString(passwordKey, value?.password)
        }

    var authenticatedCredentials: AuthenticatedCredentials?
        get() {
            val accessToken = securePreferenceManager.getString(accessTokenKey) ?: return null
            val userId = securePreferenceManager.getString(userIdKey) ?: return null
            return AuthenticatedCredentials(accessToken, userId, securePreferenceManager.getBoolean(canDownloadKey))
        }
        set(value) {
            securePreferenceManager.putString(accessTokenKey, value?.accessToken)
            securePreferenceManager.putString(userIdKey, value?.userId)
            securePreferenceManager.putBoolean(canDownloadKey, value?.canDownload ?: false)
        }

    var address: String?
        get() = securePreferenceManager.getString(addressKey)
        set(value) {
            securePreferenceManager.putString(addressKey, value)
        }
}
