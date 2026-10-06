package com.simplecityapps.shuttle.settings

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@SingleIn(AppScope::class)
class DownloadSettings @Inject constructor(
    store: SettingsStore
) {
    val wifiOnly = store.preference(WifiOnly)
    val notificationPermissionAsked = store.preference(NotificationPermissionAsked)

    companion object {
        /** Downloads wait for an unmetered network. On by default, so nothing eats mobile data unasked. */
        val WifiOnly = Setting.boolean("pref_download_wifi_only", true)

        /** The notification permission was asked for once, on a download that went ahead; it isn't asked again. */
        val NotificationPermissionAsked = Setting.boolean("pref_download_notification_permission_asked", false)
    }
}
