package com.simplecityapps.networking.retrofit

import android.annotation.SuppressLint
import android.net.ConnectivityManager
import com.simplecityapps.networking.retrofit.error.NetworkError
import com.simplecityapps.networking.retrofit.error.RemoteServiceHttpError
import com.simplecityapps.networking.retrofit.error.UnexpectedError
import java.io.IOException
import okhttp3.Request
import okhttp3.ResponseBody
import okio.Timeout
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Converter
import retrofit2.Response
import timber.log.Timber

/**
 * Maps a [Call] to [NetworkResultCall].
 * */
@SuppressLint("BinaryOperationInTimber")
class NetworkResultCall<S : Any>(
    private val call: Call<S>,
    private val errorBodyConverter: Converter<ResponseBody, Throwable>?,
    private val connectivityManager: ConnectivityManager?
) : Call<NetworkResult<S>> {
    val logErrors = false

    override fun enqueue(callback: Callback<NetworkResult<S>>) {
        call.enqueue(
            object : Callback<S> {
                override fun onResponse(
                    call: Call<S>,
                    response: Response<S>
                ) {
                    if (call.isCanceled) return

                    if (response.isSuccessful) {
                        if (response.body() == null) {
                            callback.onResponse(this@NetworkResultCall, Response.success(NetworkResult.Failure(Throwable("Response body is empty"))))
                        } else {
                            callback.onResponse(this@NetworkResultCall, Response.success(NetworkResult.Success(response.body()!!)))
                        }
                    } else {
                        val error =
                            response.errorBody()?.let { body ->
                                try {
                                    errorBodyConverter?.convert(body)
                                } catch (e: IOException) {
                                    null
                                }
                            } ?: RemoteServiceHttpError(response)

                        if (logErrors) {
                            Timber.e(
                                "Request failed." +
                                    "\nmethod: ${call.request().method}" +
                                    "\nurl: ${call.request().url}" +
                                    "\nerror: $error"
                            )
                        }
                        callback.onResponse(this@NetworkResultCall, Response.success(NetworkResult.Failure(error)))
                    }
                }

                override fun onFailure(
                    call: Call<S>,
                    t: Throwable
                ) {
                    val error: Error =
                        if (t is IOException) {
                            NetworkError(connectivityManager?.activeNetworkInfo?.isConnected ?: false, t)
                        } else {
                            UnexpectedError(t)
                        }

                    if (logErrors) {
                        Timber.e(
                            "Request failed." +
                                "\nmethod: ${call.request().method}" +
                                "\nurl: ${call.request().url}" +
                                "\nerror: $error"
                        )
                    }
                    callback.onResponse(this@NetworkResultCall, Response.success(NetworkResult.Failure(error)))
                }
            }
        )
    }

    override fun execute(): Response<NetworkResult<S>> = throw(IllegalStateException("execute() not supported"))

    override fun isExecuted(): Boolean = call.isExecuted

    override fun clone(): Call<NetworkResult<S>> = NetworkResultCall(call.clone(), errorBodyConverter, connectivityManager = connectivityManager)

    override fun cancel() {
        call.cancel()
    }

    override fun isCanceled(): Boolean = call.isCanceled

    override fun request(): Request = call.request()

    override fun timeout(): Timeout = call.timeout()
}
