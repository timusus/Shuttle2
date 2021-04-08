package com.simplecityapps.provider.jellyfin

import com.simplecityapps.provider.jellyfin.http.AuthenticatedCredentials
import com.simplecityapps.provider.jellyfin.http.LoginCredentials
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager

class CredentialStore(private val securePreferenceManager: SecurePreferenceManager) {

    var loginCredentials: LoginCredentials?
        get() {
            return securePreferenceManager.jellyfinUserName?.let { userName ->
                securePreferenceManager.jellyfinPass?.let { md5Pass ->
                    LoginCredentials(userName, md5Pass)
                }
            }
        }
        set(value) {
            securePreferenceManager.jellyfinUserName = value?.username
            securePreferenceManager.jellyfinPass = value?.password
        }

    var authenticatedCredentials: AuthenticatedCredentials?
        get() {
            return securePreferenceManager.jellyfinAccessToken?.let { accessToken ->
                securePreferenceManager.jellyfinUserId?.let { userId ->
                    AuthenticatedCredentials(accessToken, userId)
                }
            }
        }
        set(value) {
            securePreferenceManager.jellyfinAccessToken = value?.accessToken
            securePreferenceManager.jellyfinUserId = value?.userId
        }

    var address: String?
        get() {
            return securePreferenceManager.jellyfinAddress
        }
        set(value) {
            securePreferenceManager.jellyfinAddress = value
        }
}