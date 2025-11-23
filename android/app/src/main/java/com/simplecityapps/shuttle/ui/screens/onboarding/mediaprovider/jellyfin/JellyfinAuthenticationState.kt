package com.simplecityapps.shuttle.ui.screens.onboarding.mediaprovider.jellyfin

sealed interface JellyfinAuthenticationState {
    data object Loading : JellyfinAuthenticationState
    data object Success : JellyfinAuthenticationState
    data class Error(val error: Throwable?) : JellyfinAuthenticationState
}
