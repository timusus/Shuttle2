package com.simplecityapps.mediaprovider.server

import com.simplecityapps.mediaprovider.PlaylistWriteResult
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.networking.retrofit.error.RemoteServiceHttpError
import io.ktor.http.HttpStatusCode

/** Maps a playlist request's failure: a 404 means the playlist is gone, other client errors are final, anything else is retried later. */
fun <S : Any, T> NetworkResult<S>.toPlaylistWriteResult(transform: (S) -> T): PlaylistWriteResult<T> = when (this) {
    is NetworkResult.Success -> PlaylistWriteResult.Success(transform(body))

    is NetworkResult.Failure -> when (val status = (error as? RemoteServiceHttpError)?.httpStatusCode) {
        HttpStatusCode.NotFound -> PlaylistWriteResult.PlaylistGone
        null, HttpStatusCode.Unauthorized, HttpStatusCode.RequestTimeout, HttpStatusCode.TooManyRequests -> PlaylistWriteResult.Failed
        else -> if (status.value in 400..499) PlaylistWriteResult.Refused else PlaylistWriteResult.Failed
    }
}

fun NetworkResult<*>.toPlaylistWriteResult(): PlaylistWriteResult<Unit> = toPlaylistWriteResult { }
