package com.simplecityapps.provider.subsonic.http

import com.simplecityapps.networking.networkResult
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.provider.subsonic.SubsonicAuth
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType

/**
 * The Subsonic REST API (`/rest/<method>.view`), JSON flavoured. Every request carries the protocol version, the client
 * name and [SubsonicAuth]'s credentials as query parameters; a `status: "failed"` response is a [SubsonicError].
 */
class SubsonicService(
    private val client: HttpClient,
    private val clientName: String
) {
    suspend fun ping(address: String, auth: SubsonicAuth): NetworkResult<SubsonicResponse> = get(address, "ping", auth)

    suspend fun openSubsonicExtensions(address: String, auth: SubsonicAuth): NetworkResult<List<ExtensionDto>> = get(address, "getOpenSubsonicExtensions", auth).map { it.openSubsonicExtensions.orEmpty() }

    /** A page of every song: an empty query matches everything on Navidrome and other OpenSubsonic servers. */
    suspend fun songs(address: String, auth: SubsonicAuth, offset: Int, count: Int): NetworkResult<List<SongDto>> = get(
        address,
        "search3",
        auth,
        "query" to "",
        "songCount" to count,
        "songOffset" to offset,
        "artistCount" to 0,
        "albumCount" to 0
    ).map { it.searchResult3?.song.orEmpty() }

    suspend fun albums(address: String, auth: SubsonicAuth, offset: Int, size: Int): NetworkResult<List<AlbumDto>> = get(
        address,
        "getAlbumList2",
        auth,
        "type" to "alphabeticalByName",
        "size" to size,
        "offset" to offset
    ).map { it.albumList2?.album.orEmpty() }

    suspend fun album(address: String, auth: SubsonicAuth, id: String): NetworkResult<AlbumDto> = get(address, "getAlbum", auth, "id" to id).required { it.album }

    suspend fun artist(address: String, auth: SubsonicAuth, id: String): NetworkResult<ArtistDto> = get(address, "getArtist", auth, "id" to id).required { it.artist }

    suspend fun playlists(address: String, auth: SubsonicAuth): NetworkResult<List<PlaylistDto>> = get(address, "getPlaylists", auth).map { it.playlists?.playlist.orEmpty() }

    suspend fun playlist(address: String, auth: SubsonicAuth, id: String): NetworkResult<PlaylistDto> = get(address, "getPlaylist", auth, "id" to id).required { it.playlist }

    suspend fun star(address: String, auth: SubsonicAuth, id: String): NetworkResult<SubsonicResponse> = get(address, "star", auth, "id" to id)

    suspend fun unstar(address: String, auth: SubsonicAuth, id: String): NetworkResult<SubsonicResponse> = get(address, "unstar", auth, "id" to id)

    /** A now-playing report ([submission] false) or a play ([submission] true, played at [timeMs], epoch millis). */
    suspend fun scrobble(address: String, auth: SubsonicAuth, id: String, submission: Boolean, timeMs: Long? = null): NetworkResult<SubsonicResponse> = get(
        address,
        "scrobble",
        auth,
        "id" to id,
        "submission" to submission,
        "time" to timeMs
    )

    /** OpenSubsonic's `transcoding` extension: how the server would stream song [id] to a client that plays [clientInfo]. */
    suspend fun transcodeDecision(address: String, auth: SubsonicAuth, id: String, clientInfo: ClientInfoDto): NetworkResult<TranscodeDecisionDto> = call(
        address,
        "getTranscodeDecision",
        auth,
        listOf("mediaId" to id, "mediaType" to "song"),
        body = clientInfo
    ).required { it.transcodeDecision }

    /** The URL of [method] with [parameters] and freshly salted credentials, for the player or image loader to fetch. */
    fun url(address: String, method: String, auth: SubsonicAuth, vararg parameters: Pair<String, Any?>): String = buildUrl(address, method, auth, parameters.toList())

    /**
     * The URL of [method] with [parameters] and no credentials, for a request that has them added on the way out
     * (artwork, whose URL is its cache key, so it can't carry a fresh salt).
     */
    fun unsignedUrl(address: String, method: String, vararg parameters: Pair<String, Any?>): String = buildUrl(address, method, auth = null, parameters.toList())

    /** [url] (an [unsignedUrl]) with the protocol version, client name and [auth]'s credentials added. */
    fun sign(url: String, auth: SubsonicAuth): String = URLBuilder(url).apply { appendCommonParameters(auth) }.buildString()

    private suspend fun get(address: String, method: String, auth: SubsonicAuth, vararg parameters: Pair<String, Any?>): NetworkResult<SubsonicResponse> = call(address, method, auth, parameters.toList())

    private suspend fun call(
        address: String,
        method: String,
        auth: SubsonicAuth,
        parameters: List<Pair<String, Any?>>,
        body: ClientInfoDto? = null
    ): NetworkResult<SubsonicResponse> {
        val url = buildUrl(address, method, auth, parameters + ("f" to "json"))
        val result = client.networkResult<SubsonicEnvelope> {
            if (body == null) {
                get(url)
            } else {
                post(url) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            }
        }
        return when (result) {
            is NetworkResult.Success -> {
                val response = result.body.response
                if (response.isOk) NetworkResult.Success(response) else NetworkResult.Failure(SubsonicError.of(response.error))
            }

            is NetworkResult.Failure -> result
        }
    }

    private fun buildUrl(
        address: String,
        method: String,
        auth: SubsonicAuth?,
        parameters: List<Pair<String, Any?>>
    ): String = URLBuilder(address.trimEnd('/')).apply {
        appendPathSegments("rest", "$method.view")
        if (auth != null) appendCommonParameters(auth)
        parameters.forEach { (name, value) -> if (value != null) this.parameters.append(name, value.toString()) }
    }.buildString()

    private fun URLBuilder.appendCommonParameters(auth: SubsonicAuth) {
        (auth.parameters() + listOf("v" to API_VERSION, "c" to clientName)).forEach { (name, value) -> parameters.append(name, value) }
    }

    companion object {
        /** The Subsonic API version this client speaks: 1.16.1, the last, which OpenSubsonic builds on. */
        const val API_VERSION = "1.16.1"
    }
}

private inline fun <T : Any> NetworkResult<SubsonicResponse>.required(payload: (SubsonicResponse) -> T?): NetworkResult<T> = when (this) {
    is NetworkResult.Success -> payload(body)?.let { NetworkResult.Success(it) } ?: NetworkResult.Failure(SubsonicError.Other(0, "The response had no payload"))
    is NetworkResult.Failure -> this
}

private inline fun <T : Any> NetworkResult<SubsonicResponse>.map(transform: (SubsonicResponse) -> T): NetworkResult<T> = when (this) {
    is NetworkResult.Success -> NetworkResult.Success(transform(body))
    is NetworkResult.Failure -> this
}
