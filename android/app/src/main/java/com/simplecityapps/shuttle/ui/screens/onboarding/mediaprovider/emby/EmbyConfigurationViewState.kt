package com.simplecityapps.shuttle.ui.screens.onboarding.mediaprovider.emby

import com.simplecityapps.provider.emby.http.LoginCredentials

data class EmbyConfigurationViewState(
    val address: String,
    val canAuthenticate: Boolean,
    val rememberPassword: Boolean,
    val loginCredentials: LoginCredentials,
    val authenticationState: EmbyAuthenticationState?
) {
    companion object {
        val Empty = EmbyConfigurationViewState(
            address = "",
            canAuthenticate = false,
            rememberPassword = false,
            authenticationState = null,
            loginCredentials = LoginCredentials.Empty
        )
    }
}
