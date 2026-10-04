package com.simplecityapps.networking.retrofit.error

import io.ktor.http.HttpStatusCode

/**
 * A Remote Service Error with a HttpStatus code, and the response [body] when there was one, for diagnostics.
 */
open class RemoteServiceHttpError(
    val httpStatusCode: HttpStatusCode,
    val body: String? = null,
    /** The delay in seconds a `Retry-After` header asked for, when it gave one as a number of seconds. */
    val retryAfterSeconds: Long? = null
) : RemoteServiceError() {
    val isClientError: Boolean
        get() = httpStatusCode.value in 400..499

    val isServerError: Boolean
        get() = httpStatusCode.value in 500..599

    override fun toString(): String = "RemoteServiceHttpError" +
        "\n\t- code: ${httpStatusCode.value} (${httpStatusCode.description})" +
        "\n\t- message: ${super.message}"
}
