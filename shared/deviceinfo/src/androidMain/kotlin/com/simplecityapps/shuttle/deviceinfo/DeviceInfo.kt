package com.simplecityapps.shuttle.deviceinfo

import java.util.*

actual class DeviceInfo {

    actual suspend fun getDeviceName(): String? {
        return "Android"
    }

    actual suspend fun getDeviceId(): String? {
        return UUID.randomUUID().toString()
    }
}