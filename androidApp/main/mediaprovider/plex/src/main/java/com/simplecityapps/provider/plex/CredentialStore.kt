package com.simplecityapps.provider.plex

import com.simplecityapps.provider.plex.http.AuthenticatedCredentials
import com.simplecityapps.provider.plex.http.LoginCredentials
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager

class CredentialStore(private val securePreferenceManager: SecurePreferenceManager) {

    var loginCredentials: LoginCredentials?
        get() {
            return securePreferenceManager.plexUserName?.let { userName ->
                securePreferenceManager.plexPass?.let { md5Pass ->
                    LoginCredentials(
                        username = userName,
                        password = md5Pass,
                        authCode = null
                    )
                }
            }
        }
        set(value) {
            securePreferenceManager.plexUserName = value?.username
            securePreferenceManager.plexPass = value?.password
        }

    var authenticatedCredentials: AuthenticatedCredentials?
        get() {
            return securePreferenceManager.plexAccessToken?.let { accessToken ->
                securePreferenceManager.plexUserId?.let { userId ->
                    AuthenticatedCredentials(accessToken, userId)
                }
            }
        }
        set(value) {
            securePreferenceManager.plexAccessToken = value?.accessToken
            securePreferenceManager.plexUserId = value?.userId
        }

    var address: String?
        get() {
            return securePreferenceManager.plexAddress
        }
        set(value) {
            securePreferenceManager.plexAddress = value
        }
}
