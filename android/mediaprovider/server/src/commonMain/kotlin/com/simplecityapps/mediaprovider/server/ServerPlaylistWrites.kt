package com.simplecityapps.mediaprovider.server

import com.simplecityapps.mediaprovider.PlaylistWriteResult
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.networking.retrofit.error.RemoteServiceHttpError
import io.ktor.http.HttpStatusCode

/**
 * A server's answer to a request to one of its playlists (#916), as the queue of playlist edits reads it: a 404 means the
 * playlist is gone, any other client error but those a later try can get past (signed out, timed out, too many requests) means
 * the server won't take the edit, and anything else that failed (no connection, a server error) is tried again later.
 */
fun <S : Any, T> NetworkResult<S>.toPlaylistWriteResult(transform: (S) -> T): PlaylistWriteResult<T> = when (this) {
    is NetworkResult.Success -> PlaylistWriteResult.Success(transform(body))

    is NetworkResult.Failure -> when (val status = (error as? RemoteServiceHttpError)?.httpStatusCode) {
        HttpStatusCode.NotFound -> PlaylistWriteResult.PlaylistGone
        null, HttpStatusCode.Unauthorized, HttpStatusCode.RequestTimeout, HttpStatusCode.TooManyRequests -> PlaylistWriteResult.Failed
        else -> if (status.value in 400..499) PlaylistWriteResult.Refused else PlaylistWriteResult.Failed
    }
}

fun NetworkResult<*>.toPlaylistWriteResult(): PlaylistWriteResult<Unit> = toPlaylistWriteResult { }
