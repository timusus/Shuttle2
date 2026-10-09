package com.simplecityapps.shuttle.settings

import com.simplecityapps.shuttle.persistence.DeviceLocalStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@SingleIn(AppScope::class)
class DownloadSettings @Inject constructor(
    store: SettingsStore,
    deviceLocalStore: DeviceLocalStore
) {
    val wifiOnly = store.preference(WifiOnly)
    val notificationPermissionAsked = deviceLocalStore.preference(NotificationPermissionAsked)

    init {
        // The flag used to live in the default store, which is backed up; carry an existing install's answer over.
        val legacy = store.preference(NotificationPermissionAsked)
        if (legacy.isSet()) {
            val asked = legacy.value
            legacy.reset()
            if (!notificationPermissionAsked.isSet()) notificationPermissionAsked.value = asked
        }
    }

    companion object {
        /** Downloads wait for an unmetered network. On by default, so nothing eats mobile data unasked. */
        val WifiOnly = Setting.boolean("pref_download_wifi_only", true)

        /** The notification permission was asked for once, on a download that went ahead; it isn't asked again. */
        val NotificationPermissionAsked = Setting.boolean("pref_download_notification_permission_asked", false)
    }
}
