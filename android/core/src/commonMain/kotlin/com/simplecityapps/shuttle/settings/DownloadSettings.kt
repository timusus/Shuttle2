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
        // A copy restored from a backup describes the old device, so it is dropped, not honoured. Skipped where both
        // stores are one (iOS), as the delete would wipe the live flag.
        if (!store.isSameStorageAs(deviceLocalStore)) store.preference(NotificationPermissionAsked).reset()
    }

    companion object {
        /** Downloads wait for an unmetered network. On by default, so nothing eats mobile data unasked. */
        val WifiOnly = Setting.boolean("pref_download_wifi_only", true)

        /** The notification permission was asked for once, on a download that went ahead; it isn't asked again. */
        val NotificationPermissionAsked = Setting.boolean("pref_download_notification_permission_asked", false)
    }
}
