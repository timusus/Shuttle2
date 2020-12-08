package com.simplecityapps.networking.retrofit

import android.net.ConnectivityManager
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.CallAdapter
import retrofit2.Converter
import java.lang.reflect.Type

class NetworkResultCallAdapter<S : Any>(
    private val responseType: Type,
    private val errorBodyConverter: Converter<ResponseBody, Throwable>?,
    private val connectivityManager: ConnectivityManager?
) : CallAdapter<S, Call<NetworkResult<S>>> {

    override fun responseType(): Type {
        return responseType
    }

    override fun adapt(call: Call<S>): Call<NetworkResult<S>> {
        return NetworkResultCall(call, errorBodyConverter, connectivityManager)
    }
}