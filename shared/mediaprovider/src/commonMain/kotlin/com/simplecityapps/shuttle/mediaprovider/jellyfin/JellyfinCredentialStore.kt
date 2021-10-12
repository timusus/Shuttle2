package com.simplecityapps.shuttle.mediaprovider.jellyfin

import com.simplecityapps.shuttle.mediaprovider.jellyfin.http.data.AuthenticatedCredentials
import com.simplecityapps.shuttle.mediaprovider.jellyfin.http.data.LoginCredentials
import com.simplecityapps.shuttle.preferences.SecurePreferenceManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class JellyfinCredentialStore(
    private val securePreferenceManager: SecurePreferenceManager
) {

    object PreferenceKey {
        const val USERNAME = "jellyfin_username"
        const val PASSWORD = "jellyfin_pass"
        const val TOKEN = "jellyfin_access_token"
        const val USER_ID = "jellyfin_user_id"
        const val ADDRESS = "jellyfin_address"
    }

    suspend fun setUserName(value: String?) {
        securePreferenceManager.setString(PreferenceKey.USERNAME, value)
    }

    fun getUserName(): Flow<String?> {
        return securePreferenceManager.getString(PreferenceKey.USERNAME)
    }

    suspend fun setPassword(value: String?) {
        securePreferenceManager.setString(PreferenceKey.PASSWORD, value)
    }

    fun getPassword(): Flow<String?> {
        return securePreferenceManager.getString(PreferenceKey.PASSWORD)
    }

    suspend fun setAccessToken(value: String?) {
        securePreferenceManager.setString(PreferenceKey.TOKEN, value)
    }

    fun getAccessToken(): Flow<String?> {
        return securePreferenceManager.getString(PreferenceKey.TOKEN)
    }

    suspend fun setUserId(value: String?) {
        securePreferenceManager.setString(PreferenceKey.USER_ID, value)
    }

    fun getUserId(): Flow<String?> {
        return securePreferenceManager.getString(PreferenceKey.USER_ID)
    }

    suspend fun setAddress(value: String?) {
        securePreferenceManager.setString(PreferenceKey.ADDRESS, value)
    }

    fun getAddress(): Flow<String?> {
        return securePreferenceManager.getString(PreferenceKey.ADDRESS)
    }

    fun getLoginCredentials(): Flow<LoginCredentials?> {
        return combine(getUserName(), getPassword()) { userName, password ->
            userName?.let {
                password?.let {
                    LoginCredentials(userName, password)
                }
            }
        }
    }

    fun getAuthenticatedCredentials(): Flow<AuthenticatedCredentials?> {
        return combine(getAccessToken(), getUserId()) { accessToken, userId ->
            accessToken?.let {
                userId?.let {
                    AuthenticatedCredentials(accessToken, userId)
                }
            }
        }
    }
}