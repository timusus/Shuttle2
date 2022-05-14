package com.simplecityapps.trial

import com.simplecityapps.networking.retrofit.NetworkResult
import retrofit2.http.GET
import retrofit2.http.Path

interface DeviceService {

    @GET("v1/devices/{deviceId}")
    suspend fun getDevice(@Path("deviceId") deviceId: String): NetworkResult<Device>
}
