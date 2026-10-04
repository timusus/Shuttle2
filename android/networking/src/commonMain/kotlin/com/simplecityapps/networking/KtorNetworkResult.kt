package com.simplecityapps.networking

import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.networking.retrofit.error.NetworkError
import com.simplecityapps.networking.retrofit.error.RemoteServiceHttpError
import com.simplecityapps.networking.retrofit.error.UnexpectedError
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.io.IOException

/**
 * The response [request] gets, as a [NetworkResult]: its body decoded as [T] on a 2xx, a [RemoteServiceHttpError]
 * for any other status, a [NetworkError] when the server couldn't be reached and an [UnexpectedError] for anything
 * else (a body that doesn't decode, say). Cancellation isn't caught.
 */
suspend inline fun <reified T : Any> HttpClient.networkResult(request: HttpClient.() -> HttpResponse): NetworkResult<T> = try {
    val response = request()
    if (response.status.isSuccess()) {
        NetworkResult.Success(response.body<T>())
    } else {
        NetworkResult.Failure(response.toHttpError())
    }
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    NetworkResult.Failure(failureOf(e))
}

@PublishedApi
internal suspend fun HttpResponse.toHttpError(): RemoteServiceHttpError {
    val body = try {
        bodyAsText().ifEmpty { null }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
    return RemoteServiceHttpError(status, body, retryAfterSeconds = headers[HttpHeaders.RetryAfter]?.trim()?.toLongOrNull()?.coerceAtLeast(0))
}

/** What a request that threw [throwable] failed with. */
@PublishedApi
internal suspend fun HttpClient.failureOf(throwable: Throwable): Error = when (throwable) {
    // A client with expectSuccess throws for a non-2xx status rather than returning the response
    is ResponseException -> throwable.response.toHttpError()

    is IOException -> NetworkError(isConnected, throwable)

    else -> UnexpectedError(throwable)
}
