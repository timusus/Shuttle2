package com.simplecityapps.networking.retrofit

sealed class NetworkResult<out S : Any> {
    data class Success<S : Any>(val body: S) : NetworkResult<S>()

    data class Failure(val error: Throwable) : NetworkResult<Nothing>()
}

/** This result with a successful body transformed by [transform]; a failure passes through. */
inline fun <S : Any, R : Any> NetworkResult<S>.map(transform: (S) -> R): NetworkResult<R> = when (this) {
    is NetworkResult.Success -> NetworkResult.Success(transform(body))
    is NetworkResult.Failure -> this
}
