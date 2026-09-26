package com.simplecityapps.shuttle.ui

import com.github.takahirom.roborazzi.RoborazziOptions

// Rationale and the changeThreshold value live in the root build.gradle.kts (#458), which sets
// s2.roborazzi.changeThreshold only on Linux.
private val linuxChangeThreshold: Float? = System.getProperty("s2.roborazzi.changeThreshold")?.toFloat()

val DocsDesignRoborazziOptions = RoborazziOptions(
    captureType = RoborazziOptions.CaptureType.Screenshot(),
    compareOptions = linuxChangeThreshold
        ?.let { RoborazziOptions.CompareOptions(changeThreshold = it) }
        ?: RoborazziOptions.CompareOptions(),
)
