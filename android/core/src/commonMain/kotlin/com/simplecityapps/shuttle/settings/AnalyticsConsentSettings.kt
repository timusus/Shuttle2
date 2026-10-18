package com.simplecityapps.shuttle.settings

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/** Analytics consent history, kept so InstallDefaults can migrate old answers into an explicit opt-out. */
@SingleIn(AppScope::class)
class AnalyticsConsentSettings @Inject constructor(
    store: SettingsStore
) {
    /** Answered, by Share, No thanks or dismissing the deleted card. */
    val asked = store.preference(Asked)

    /** Whether the one-time "analytics is now on" notice has been shown (or never applies) for this install. */
    val noticeShown = store.preference(NoticeShown)

    companion object {
        val Asked = Setting.boolean("pref_analytics_consent_asked", false)
        val NoticeShown = Setting.boolean("pref_analytics_notice_shown", false)
    }
}
