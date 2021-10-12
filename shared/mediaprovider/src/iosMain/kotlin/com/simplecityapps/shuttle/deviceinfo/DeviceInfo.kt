package com.simplecityapps.shuttle.deviceinfo

actual class DeviceInfo {
    actual suspend fun getDeviceName(): String {
        return "iPhone"
    }
}