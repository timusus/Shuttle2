package com.simplecityapps.shuttle.ui.screens.onboarding.mediaprovider.emby

sealed interface EmbyAuthenticationState {
    data object Loading : EmbyAuthenticationState
    data object Success : EmbyAuthenticationState
    data class Error(val error: Throwable?) : EmbyAuthenticationState
}
