package com.simplecityapps.provider.emby

import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.mediaprovider.server.ServerCredentialStore
import com.simplecityapps.mediaprovider.server.StreamProfile
import com.simplecityapps.mediaprovider.server.mediabrowser.MediaBrowserAuthenticationManager
import com.simplecityapps.mediaprovider.server.mediabrowser.MediaBrowserServer
import com.simplecityapps.mediaprovider.server.mediabrowser.UserService
import io.ktor.client.HttpClient

class EmbyAuthenticationManager(
    httpClient: HttpClient,
    credentialStore: ServerCredentialStore,
    clientIdentity: ClientIdentity,
    streamProfile: StreamProfile
) : MediaBrowserAuthenticationManager(
    MediaBrowserServer.Emby,
    UserService(httpClient, MediaBrowserServer.Emby, clientIdentity),
    credentialStore,
    clientIdentity,
    streamProfile
)
