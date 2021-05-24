package com.simplecityapps.trial

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import com.simplecityapps.networking.retrofit.NetworkResult
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit


class TrialManager(
    private val context: Context,
    private val moshi: Moshi,
    private val deviceService: DeviceService
) {

    val trialLength = TimeUnit.DAYS.toMillis(14)

    private val deviceAdapter = moshi.adapter(Device::class.java)

    val trialState: MutableStateFlow<TrialState> = MutableStateFlow(TrialState.Unknown)

    suspend fun updateTrialState() {
        trialState.value = retrieveTrialState()
    }

    private suspend fun retrieveTrialState(): TrialState {
        if (hasPaidVersion()) {
            Timber.i("TrialState: Paid")
            return TrialState.Paid // User has paid version (1)
        }

        val now = Date()

        var localDevice = getLocalDevice()
        localDevice?.let { localDevice ->
            if (now.time - localDevice.lastUpdate.time >= trialLength) {
                val trialState = TrialState.Expired(now.time - localDevice.lastUpdate.time) // User's trial has expired (2)
                Timber.i("TrialState: Expired (Local). Multiplier: ${trialState.multiplier()}")
                return trialState
            }
        } ?: run {
            Timber.e("Local device not found")
        }

        // If we get to this point, it looks like the user's trial hasn't expired, based on their local device last update date.
        // This could occur if the user has managed to delete the local device info, via app uninstall ora clearing cache/date
        // So, we need to consult the backend
        val deviceId = localDevice?.deviceId ?: getDeviceId() // We use our the local device id if it exists, or retrieve a (possibly) new one
        val remoteDevice = getRemoteDevice(deviceId)
        remoteDevice?.let {
            if (now.time - remoteDevice.lastUpdate.time >= trialLength) {
                // User's trial has expired (3)
                writeLocalDevice(remoteDevice)
                val trialState = TrialState.Expired(now.time - remoteDevice.lastUpdate.time)
                Timber.i("TrialState: Expired (Remote). Multiplier: ${trialState.multiplier()}")
                return trialState
            }
        }

        // If we don't have a local device, or the remote device's update date is older than the remote device's update date, update our local device
        if (localDevice == null || (remoteDevice?.lastUpdate ?: now) < localDevice.lastUpdate) {
            Timber.i("Updating local device")
            localDevice = remoteDevice ?: Device(deviceId, now)
            writeLocalDevice(localDevice)
        }

        Timber.i("TrialState: Trial (${TimeUnit.MILLISECONDS.toHours(trialLength - (now.time - localDevice.lastUpdate.time))} hours)")
        return TrialState.Trial(trialLength - (now.time - localDevice.lastUpdate.time))
    }

    fun hasPaidVersion(): Boolean {
        return true
    }

    private suspend fun getRemoteDevice(deviceId: String): Device? {
        return when (val result = deviceService.getDevice(deviceId)) {
            is NetworkResult.Success -> {
                result.body
            }
            is NetworkResult.Failure -> {
                Timber.e("Failed to retrieve remote device: ${result.error}")
                null
            }
        }
    }

    private suspend fun getLocalDevice(): Device? {
        return null
        return withContext(Dispatchers.IO) {
            val adapter = moshi.adapter(Device::class.java)
            val file = File(context.filesDir, "device")

            if (file.exists()) {
                file.bufferedReader().use {
                    try {
                        return@withContext adapter.fromJson(it.readText())!!
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to read device json")
                    }
                }
            }
            null
        }
    }

    private suspend fun writeLocalDevice(device: Device) {
        withContext(Dispatchers.IO) {
            File(context.filesDir, "device").writeText(deviceAdapter.toJson(device))
        }
    }

    /**
     * Retrieves a GUID representing this device.
     * On Api 26+, this is the Android ID (SSAID), which is unique per app and device, and persists across installations.
     * Otherwise, a GUID is generated and stored in internal storage - which will persist across installations via auto-backup, if enabled by the user
     */
    @SuppressLint("HardwareIds")
    private fun getDeviceId(): String {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        } else {
            UUID.randomUUID().toString()
        }
    }
}