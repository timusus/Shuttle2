package com.simplecityapps.networking

import com.simplecityapps.networking.retrofit.error.NetworkError
import com.simplecityapps.networking.retrofit.error.RemoteServiceError
import com.simplecityapps.networking.retrofit.error.RemoteServiceHttpError
import com.simplecityapps.networking.retrofit.error.UnexpectedError
import com.simplecityapps.networking.retrofit.error.UserFriendlyError

fun Throwable.userDescription(): String {
    return (this as? Error)?.userDescription() ?: "An unknown error occurred."
}

fun Error.userDescription(): String {
    return when (this) {
        is RemoteServiceHttpError -> {
            when {
                isServerError -> "A server error occurred. (${httpStatusCode.code})"
                else -> "An error occurred. (${httpStatusCode.code})"
            }
        }
        is RemoteServiceError -> {
            "An unknown service error occurred."
        }
        is NetworkError -> {
            if (hasInternetConnectivity) {
                "The server could not be reached."
            } else {
                "You are not connected to the internet."
            }
        }
        is UnexpectedError -> {
            "An unexpected error occurred."
        }
        is UserFriendlyError -> {
            message
        }
        else -> {
            "An unknown error occurred."
        }
    }
}

fun Error.isHttpError(): Boolean {
    return this is RemoteServiceHttpError
}

fun Error.isHttpServerError(): Boolean {
    return (this as? RemoteServiceHttpError)?.isServerError ?: false
}

fun Error.isHttpClientError(): Boolean {
    return (this as? RemoteServiceHttpError)?.isClientError ?: false
}

fun Error.isNetworkError(): Boolean {
    return this is NetworkError
}
