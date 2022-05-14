package com.simplecityapps.networking.retrofit

sealed class NetworkResult<out S : Any> {
    data class Success<S : Any>(val body: S) : NetworkResult<S>()
    data class Failure(val error: Throwable) : NetworkResult<Nothing>()
}
