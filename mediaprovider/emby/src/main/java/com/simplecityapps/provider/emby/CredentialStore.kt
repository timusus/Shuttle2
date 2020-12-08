package com.simplecityapps.provider.emby

import com.simplecityapps.provider.emby.http.AuthenticatedCredentials
import com.simplecityapps.provider.emby.http.LoginCredentials
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager

class CredentialStore(private val securePreferenceManager: SecurePreferenceManager) {

    var loginCredentials: LoginCredentials?
        get() {
            return securePreferenceManager.embyUserName?.let { userName ->
                securePreferenceManager.embyPass?.let { md5Pass ->
                    LoginCredentials(userName, md5Pass)
                }
            }
        }
        set(value) {
            securePreferenceManager.embyUserName = value?.username
            securePreferenceManager.embyPass = value?.password
        }

    var authenticatedCredentials: AuthenticatedCredentials?
        get() {
            return securePreferenceManager.embyAccessToken?.let { accessToken ->
                securePreferenceManager.embyUserId?.let { userId ->
                    AuthenticatedCredentials(accessToken, userId)
                }
            }
        }
        set(value) {
            securePreferenceManager.embyAccessToken = value?.accessToken
            securePreferenceManager.embyUserId = value?.userId
        }

    var host: String?
        get() {
            return securePreferenceManager.embyHost
        }
        set(value) {
            securePreferenceManager.embyHost = value
        }

    var port: Int?
        get() {
            return securePreferenceManager.embyPort
        }
        set(value) {
            securePreferenceManager.embyPort = value
        }
}