package com.simplecityapps.shuttle.ui.screens.onboarding.mediaprovider.plex

sealed interface PlexAuthenticationState {
    data object Loading : PlexAuthenticationState
    data object Success : PlexAuthenticationState
    data class Error(val error: Throwable?) : PlexAuthenticationState
}
