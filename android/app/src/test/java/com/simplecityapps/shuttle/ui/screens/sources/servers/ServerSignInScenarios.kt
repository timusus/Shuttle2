package com.simplecityapps.shuttle.ui.screens.sources.servers

import com.simplecityapps.shuttle.model.MediaProviderType

fun serverSignInForm(
    type: MediaProviderType = MediaProviderType.Jellyfin,
    form: ServerSignInForm = ServerSignInForm(address = "http://"),
    showProDisclosure: Boolean = false,
    quickConnectEnabled: Boolean = false,
) = ServerSignInUiState(type, form, showProDisclosure = showProDisclosure, quickConnectEnabled = quickConnectEnabled)

fun serverSignInAuthenticating(type: MediaProviderType = MediaProviderType.Jellyfin) = ServerSignInUiState(type, step = ServerSignInStep.Authenticating)

fun serverSignInAwaitingCode(code: String, type: MediaProviderType = MediaProviderType.Jellyfin) = ServerSignInUiState(type, step = ServerSignInStep.AwaitingCode(code))

fun serverSignInAwaitingPin(
    code: String = "H7KQ",
    authUrl: String = "https://app.plex.tv/auth#?code=H7KQ",
) = ServerSignInUiState(MediaProviderType.Plex, step = ServerSignInStep.AwaitingPin(code, authUrl, "https://plex.tv/link"))

fun serverSignInChoosingServer(vararg servers: ServerChoice) = ServerSignInUiState(MediaProviderType.Plex, step = ServerSignInStep.ChoosingServer(servers.toList()))

fun serverSignInConnected(type: MediaProviderType = MediaProviderType.Jellyfin) = ServerSignInUiState(type, step = ServerSignInStep.Connected)

fun serverSignInFailed(
    message: String,
    type: MediaProviderType = MediaProviderType.Jellyfin,
) = ServerSignInUiState(type, step = ServerSignInStep.Failed(message))
