package com.simplecityapps.shuttle.deviceinfo

expect class DeviceInfo {
    suspend fun getDeviceName(): String?
    suspend fun getDeviceId(): String?
}