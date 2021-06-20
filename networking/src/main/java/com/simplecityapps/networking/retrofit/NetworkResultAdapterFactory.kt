package com.simplecityapps.networking.retrofit

import android.net.ConnectivityManager
import retrofit2.Call
import retrofit2.CallAdapter
import retrofit2.Retrofit
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

class NetworkResultAdapterFactory(
    private val connectivityManager: ConnectivityManager?
) : CallAdapter.Factory() {

    override fun get(returnType: Type, annotations: Array<Annotation>, retrofit: Retrofit): CallAdapter<*, *>? {
        // Suspend functions wrap the response type in `Call`
        if (Call::class.java != getRawType(returnType)) {
            return null
        }

        // Check first that the return type is `ParameterizedType`
        check(returnType is ParameterizedType) {
            "Return type must be parameterized as Call<NetworkResponse<<Foo>> or Call<NetworkResponse<out Foo>>"
        }

        // Get the response type inside the `Call` type
        val responseType = getParameterUpperBound(0, returnType)
        // If the response type is not NetworkResponse then we can't handle this type, so we return null
        if (getRawType(responseType) != NetworkResult::class.java) {
            return null
        }

        // The response type is NetworkResponse and should be parameterized
        check(responseType is ParameterizedType) { "Response must be parameterized as NetworkResponse<Foo> or NetworkResponse<out Foo>" }

        val successBodyType = getParameterUpperBound(0, responseType)

        val errorBodyConverter = try {
            retrofit.nextResponseBodyConverter<Throwable>(null, Throwable::class.java, annotations)
        } catch (e: IllegalArgumentException) {
            null
        }

        return NetworkResultCallAdapter<Any>(successBodyType, errorBodyConverter, connectivityManager)
    }
}