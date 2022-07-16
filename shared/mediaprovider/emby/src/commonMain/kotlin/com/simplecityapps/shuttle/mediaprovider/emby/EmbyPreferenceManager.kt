package com.simplecityapps.shuttle.mediaprovider.emby

import com.simplecityapps.shuttle.mediaprovider.emby.http.data.AuthenticatedCredentials
import com.simplecityapps.shuttle.mediaprovider.emby.http.data.LoginCredentials
import com.simplecityapps.shuttle.preferences.SecurePreferenceManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class EmbyPreferenceManager(
    private val securePreferenceManager: SecurePreferenceManager
) {

    object PreferenceKey {
        const val USERNAME = "emby_username"
        const val PASSWORD = "emby_pass"
        const val TOKEN = "emby_access_token"
        const val USER_ID = "emby_user_id"
        const val ADDRESS = "emby_address"
        const val REMEMBER_PASSWORD = "remember_password"
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

    suspend fun setRememberPassword(value: Boolean) {
        securePreferenceManager.setBoolean(PreferenceKey.REMEMBER_PASSWORD, value)
    }

    fun getRememberPassword(): Flow<Boolean> {
        return securePreferenceManager.getBoolean(PreferenceKey.REMEMBER_PASSWORD)
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

    suspend fun setAuthenticatedCredentials(credentials: AuthenticatedCredentials?) {
        setAccessToken(credentials?.accessToken)
        setUserId(credentials?.userId)
    }
}