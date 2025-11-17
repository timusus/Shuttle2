package com.simplecityapps.shuttle.ui.screens.onboarding.mediaprovider.plex

import com.simplecityapps.provider.plex.http.LoginCredentials

data class PlexConfigurationViewState(
    val address: String,
    val canAuthenticate: Boolean,
    val rememberPassword: Boolean,
    val loginCredentials: LoginCredentials,
    val authenticationState: PlexAuthenticationState?
) {
    companion object {
        val Empty = PlexConfigurationViewState(
            address = "",
            canAuthenticate = false,
            rememberPassword = false,
            authenticationState = null,
            loginCredentials = LoginCredentials.Empty
        )
    }
}
