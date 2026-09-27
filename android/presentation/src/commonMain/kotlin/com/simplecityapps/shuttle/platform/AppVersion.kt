package com.simplecityapps.shuttle.platform

/** The version the user sees (`2026.09.27`): Android's `BuildConfig.VERSION_NAME`, iOS's `CFBundleShortVersionString`. */
fun interface AppVersion {
    fun name(): String
}
