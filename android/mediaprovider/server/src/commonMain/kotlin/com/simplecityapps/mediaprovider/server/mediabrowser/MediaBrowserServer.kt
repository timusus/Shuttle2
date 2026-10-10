package com.simplecityapps.mediaprovider.server.mediabrowser

import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.mediaprovider.server.mediaBrowserAuthorization
import com.simplecityapps.shuttle.model.MediaProviderType
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders

/**
 * The servers that speak the MediaBrowser API, Jellyfin and Emby (a fork of it), and where they differ in it: how a
 * session's token is sent, the case of the item query's parameters, and the names of a few fields and routes.
 */
enum class MediaBrowserServer(
    val type: MediaProviderType,
    /** What a song's path puts before the item id. */
    val songPathPrefix: String,
    /** What the stream, download and current-user routes sit under, after the address. */
    val apiPrefix: String,
    /** The query parameter a URL the player opens carries the session's token in. */
    val tokenParameter: String,
    /** The `ProviderIds` key of a file's MUSICBRAINZ_TRACKID, the recording. */
    val recordingIdKey: String,
    /** The `Fields` a song is read with. */
    val songFields: String
) {
    Jellyfin(
        type = MediaProviderType.Jellyfin,
        songPathPrefix = "jellyfin://item/",
        apiPrefix = "",
        tokenParameter = "ApiKey",
        // MusicBrainzTrack is the release track id
        recordingIdKey = "MusicBrainzRecording",
        songFields = "Genres,DateCreated,ProviderIds,MediaStreams"
    ),
    Emby(
        type = MediaProviderType.Emby,
        songPathPrefix = "emby://item/",
        apiPrefix = "/emby",
        tokenParameter = "api_key",
        recordingIdKey = "MusicBrainzTrack",
        songFields = "Genres,ProductionYear,DateCreated,ProviderIds,MediaStreams"
    );

    /**
     * Sends the session's [token]: Jellyfin in the `Authorization` client identity (it rejects the legacy `X-Emby-Token`
     * since 12), Emby as `X-Emby-Token`.
     */
    fun authorize(request: HttpRequestBuilder, token: String, clientIdentity: ClientIdentity) {
        when (this) {
            Jellyfin -> request.header(HttpHeaders.Authorization, mediaBrowserAuthorization(clientIdentity.id, token, clientIdentity.deviceName, clientIdentity.version))
            Emby -> request.header(EMBY_TOKEN, token)
        }
    }

    /**
     * Sends the session's [token] and, on Emby, the client identity beside it in `X-Emby-Authorization`: the headers
     * of the write and report routes, which [authorize] leaves at the token.
     */
    fun authorizeSession(request: HttpRequestBuilder, token: String, clientIdentity: ClientIdentity) {
        authorize(request, token, clientIdentity)
        if (this == Emby) {
            request.header(EMBY_AUTHORIZATION, mediaBrowserAuthorization(clientIdentity.id, deviceName = clientIdentity.deviceName, version = clientIdentity.version))
        }
    }

    /** The item query's parameter [name] (PascalCase) as this server documents it: camelCase on Jellyfin. */
    internal fun itemsParameter(name: String): String = when (this) {
        Jellyfin -> name.replaceFirstChar(Char::lowercase)
        Emby -> name
    }

    /** The path of the song read from [item]. */
    fun songPath(item: Item): String = "$songPathPrefix${item.id}"
}

/** The header Emby reads a session's access token from. */
internal const val EMBY_TOKEN = "X-Emby-Token"

/** The header Emby reads the client identity from once signed in. */
internal const val EMBY_AUTHORIZATION = "X-Emby-Authorization"
