package com.simplecityapps.trial

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.squareup.moshi.Moshi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit

class TrialManager(
    private val context: Context,
    private val moshi: Moshi,
    private val deviceService: DeviceService,
    private val preferenceManager: GeneralPreferenceManager,
    private val remoteConfig: FirebaseRemoteConfig,
    billingManager: BillingManager,
    coroutineScope: CoroutineScope
) {

    private val deviceAdapter = moshi.adapter(Device::class.java)

    var preTrialLength = TimeUnit.DAYS.toMillis(remoteConfig.getLong("pre_trial_length"))
    var trialLength = TimeUnit.DAYS.toMillis(remoteConfig.getLong("trial_length"))

    val trialState: StateFlow<TrialState> = billingManager.billingState.map { billingState ->
        when (billingState) {
            BillingState.Unknown -> TrialState.Unknown
            BillingState.Paid -> {
                TrialState.Paid
            }
            BillingState.Unpaid -> {
                getTrialState()
            }
        }
    }
        .onEach {
            Timber.i("trialState changed to $it")
            when (it) {
                TrialState.Paid -> {
                    if (preferenceManager.appPurchasedDate == null) {
                        preferenceManager.appPurchasedDate = Date()
                    }
                }
            }
        }
        .stateIn(coroutineScope, SharingStarted.Lazily, TrialState.Unknown)

    private suspend fun getTrialState(): TrialState {
        if (preferenceManager.firebaseAnalyticsEnabled) {
            withTimeout(5000) {
                remoteConfig.fetchAndActivate().await()
            }

            preTrialLength = TimeUnit.DAYS.toMillis(remoteConfig.getLong("pre_trial_length"))
            trialLength = TimeUnit.DAYS.toMillis(remoteConfig.getLong("trial_length"))
        }

        val now = Date()

        var localDevice = getLocalDevice()

        localDevice?.let { localDevice ->
            if (now.time - localDevice.lastUpdate.time >= trialLength) {
                val trialState = TrialState.Expired(now.time - localDevice.lastUpdate.time) // User's trial has expired
                Timber.i("TrialState: Expired (Local). Multiplier: ${trialState.multiplier()}")
                return trialState
            }
        } ?: run {
            Timber.d("Local device not found")
        }

        // If we get to this point, it looks like the user's trial hasn't expired, based on their local device last update date.
        // This could occur if the user has managed to delete the local device info, via app uninstall or clearing cache/date
        // So, we need to consult the backend
        val deviceId = localDevice?.deviceId ?: getDeviceId() // We use our the local device id if it exists, or retrieve a (possibly) new one
        val remoteDevice = getRemoteDevice(deviceId)
        remoteDevice?.let {
            if (now.time - remoteDevice.lastUpdate.time >= trialLength) {
                // User's trial has expired
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

        val timeSinceInstall = now.time - localDevice.lastUpdate.time
        return if (timeSinceInstall < preTrialLength) {
            val remaining = preTrialLength - timeSinceInstall
            TrialState.Pretrial(remaining)
        } else {
            val remaining = preTrialLength + trialLength - timeSinceInstall
            TrialState.Trial(remaining)
        }
    }

    private suspend fun getRemoteDevice(deviceId: String): Device? {
        return when (val result = deviceService.getDevice(deviceId)) {
            is NetworkResult.Success -> {
                Timber.i("Retrieved remote device")
                result.body
            }
            is NetworkResult.Failure -> {
                Timber.e("Failed to retrieve remote device: ${result.error}")
                null
            }
        }
    }

    private suspend fun getLocalDevice(): Device? {
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
