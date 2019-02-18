package com.simplecityapps.shuttle.networking

import okhttp3.OkHttpClient

class ApiClient {

    val okHttpClient: OkHttpClient = OkHttpClient.Builder().build()

}