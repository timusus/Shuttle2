package com.simplecityapps.shuttle.ui.screens.sources.servers

import com.simplecityapps.shuttle.model.MediaProviderType

fun serverSignInForm(
    type: MediaProviderType = MediaProviderType.Jellyfin,
    form: ServerSignInForm = ServerSignInForm(address = "http://"),
    showProDisclosure: Boolean = false,
) = ServerSignInUiState(type, form, showProDisclosure = showProDisclosure)

fun serverSignInAuthenticating(type: MediaProviderType = MediaProviderType.Jellyfin) = ServerSignInUiState(type, step = ServerSignInStep.Authenticating)

fun serverSignInConnected(type: MediaProviderType = MediaProviderType.Jellyfin) = ServerSignInUiState(type, step = ServerSignInStep.Connected)

fun serverSignInFailed(
    message: String,
    type: MediaProviderType = MediaProviderType.Jellyfin,
) = ServerSignInUiState(type, step = ServerSignInStep.Failed(message))
