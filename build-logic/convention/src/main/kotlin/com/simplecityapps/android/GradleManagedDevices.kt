package com.simplecityapps.android

import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.ManagedVirtualDevice
import org.gradle.kotlin.dsl.invoke
import java.util.Locale

/**
 * Configure project for Gradle managed devices
 */
internal fun configureGradleManagedDevices(
    commonExtension: CommonExtension<*, *, *, *, *, *>,
) {
    val deviceConfigs = listOf(
        DeviceConfig(device = "Pixel 4", apiLevel = 30, systemImageSource = "aosp-atd"),
        DeviceConfig(device = "Pixel 6", apiLevel = 31, systemImageSource = "aosp"),
        DeviceConfig(device = "Pixel C", apiLevel = 30, systemImageSource = "aosp-atd"),
    )

    @Suppress("UnstableApiUsage")
    commonExtension.testOptions {
        managedDevices {
            devices {
                deviceConfigs.forEach { deviceConfig ->
                    maybeCreate(deviceConfig.taskName, ManagedVirtualDevice::class.java).apply {
                        device = deviceConfig.device
                        apiLevel = deviceConfig.apiLevel
                        systemImageSource = deviceConfig.systemImageSource
                    }
                }
            }
        }
    }
}

private data class DeviceConfig(
    val device: String,
    val apiLevel: Int,
    val systemImageSource: String,
) {
    val taskName = buildString {
        append(device.lowercase(Locale.ROOT).replace(" ", ""))
        append("api")
        append(apiLevel.toString())
        append(systemImageSource.replace("-", ""))
    }
}
