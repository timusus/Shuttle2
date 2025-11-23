package com.simplecityapps.shuttle.ui.screens.onboarding.mediaprovider.jellyfin

import com.simplecityapps.provider.jellyfin.http.LoginCredentials

data class JellyfinConfigurationViewState(
    val address: String,
    val canAuthenticate: Boolean,
    val rememberPassword: Boolean,
    val loginCredentials: LoginCredentials,
    val authenticationState: JellyfinAuthenticationState?
) {
    companion object {
        val Empty = JellyfinConfigurationViewState(
            address = "",
            canAuthenticate = false,
            rememberPassword = false,
            authenticationState = null,
            loginCredentials = LoginCredentials.Empty
        )
    }
}
